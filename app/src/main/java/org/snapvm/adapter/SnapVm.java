package org.snapvm.adapter;

import android.util.Log;

import java.io.File;
import java.util.TimerTask;
import java.util.zip.ZipFile;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.SnapStateCodecs;
import javax.microedition.lcdui.SnapVmBridge;
import javax.microedition.midlet.MIDlet;
import javax.microedition.shell.AppClassLoader;
import javax.microedition.shell.MidletThread;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;
import org.snapvm.VmSampler;

import ru.playsoftware.j2meloader.config.Config;

/**
 * SnapVM execution path: loads the midlet jar into the interpreter instead of
 * the dex pipeline, wires host shadows for guest-extends-host classes, and runs
 * a scheduler thread that services guest green threads.
 *
 * Since v1.0 SnapVM is the only engine — the dex path is gone and enabled()
 * always returns true; the marker file no longer does anything.
 */
public final class SnapVm {
	private static final String TAG = "SnapVm";
	private static final long PUMP_BUDGET = 200_000;

	private static final String TRACE_FILE = "snapvm.trace";
	private static final String TRACE_CALLS_FILE = "snapvm.tracecalls";
	private static final String TRACE_ARGS_FILE = "snapvm.traceargs";
	private static final String STATS_FILE = "snapvm.stats";
	private static final String PROF_FILE = "snapvm.prof";
	private static final String ERR_FILE = "snapvm.err";
	private static final String SAMPLE_FILE = "snapvm.sample";
	private static final String STACKDUMP_FILE = "snapvm.stackdump";
	// write a guest class name into this file to dump its statics to logcat
	private static final String STATICS_FILE = "snapvm.statics";

	private static volatile SnapVm instance;

	private final Vm vm = new Vm();
	private volatile boolean running;
	private Thread pumpThread;
	private File appDir;
	private VmSampler sampler;
	// post-restore rebind targets — filled by shadow codecs during vm.restore
	private volatile MIDlet restoredMidlet;
	private volatile Displayable restoredCurrent;

	private SnapVm() {
	}

	public static boolean enabled() {
		return true;
	}

	private static String readRegex(File f) {
		try {
			byte[] b = new byte[(int) f.length()];
			java.io.FileInputStream in = new java.io.FileInputStream(f);
			int n = in.read(b);
			in.close();
			return new String(b, 0, Math.max(0, n)).trim();
		} catch (Exception e) {
			return ".*";
		}
	}

