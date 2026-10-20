package org.snapvm.adapter;

import android.util.Log;

import java.io.File;
import java.util.TimerTask;
import java.util.zip.ZipFile;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.SnapVmBridge;
import javax.microedition.midlet.MIDlet;
import javax.microedition.shell.AppClassLoader;

import org.snapvm.Vm;
import org.snapvm.VmObject;

import ru.playsoftware.j2meloader.config.Config;

/**
 * SnapVM execution path: loads the midlet jar into the interpreter instead of
 * the dex pipeline, wires host shadows for guest-extends-host classes, and runs
 * a scheduler thread that services guest green threads.
 *
 * Enabled per device by creating the marker file "snapvm.on" in the emulator
 * data dir; absent the file the classic dex path runs unchanged.
 */
public final class SnapVm {
	private static final String TAG = "SnapVm";
	private static final long PUMP_BUDGET = 200_000;
	private static final String ENABLE_FILE = "snapvm.on";
	private static final String TRACE_FILE = "snapvm.trace";
	private static final String TRACE_CALLS_FILE = "snapvm.tracecalls";
	private static final String STATS_FILE = "snapvm.stats";
	private static final String PROF_FILE = "snapvm.prof";
	private static final String ERR_FILE = "snapvm.err";

	private static volatile SnapVm instance;

	private final Vm vm = new Vm();
	private volatile boolean running;
	private Thread pumpThread;

	private SnapVm() {
	}

	public static boolean enabled() {
		String dir = Config.getEmulatorDir();
		return dir != null && new File(dir, ENABLE_FILE).exists();
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
		s.vm.registerParkedCall("javax/microedition/lcdui/Canvas.serviceRepaints()V",
				(v, t, args, done) -> SnapVmBridge.serviceRepaints(v, t, args[0], done));
		LcduiFastCalls.register(s.vm);
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

	private void start() {
		running = true;
		pumpThread = new Thread(() -> {
			long lastOps = 0, lastLog = System.currentTimeMillis();
			long busy = 0;
			String profPath = Config.getEmulatorDir() == null ? null
					: Config.getEmulatorDir() + "/" + PROF_FILE;
			boolean profWas = false;
			while (running) {
				try {
					boolean prof = profPath != null && new File(profPath).exists();
					if (prof != profWas) {
						vm.profileOn = prof;
						profWas = prof;
					}
					long b0 = System.nanoTime();
					vm.pump(prof ? 4_000 : PUMP_BUDGET);
					busy += System.nanoTime() - b0;
					Thread.sleep(1);
					long now = System.currentTimeMillis();
						if (now - lastLog >= 1000) {
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
