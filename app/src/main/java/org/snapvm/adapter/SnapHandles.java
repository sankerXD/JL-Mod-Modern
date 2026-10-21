package org.snapvm.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Ticker;
import javax.microedition.lcdui.game.LayerManager;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.lcdui.game.TiledLayer;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.protocol.DataSource;
import javax.microedition.rms.RecordListener;
import javax.microedition.rms.RecordStore;

import org.snapvm.GuestShadow;
import org.snapvm.HostHandle;
import org.snapvm.Vm;
import org.snapvm.VmError;

/**
 * HostHandle codecs for host objects that live in guest state under the
 * SnapVM path. Every guest-reachable lcdui/rms object needs one; anything
 * missing fails the save loudly (VmError names the class).
 *
 * Records are plain snapshot values (scalars, strings, arrays, guest refs)
 * — they may themselves contain handled host objects (a Sprite's Image).
 */
public final class SnapHandles {
	private SnapHandles() {
	}

	public static void registerAll(Vm vm) {
		vm.registerHostHandle(Image.class, new ImageHandle());
		vm.registerHostHandle(Graphics.class, new GraphicsHandle());
		vm.registerHostHandle(Font.class, new FontHandle());
		vm.registerHostHandle(Display.class, new DisplayHandle());
		vm.registerHostHandle(Command.class, new CommandHandle());
		vm.registerHostHandle(Ticker.class, new TickerHandle());
		vm.registerHostHandle(RecordStore.class, new RmsHandle());
		vm.registerHostHandle(Player.class, new PlayerHandle());
		vm.registerHostHandle(Calendar.class, new CalendarHandle());
		vm.registerHostHandle(Timer.class, new TimerHandle());
		// game package: all-private int/Image field state — generic
		// reflective capture covers them without Unsafe.
		vm.registerHostHandle(Sprite.class,
				new ReflectHandle(() -> new Sprite(Image.createImage(1, 1))));
		vm.registerHostHandle(TiledLayer.class,
				new ReflectHandle(() -> new TiledLayer(1, 1, Image.createImage(1, 1), 1, 1)));
		vm.registerHostHandle(LayerManager.class,
				new ReflectHandle(LayerManager::new));
	}