	public static MIDlet load(File appDir, String mainClass) throws Exception {
		if (instance != null) {
			// A previous midlet can outlive its activity in the :midlet process
			// (HOME instead of a clean exit skips notifyDestroyed/killProcess).
			// Its VM is dead weight — replace it like the dex path would.
			Log.i(TAG, "stale SnapVm instance found; stopping it");
			instance.stop();
			instance = null;
		}
		SnapVm s = new SnapVm();
		String dir = Config.getEmulatorDir();
		if (dir != null) {
			if (new File(dir, TRACE_FILE).exists())
				org.snapvm.Interpreter.TRACE_THROWS = true;
			File calls = new File(dir, TRACE_CALLS_FILE);
			if (calls.exists())
				org.snapvm.Interpreter.TRACE_CALLS = readRegex(calls);
			File targs = new File(dir, TRACE_ARGS_FILE);
			if (targs.exists())
				org.snapvm.Interpreter.TRACE_ARGS = readRegex(targs);
			s.vm.setCallStatsEnabled(new File(dir, STATS_FILE).exists());
			File err = new File(dir, ERR_FILE);
			if (err.exists()) {
				try {
					// gate file is shell-created (unwritable for us); the log
					// goes to a file the process owns so writes always land
					System.setErr(new java.io.PrintStream(
							new java.io.FileOutputStream(dir + "/snapvm_err.log"), true));
				} catch (Exception e) {
					Log.w(TAG, "err redirect failed", e);
				}
			}
		}
		// jar resource pipeline shared with the dex path (Image.createImage, RMS dir)
		AppClassLoader.initRes(appDir);
		File jar = new File(appDir, Config.MIDLET_RES_FILE);
		s.vm.addJar(new ZipFile(jar));
		copyProps(s.vm);
		s.vm.registerShadow(MIDlet.class, SnapMidletShadow::new);
		s.vm.registerShadow(Canvas.class, SnapCanvasShadow::new);
		s.vm.registerShadow(com.nokia.mid.ui.FullCanvas.class, SnapFullCanvasShadow::new);
		s.vm.registerShadow(javax.microedition.lcdui.game.GameCanvas.class,
				SnapGameCanvasShadow::new);
		s.vm.registerShadow(TimerTask.class, SnapTimerTaskShadow::new);
		// Other host classes games subclass — missing a factory kills the guest
		// thread on marshal (requireShadow). Library scan: Sprite x44, Form x31,
		// TextBox x4, Alert x2, List x1, TiledLayer x0 (sibling of Sprite).
		s.vm.registerShadow(javax.microedition.lcdui.TextBox.class,
				SnapTextBoxShadow::new);
		s.vm.registerShadow(javax.microedition.lcdui.Form.class,
				SnapFormShadow::new);
		s.vm.registerShadow(javax.microedition.lcdui.List.class,
				SnapListShadow::new);
		s.vm.registerShadow(javax.microedition.lcdui.Alert.class,
				SnapAlertShadow::new);
		s.vm.registerShadow(javax.microedition.lcdui.game.Sprite.class,
				SnapSpriteShadow::create);
		s.vm.registerShadow(javax.microedition.lcdui.game.TiledLayer.class,
				SnapTiledLayerShadow::new);
		// java.util.Timer's internals are unreachable on ART (empty
		// getDeclaredFields) — substitute our own subclass so its task
		// schedule params are recorded at schedule() time.
		s.vm.addHostAlias("java/util/Timer", SnapTimer.class);
		s.vm.registerParkedCall("javax/microedition/lcdui/Canvas.serviceRepaints()V",
				(v, t, args, done) -> SnapVmBridge.serviceRepaints(v, t, args[0], done));
		LcduiFastCalls.register(s.vm);
		SnapHandles.registerAll(s.vm);
		SnapM3gHandles.registerAll(s.vm);
		SnapStateCodecs.registerAll(s.vm);
		s.appDir = appDir;
		VmObject guest = s.vm.newGuestObject(mainClass);
		s.start();
		instance = s;
		Object shadow = guest.hostShadow;
		if (!(shadow instanceof MIDlet)) {
			throw new IllegalStateException("midlet shadow not created: " + shadow);
		}
		return (MIDlet) shadow;
	}

	// ---- paint probe (perf diagnostics) ----
	public static volatile int paints;
	public static volatile long paintMsTotal, paintMsMax, lastPaintAt, maxGap;
	public static volatile int srCalls;
	public static volatile long srMsTotal, srMsMax;

	public static void paintProbe(long paintMs) {
		long now = System.currentTimeMillis();
		long gap = lastPaintAt == 0 ? 0 : now - lastPaintAt;
		lastPaintAt = now;
		paints++;
		paintMsTotal += paintMs;
		if (paintMs > paintMsMax) paintMsMax = paintMs;
		if (gap > maxGap) maxGap = gap;
	}

	public static void srProbe(long waitMs) {
		srCalls++;
		srMsTotal += waitMs;
		if (waitMs > srMsMax) srMsMax = waitMs;
	}

	/**
	 * Diagnostic for the snapvm.statics marker: logs scalars + arrays of the
	 * named guest class, and for the 时空旅者 actor engine (class "e") walks the
	 * O→N[] active-actor list and the aL[] draw list to expose cycles.
	 */
	private void dumpStatics(String className) {
		try {
			java.util.Map<String, Object> m = vm.staticMap(className);
			if (m.isEmpty()) {
				Log.i(TAG, "statics: no class " + className);
				return;
			}
			StringBuilder sb = new StringBuilder("=== statics ").append(className).append(" ===\n");
			for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
				Object v = e.getValue();
				if (v instanceof short[])
					sb.append(e.getKey()).append("=short[").append(((short[]) v).length).append("]\n");
				else if (v instanceof int[])
					sb.append(e.getKey()).append("=int[").append(((int[]) v).length).append("]\n");
				else if (v instanceof Object[])
					sb.append(e.getKey()).append("=obj[").append(((Object[]) v).length).append("]\n");
				else
					sb.append(e.getKey()).append('=').append(v).append('\n');
			}
			Log.i(TAG, sb.toString());
			if (!className.equals("e"))
				return;
			// actor-engine forensics: walk O via N[], report the traversal and
			// where a cycle closes; then the aL[] draw-list head chain.
			short[] N = (short[]) m.get("N|[S"), aL = (short[]) m.get("aL|[S"),
					aJ = (short[]) m.get("aJ|[S"), aI = (short[]) m.get("aI|[S");
			Integer O = (Integer) m.get("O|I"), aK = (Integer) m.get("aK|I"),
					aM = (Integer) m.get("aM|I"), aO = (Integer) m.get("aO|I");
			Log.i(TAG, "actors: O=" + O + " aK=" + aK + " aM=" + aM + " aO=" + aO
					+ " | N=" + walk(N, O) + " | aL=" + walk(aL, aM));
			if (N != null) Log.i(TAG, "N[]=" + java.util.Arrays.toString(N));
			if (aL != null) Log.i(TAG, "aL[]=" + java.util.Arrays.toString(aL));
			// per-actor K (sort key) + a-field state for every node N links to
			Object[] M = (Object[]) m.get("M|[La;");
			if (M != null) {
				StringBuilder ks = new StringBuilder("K[]:");
				for (int i = 0; i < M.length; i++) {
					Object ai = M[i];
					if (ai == null) continue;
					try {
						java.lang.reflect.Field f = ai.getClass().getDeclaredField("fields");
						f.setAccessible(true);
						Object[] fv = (Object[]) f.get(ai);
						java.lang.reflect.Field cf = ai.getClass().getDeclaredField("clazz");
						cf.setAccessible(true);
						Object vc = cf.get(ai);
						java.lang.reflect.Field sl = vc.getClass().getDeclaredField("fieldSlots");
						sl.setAccessible(true);
						java.util.Map<?, ?> slots = (java.util.Map<?, ?>) sl.get(vc);
						Object kIdx = slots.get("K|I"), aIdx = slots.get("a|I");
						ks.append(' ').append(i).append(':')
								.append(kIdx == null ? "?" : fv[(Integer) kIdx]).append('/')
								.append(aIdx == null ? "?" : fv[(Integer) aIdx]);
					} catch (Throwable ignored) {
					}
				}
				Log.i(TAG, ks.toString());
			}
			if (aJ != null) Log.i(TAG, "aJ=" + java.util.Arrays.toString(aJ));
			if (aI != null) Log.i(TAG, "aI=" + java.util.Arrays.toString(aI));
			// e.c resource blob + bD count — bE-null NPE forensics
			Object cb = m.get("c|[B"), bd = m.get("bD|I"), w = m.get("W|I");
			if (cb instanceof byte[]) {
				byte[] a = (byte[]) cb;
				StringBuilder hex = new StringBuilder("c len=").append(a.length)
						.append(" bD=").append(bd).append(" W=").append(w).append(" c[:64]=");
				for (int i = 0; i < Math.min(64, a.length); i++)
					hex.append(String.format("%02x", a[i]));
				Log.i(TAG, hex.toString());
			}
		} catch (Throwable t) {
			Log.i(TAG, "statics dump failed: " + t);
		}
	}

	/** follow a short[] next-index list from head; stop at -1 or the 3rd revisit. */
	private static String walk(short[] next, Integer head) {
		if (next == null || head == null)
			return "n/a";
		StringBuilder sb = new StringBuilder();
		java.util.HashSet<Integer> seen = new java.util.HashSet<>();
		for (int i = head, hops = 0; i >= 0 && i < next.length && hops < 520; hops++) {
			sb.append(' ').append(i);
			if (!seen.add(i)) {
				sb.append(" <CYCLE>");
				break;
			}
			i = next[i];
		}
		return sb.toString();
	}

	private void start() {
		running = true;
		// independent diagnostic thread: polls the stackdump marker and dumps
		// every Java stack to err.log — must NOT live on the pump loop (if the
		// pump is stuck, this is exactly when we need the dump).
		Thread sd = new Thread(() -> {
			String emuDir = Config.getEmulatorDir();
			if (emuDir == null)
				return;
			File marker = new File(emuDir, STACKDUMP_FILE);
			File staticsMarker = new File(emuDir, STATICS_FILE);
			while (running) {
				try {
					Thread.sleep(500);
					if (staticsMarker.exists()) {
						String cls = readRegex(staticsMarker);
						staticsMarker.delete();
						dumpStatics(cls);
					}
					if (!marker.exists())
						continue;
					marker.delete();
					StringBuilder sb = new StringBuilder("=== stackdump ===\n");
					for (java.util.Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
						Thread t = e.getKey();
						sb.append('"').append(t.getName()).append("' ").append(t.getState()).append('\n');
						for (StackTraceElement el : e.getValue())
							sb.append("    at ").append(el).append('\n');
					}
					System.err.println(sb);
					Log.i(TAG, "stackdump written");
				} catch (Throwable ignored) {
				}
			}
		}, "SnapVm-StackDump");
		sd.setDaemon(true);
		sd.start();
		pumpThread = new Thread(() -> {
			long lastOps = 0, lastLog = System.currentTimeMillis();
			long busy = 0;
			String emuDir = Config.getEmulatorDir();
			String profPath = emuDir == null ? null : emuDir + "/" + PROF_FILE;
			String samplePath = emuDir == null ? null : emuDir + "/" + SAMPLE_FILE;
			boolean profWas = false;
			while (running) {
				try {
					if (frozen) {
						// VK layout edit freezes the VM wholesale — no guest code
						// runs, pending repaints just queue up until thaw.
						Thread.sleep(10);
						continue;
					}
					long b0 = System.nanoTime();
					long opsBefore = vm.totalOps;
					vm.pump(profWas ? 4_000 : PUMP_BUDGET);
					busy += System.nanoTime() - b0;
					// Only yield when the slice found nothing to run — a fixed
					// sleep(1) here turns into a hard floor on tick rate for
					// games whose load loop is one short burst per tick.
					if (vm.totalOps == opsBefore)
						Thread.sleep(1);
					long now = System.currentTimeMillis();
						if (now - lastLog >= 1000) {
						boolean prof = profPath != null && new File(profPath).exists();
						if (prof != profWas) {
							vm.profileOn = prof;
							profWas = prof;
						}
						boolean wantSample = samplePath != null && new File(samplePath).exists();
						if (wantSample != (sampler != null)) {
							if (wantSample) {
								sampler = new VmSampler(vm, line -> Log.i(TAG, line));
								sampler.start();
								Log.i(TAG, "VmSampler on (marker " + SAMPLE_FILE + ")");
							} else {
								sampler.stop();
								sampler = null;
								Log.i(TAG, "VmSampler off");
							}
						}
						long ops = vm.totalOps - lastOps;
						lastOps = vm.totalOps;
						int n = paints, ab = vm.abandonedRuns, sc = vm.sleepCalls, sr = srCalls;
						long maxP = paintMsMax, gap = maxGap, tot = paintMsTotal, sm = vm.sleepMsTotal;
						long srT = srMsTotal, srM = srMsMax;
						paints = 0; paintMsMax = 0; paintMsTotal = 0; maxGap = 0;
						vm.sleepCalls = 0; vm.sleepMsTotal = 0; srCalls = 0; srMsTotal = 0; srMsMax = 0;
						StringBuilder sb = new StringBuilder(String.format(java.util.Locale.US,
								"%.1f MIPS | busy %dms | %d paints, avg %dms, maxP %dms, maxGap %dms, abandon %d | sleeps %d x ~%dms | sr %d avg %dms max %dms",
								ops * 1000.0 / (now - lastLog) / 1e6, busy / 1_000_000, n,
								n == 0 ? 0 : tot / n, maxP, gap, ab, sc, sc == 0 ? 0 : sm / sc,
								sr, sr == 0 ? 0 : srT / sr, srM));
						busy = 0;
						java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> top =
								new java.util.ArrayList<>(vm.callStats().entrySet());
						top.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
						int shown = 0;
						for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> e : top) {
							long c = e.getValue().getAndSet(0);
							if (c == 0) continue;
							if (shown++ < 6) sb.append(' ').append(e.getKey()).append('x').append(c);
						}
						Log.i(TAG, sb.toString());
						if (prof) {
							java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> pt =
									new java.util.ArrayList<>();
							synchronized (vm.profStats) {
								pt.addAll(vm.profStats.entrySet());
							}
							pt.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
							StringBuilder pb = new StringBuilder("prof:");
							int ps = 0;
							for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> e : pt) {
								long c = e.getValue().getAndSet(0);
								if (c == 0) continue;
								if (ps++ < 10) pb.append(' ').append(e.getKey()).append('x').append(c);
							}
							Log.i(TAG, pb.toString());
							Log.i(TAG, "threads:\n" + vm.dumpThreads(true));
						}
						lastLog = now;
					}
				} catch (InterruptedException ignored) {
				} catch (Throwable th) {
					Log.e(TAG, "pump loop", th);
					try {
						Thread.sleep(5);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}, "SnapVm-Pump");
		pumpThread.setDaemon(true);
		pumpThread.start();
	}

	public void stop() {
		running = false;
		if (sampler != null) {
			sampler.stop();
			sampler = null;
		}
	}

	/** Freeze/thaw all guest execution (VK layout edit etc.). */
	private volatile boolean frozen;

	public static void setFrozen(boolean freeze) {
		SnapVm s = instance;
		if (s != null) {
			s.frozen = freeze;
		}
	}

	// ---------- instant save / load ----------

	public static SnapVm instance() {
		return instance;
	}

	public void reportRestoredMidlet(MIDlet m) {
		restoredMidlet = m;
	}

	public void requestCurrent(Displayable d) {
		restoredCurrent = d;
	}

	// ---------- save slots ----------

	public static final int SLOT_COUNT = 20;

	/** One snapshot slot: file on disk + display name from slots.json (null → legacy). */
	public static final class SlotInfo {
		public int slot;
		public boolean exists;
		public String name;
		public long time;   // file lastModified
	}

	private File snapshotDir() {
		String dir = Config.getEmulatorDir();
		if (dir == null || appDir == null) {
			return null;
		}
		// snapshots live in their own tree, not inside per-game configs:
		// <emulator>/snapshots/<game>/snap<slot>.svm
		return new File(dir + Config.MIDLET_SNAPSHOTS_DIR + appDir.getName());
	}

	private File saveFile(int slot) {
		File d = snapshotDir();
		return d == null ? null : new File(d, "snap" + slot + ".svm");
	}

	private File metaFile() {
		File d = snapshotDir();
		return d == null ? null : new File(d, "slots.json");
	}

	// slots.json: {"counter": N, "names": {"<slot>": "<display name>"}}
	private org.json.JSONObject readMeta() {
		try {
			File f = metaFile();
			if (f == null || !f.exists()) {
				return new org.json.JSONObject();
			}
			byte[] b = new byte[(int) f.length()];
			java.io.FileInputStream in = new java.io.FileInputStream(f);
			try {
				int off = 0;
				while (off < b.length) {
					int n = in.read(b, off, b.length - off);
					if (n < 0) break;
					off += n;
				}
			} finally {
				in.close();
			}
			return new org.json.JSONObject(
					new String(b, java.nio.charset.StandardCharsets.UTF_8));
		} catch (Exception e) {
			return new org.json.JSONObject();
		}
	}

	private void writeMeta(org.json.JSONObject meta) {
		File f = metaFile();
		if (f == null) {
			return;
		}
		try {
			java.io.FileOutputStream out = new java.io.FileOutputStream(f);
			try {
				out.write(meta.toString().getBytes(
						java.nio.charset.StandardCharsets.UTF_8));
			} finally {
				out.close();
			}
		} catch (Exception e) {
			Log.w(TAG, "slot meta write failed", e);
		}
	}

	/** All {@link #SLOT_COUNT} slots; {@code exists} tells which hold a save. */
	public static java.util.List<SlotInfo> listSlots() {
		java.util.List<SlotInfo> out = new java.util.ArrayList<>();
		SnapVm s = instance;
		org.json.JSONObject names =
				s == null ? null : s.readMeta().optJSONObject("names");
		for (int i = 0; i < SLOT_COUNT; i++) {
			SlotInfo si = new SlotInfo();
			si.slot = i;
			File f = s == null ? null : s.saveFile(i);
			si.exists = f != null && f.exists();
			si.time = si.exists ? f.lastModified() : 0;
			si.name = names == null ? null : names.optString(String.valueOf(i), null);
			out.add(si);
		}
		return out;
	}

	/** 1-based number for the next auto name ("存档N-…"); bumps on each save. */
	public static int nextSaveIndex() {
		SnapVm s = instance;
		return s == null ? 1 : s.readMeta().optInt("counter", 0) + 1;
	}

	/** Freeze the guest machine into slot {@code slot} and record its display name. */
	public static void saveGame(int slot, String name) throws Exception {
		SnapVm s = instance;
		if (s == null) {
			throw new IllegalStateException("SnapVM not running");
		}
		byte[] snap = s.vm.checkpoint();
		File f = s.saveFile(slot);
		if (f == null) {
			throw new IllegalStateException("no save dir");
		}
		File parent = f.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		java.io.FileOutputStream out = new java.io.FileOutputStream(f);
		try {
			out.write(snap);
		} finally {
			out.close();
		}
		org.json.JSONObject meta = s.readMeta();
		meta.put("counter", meta.optInt("counter", 0) + 1);
		org.json.JSONObject names = meta.optJSONObject("names");
		if (names == null) {
			names = new org.json.JSONObject();
			meta.put("names", names);
		}
		names.put(String.valueOf(slot), name);
		s.writeMeta(meta);
		Log.i(TAG, "saved " + snap.length + " bytes to " + f);
	}

	public static void renameSlot(int slot, String name) {
		SnapVm s = instance;
		if (s == null || s.saveFile(slot) == null || !s.saveFile(slot).exists()) {
			return;
		}
		org.json.JSONObject meta = s.readMeta();
		org.json.JSONObject names = meta.optJSONObject("names");
		try {
			if (names == null) {
				names = new org.json.JSONObject();
				meta.put("names", names);
			}
			names.put(String.valueOf(slot), name);
			s.writeMeta(meta);
		} catch (org.json.JSONException e) {
			Log.w(TAG, "rename failed", e);
		}
	}

	public static void deleteSlot(int slot) {
		SnapVm s = instance;
		if (s == null) {
			return;
		}
		File f = s.saveFile(slot);
		if (f != null) {
			f.delete();
		}
		org.json.JSONObject meta = s.readMeta();
		org.json.JSONObject names = meta.optJSONObject("names");
		if (names != null) {
			names.remove(String.valueOf(slot));
			s.writeMeta(meta);
		}
	}

	/**
	 * Load the snapshot and swap the machine state in place. Restored shadows
	 * are rebound onto the live shell: the midlet goes back to MidletThread,
	 * and the displayable that was current at save time is re-shown.
	 */
	public static void loadGame(int slot) throws Exception {
		SnapVm s = instance;
		if (s == null) {
			throw new IllegalStateException("SnapVM not running");
		}
		File f = s.saveFile(slot);
		if (f == null || !f.exists()) {
			throw new IllegalStateException("no save file");
		}
		byte[] snap = new byte[(int) f.length()];
		java.io.FileInputStream in = new java.io.FileInputStream(f);
		try {
			int off = 0;
			while (off < snap.length) {
				int n = in.read(snap, off, snap.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
		} finally {
			in.close();
		}
		s.restoredMidlet = null;
		s.restoredCurrent = null;
		s.vm.restore(snap);
		if (s.restoredMidlet != null) {
			MidletThread.rebindMidlet(s.restoredMidlet);
		}
		if (s.restoredCurrent != null) {
			Displayable d = s.restoredCurrent;
			s.restoredCurrent = null;
			if (d instanceof GuestShadow) {
				// the re-show is a host-side reconstruction, not a real
				// hide->show transition — games that auto-pause in showNotify
				// must not see it
				((GuestShadow) d).suppressNextLifecycleNotify();
			}
			Display.getDisplay(s.restoredMidlet).setCurrent(d);
			if (d instanceof Canvas) {
				((Canvas) d).repaint();
			}
		}
		Log.i(TAG, "restored " + snap.length + " bytes");
	}

	private static void copyProps(Vm vm) {
		// MicroLoader.applyConfiguration() ran before loadMIDlet, so the host
		// table already holds the profile's SystemProperties (supports.*,
		// microedition.*, fileconn.*, vendor keys...). Mirror the whole table —
		// on the dex path the guest reads this same table via System.getProperty.
		for (String n : System.getProperties().stringPropertyNames()) {
			vm.setJ2meProperty(n, System.getProperty(n));
		}
	}
}