	/** Image → PNG bytes + mutability + the b/w-alpha flag. */
	private static final class ImageHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Image img = (Image) h;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			img.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, bos);
			return new Object[]{bos.toByteArray(), img.isMutable(), img.isBlackWhiteAlpha()};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			byte[] png = (byte[]) r[0];
			Bitmap b = BitmapFactory.decodeByteArray(png, 0, png.length);
			if (Boolean.TRUE.equals(r[1])) {
				b = b.copy(Bitmap.Config.ARGB_8888, true);
			}
			Image img = new Image(b);
			if (Boolean.TRUE.equals(r[2])) {
				img.setBlackWhiteAlpha(true);
			}
			return img;
		}
	}

	/**
	 * Graphics always wraps an Image target — capture the image plus the
	 * drawing state (translate, clip, color, stroke, font) and rebuild via
	 * image.getGraphics(). All fields are private → reflective reads.
	 */
	private static final class GraphicsHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				Class<?> c = h.getClass();
				android.graphics.Rect clip =
						(android.graphics.Rect) field(c, "clip").get(h);
				return new Object[]{
						field(c, "image").get(h),
						field(c, "translateX").get(h),
						field(c, "translateY").get(h),
						clip.left, clip.top, clip.right, clip.bottom,
						((Graphics) h).getColor(),
						((Graphics) h).getStrokeStyle(),
						((Graphics) h).getFont(),
				};
			} catch (Exception e) {
				throw new VmError("Graphics capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Graphics g = ((Image) r[0]).getGraphics();
			int l = (Integer) r[3], t = (Integer) r[4];
			g.setClip(l, t, (Integer) r[5] - l, (Integer) r[6] - t);
			g.translate((Integer) r[1], (Integer) r[2]);
			g.setColor((Integer) r[7]);
			g.setStrokeStyle((Integer) r[8]);
			g.setFont((Font) r[9]);
			return g;
		}
	}

	/** Font → face/style/size; revive through getFont so canonical identity holds. */
	private static final class FontHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Font f = (Font) h;
			return new Object[]{f.getFace(), f.getStyle(), f.getSize()};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			return Font.getFont((Integer) r[0], (Integer) r[1], (Integer) r[2]);
		}
	}

	/** Display is a process-level singleton — revive maps to the live one. */
	private static final class DisplayHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			return null;
		}

		public Object revive(Vm vm, Object rec) {
			return Display.getDisplay(null);
		}
	}

	private static final class CommandHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Command c = (Command) h;
			return new Object[]{c.getLabel(), c.getLongLabel(), c.getCommandType(), c.getPriority()};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			return new Command((String) r[0], (String) r[1], (Integer) r[2], (Integer) r[3]);
		}
	}

	private static final class TickerHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			return ((Ticker) h).getString();
		}

		public Object revive(Vm vm, Object rec) {
			return new Ticker((String) rec);
		}
	}

	/**
	 * RecordStore contents live on disk already; the open handle needs its
	 * name (private on the impl) and its guest record listeners (proxies).
	 */
	private static final class RmsHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				Field name = field(h.getClass(), "recordStoreName");
				Field listeners = field(h.getClass(), "recordListeners");
				return new Object[]{name.get(h),
						((java.util.Vector<?>) listeners.get(h)).toArray()};
			} catch (Exception e) {
				throw new VmError("RecordStore capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			try {
				RecordStore rs = RecordStore.openRecordStore((String) r[0], true);
				for (Object l : (Object[]) r[1]) {
					rs.addRecordListener((RecordListener) vm.toHost(l, RecordListener.class));
				}
				return rs;
			} catch (Exception e) {
				throw new VmError("RecordStore revive failed", e);
			}
		}
	}

	/**
	 * Player: capture the locator (from its DataSource), lifecycle state,
	 * playback position and mixer settings, then rebuild through
	 * Manager.createPlayer. Guest PlayerListeners round-trip as proxies.
	 */
	private static final class PlayerHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Player p = (Player) h;
			try {
				// impl field names differ: MicroPlayer.source,
				// SynthPlayer.dataSource — locate by type, not name
				String locator = locatorOf(h);
				Object[] listeners;
				try {
					listeners = ((java.util.ArrayList<?>)
							field(h.getClass(), "listeners").get(h)).toArray();
				} catch (NoSuchFieldException e) {
					listeners = new Object[0];
				}
				return new Object[]{
						locator, p.getState(),
						fieldOr(h, 1, "loopCount"),
						fieldOr(h, false, "mute"),
						fieldOr(h, 100, "level", "volume"),
						fieldOr(h, 0, "pan"),
						safeMediaTime(p), listeners,
				};
			} catch (VmError e) {
				throw e;
			} catch (Exception e) {
				throw new VmError("Player capture failed", e);
			}
		}

		private static String locatorOf(Object h) throws IllegalAccessException {
			for (Class<?> x = h.getClass(); x != null; x = x.getSuperclass()) {
				for (Field f : x.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers())
							&& DataSource.class.isAssignableFrom(f.getType())) {
						f.setAccessible(true);
						Object ds = f.get(h);
						return ds != null ? ((DataSource) ds).getLocator() : null;
					}
				}
			}
			return null;
		}

		private static Object fieldOr(Object h, Object def, String... names)
				throws IllegalAccessException {
			for (String n : names) {
				try {
					return field(h.getClass(), n).get(h);
				} catch (NoSuchFieldException ignored) {
				}
			}
			return def;
		}

		private static long safeMediaTime(Player p) {
			try {
				return p.getMediaTime();
			} catch (Exception e) {
				return -1;
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			String locator = (String) r[0];
			try {
				// no DataSource-typed field (RecordPlayer, stream players
				// without one) — revive as a silent stub rather than fail
				// the whole load; game calls on it just no-op
				Player p = locator != null
						? Manager.createPlayer(locator)
						: new javax.microedition.media.BasePlayer();
				int state = (Integer) r[1];
				if (state >= Player.REALIZED) {
					p.realize();
				}
				if (state >= Player.PREFETCHED) {
					p.prefetch();
				}
				p.setLoopCount((Integer) r[2]);
				// MicroPlayer is package-private — volume/mute go through
				// its VolumeControl; pan through reflection.
				javax.microedition.media.Control vc =
						p.getControl(javax.microedition.media.control.VolumeControl.class.getName());
				if (vc instanceof javax.microedition.media.control.VolumeControl) {
					javax.microedition.media.control.VolumeControl v =
							(javax.microedition.media.control.VolumeControl) vc;
					v.setMute((Boolean) r[3]);
					v.setLevel((Integer) r[4]);
				}
				try {
					java.lang.reflect.Method m =
							p.getClass().getMethod("setPan", int.class);
					m.setAccessible(true);
					m.invoke(p, r[5]);
				} catch (Exception ignored) {
				}
				long mt = (Long) r[6];
				if (mt >= 0) {
					p.setMediaTime(mt);
				}
				for (Object l : (Object[]) r[7]) {
					p.addPlayerListener(
							(PlayerListener) vm.toHost(l, PlayerListener.class));
				}
				if (state == Player.STARTED) {
					p.start();
				}
				return p;
			} catch (Exception e) {
				throw new VmError("Player revive failed", e);
			}
		}
	}

	/**
	 * java.util.Timer: the Harmony-derived impl on Android keeps a
	 * TimerImpl thread with a TimerHeap of pending tasks. Captures each
	 * pending SnapTimerTaskShadow's guest object plus when/period/fixedRate;
	 * revive schedules the rebuilt shadows onto a fresh Timer. Tasks whose
	 * shadow is missing (never seen by the snapshot graph) are skipped.
	 */
	private static final class TimerHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				List<Object[]> recs = new ArrayList<>();
				// alias-installed impl: task schedule params were recorded at
				// schedule() time — no reflection into JDK internals (ART
				// returns empty getDeclaredFields for java.util.Timer).
				SnapTimer st = (SnapTimer) h;
				st.dropDead();
				synchronized (st.armed) {
					for (Map.Entry<TimerTask, SnapTimer.Meta> e
							: st.armed.entrySet()) {
						TimerTask task = e.getKey();
						SnapTimer.Meta m = e.getValue();
						Object guest = task instanceof GuestShadow
								? ((GuestShadow) task).guest() : null;
						if (guest == null) {
							continue;
						}
						// a one-shot that already fired is done — JDK removed
						// it from the heap, so drop it here too
						if (m.period < 0 && task instanceof SnapTimerTaskShadow
								&& ((SnapTimerTaskShadow) task).ranOnce()) {
							continue;
						}
						recs.add(new Object[]{guest, m.when, m.period,
								m.fixedRate});
					}
				}
				return recs.toArray();
			} catch (Exception e) {
				throw new VmError("Timer capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Timer t = new SnapTimer();
			for (Object o : (Object[]) rec) {
				Object[] r = (Object[]) o;
				Object shadow = ((org.snapvm.VmObject) r[0]).hostShadow;
				if (!(shadow instanceof TimerTask)) {
					continue;
				}
				TimerTask task = (TimerTask) shadow;
				long when = (Long) r[1], period = (Long) r[2];
				if (period <= 0) {
					t.schedule(task, new Date(when));
				} else {
					long delay = Math.max(0L, when - System.currentTimeMillis());
					if ((Boolean) r[3]) {
						t.scheduleAtFixedRate(task, delay, period);
					} else {
						t.schedule(task, delay, period);
					}
				}
			}
			return t;
		}
	}

	private static final class CalendarHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Calendar c = (Calendar) h;
			return new Object[]{c.getTimeInMillis(), c.getTimeZone().getID()};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone((String) r[1]));
			c.setTimeInMillis((Long) r[0]);
			return c;
		}
	}

	/**
	 * Generic codec for bean-like host classes: captures every instance field
	 * (including superclasses, minus statics/synthetics) and rebuilds by
	 * stuffing them into a fresh dummy-constructed instance. Nested host
	 * objects flow through their own handles; unhandled ones fail the save
	 * with a named class — which is what we want.
	 *
	 * A field that self-references the object would resolve to the reader's
	 * PEND placeholder mid-record, so don't use this on self-referential
	 * types (Sprite/TiledLayer/LayerManager don't self-reference).
	 */
	private static final class ReflectHandle implements HostHandle {
		private final Factory factory;

		interface Factory {
			Object create();
		}

		ReflectHandle(Factory f) {
			this.factory = f;
		}

		public Object capture(Vm vm, Object h) {
			try {
				List<String> names = new ArrayList<>();
				List<Object> vals = new ArrayList<>();
				for (Class<?> c = h.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
					for (Field f : c.getDeclaredFields()) {
						int m = f.getModifiers();
						if (Modifier.isStatic(m) || f.isSynthetic()) {
							continue;
						}
						f.setAccessible(true);
						names.add(c.getName() + "|" + f.getName());
						vals.add(f.get(h));
					}
				}
				return new Object[]{names.toArray(new String[0]), vals.toArray()};
			} catch (VmError e) {
				throw e;
			} catch (Exception e) {
				throw new VmError("reflective capture failed on " + h.getClass().getName(), e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			String[] names = (String[]) r[0];
			Object[] vals = (Object[]) r[1];
			Object inst = factory.create();
			try {
				for (int i = 0; i < names.length; i++) {
					int bar = names[i].lastIndexOf('|');
					Field f = field(Class.forName(names[i].substring(0, bar)),
							names[i].substring(bar + 1));
					f.set(inst, vals[i]);
				}
			} catch (Exception e) {
				throw new VmError("reflective revive failed on " + inst.getClass().getName(), e);
			}
			return inst;
		}
	}

	private static Field field(Class<?> c, String name) throws NoSuchFieldException {
		for (Class<?> x = c; x != null; x = x.getSuperclass()) {
			try {
				Field f = x.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(name);
	}
}
