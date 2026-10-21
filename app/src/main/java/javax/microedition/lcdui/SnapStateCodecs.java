package javax.microedition.lcdui;

import java.util.TimerTask;

import javax.microedition.midlet.MIDlet;

import org.snapvm.ShadowCodec;
import org.snapvm.Vm;
import org.snapvm.VmObject;
import org.snapvm.adapter.SnapVm;

/**
 * ShadowCodecs for the SnapVM path. Lives in the lcdui package for
 * package-private access to Displayable.commands/listener/ticker.
 *
 * A fresh shadow is built by the registered ShadowFactory during restore;
 * these codecs fold the captured host-side state back onto it.
 */
public final class SnapStateCodecs {
	private SnapStateCodecs() {
	}

	public static void registerAll(Vm vm) {
		ShadowCodec displayable = new DisplayableCodec();
		vm.registerShadowCodec(Canvas.class, displayable);
		vm.registerShadowCodec(com.nokia.mid.ui.FullCanvas.class, displayable);
		vm.registerShadowCodec(javax.microedition.lcdui.game.GameCanvas.class, displayable);
		vm.registerShadowCodec(TextBox.class, displayable);
		vm.registerShadowCodec(Form.class, displayable);
		vm.registerShadowCodec(List.class, displayable);
		vm.registerShadowCodec(Alert.class, displayable);
		// game-package shadows: all state is plain fields on Layer/Sprite/
		// TiledLayer — scope the reflective capture to those declared classes
		// so the shadow's own vm/guest fields never serialize.
		vm.registerShadowCodec(javax.microedition.lcdui.game.Sprite.class,
				new LayerCodec(javax.microedition.lcdui.game.Sprite.class));
		vm.registerShadowCodec(javax.microedition.lcdui.game.TiledLayer.class,
				new LayerCodec(javax.microedition.lcdui.game.TiledLayer.class));
		// stateless shadows: capture nothing, but report the rebuilt midlet
		// so SnapVm can rebind MidletThread's lifecycle target.
		vm.registerShadowCodec(MIDlet.class, new ShadowCodec() {
			public Object capture(Vm vm, VmObject guest, Object shadow) {
				return null;
			}

			public void restore(Vm vm, VmObject guest, Object shadow, Object rec) {
				SnapVm s = SnapVm.instance();
				if (s != null) {
					s.reportRestoredMidlet((MIDlet) shadow);
				}
			}
		});
		vm.registerShadowCodec(TimerTask.class, new ShadowCodec() {
			public Object capture(Vm vm, VmObject guest, Object shadow) {
				return null;
			}

			public void restore(Vm vm, VmObject guest, Object shadow, Object rec) {
			}
		});
	}

	/**
	 * Canvas-family Displayables: the guest-visible host state is title,
	 * ticker, command set, command listener, fullscreen flag and whether
	 * this displayable is the one currently on screen.
	 */
	private static final class DisplayableCodec implements ShadowCodec {
		public Object capture(Vm vm, VmObject guest, Object shadow) {
			Displayable d = (Displayable) shadow;
			Display disp = Display.getDisplay(null);
			return new Object[]{
					d.getTitle(),
					d.getTicker() == null ? null : d.getTicker().getString(),
					d.commands.toArray(),
					d.listener,
					disp != null && disp.getCurrent() == d,
					d instanceof Canvas && fullScreenOf((Canvas) d),
					gameBufferOf(d),
					d instanceof TextBox ? ((TextBox) d).getString() : null,
					alertExtras(d),
					listContents(d),
			};
		}

		public void restore(Vm vm, VmObject guest, Object shadow, Object rec) {
			Object[] r = (Object[]) rec;
			Displayable d = (Displayable) shadow;
			if (r[0] != null) {
				d.setTitle((String) r[0]);
			}
			if (r[1] != null) {
				d.setTicker(new Ticker((String) r[1]));
			}
			for (Object c : (Object[]) r[2]) {
				d.addCommand((Command) c);
			}
			if (r[3] != null) {
				d.setCommandListener(
						(CommandListener) vm.toHost(r[3], CommandListener.class));
			}
			if (Boolean.TRUE.equals(r[4])) {
				SnapVm s = SnapVm.instance();
				if (s != null) {
					s.requestCurrent(d);
				}
			}
			if (r[5] != null && d instanceof Canvas) {
				((Canvas) d).setFullScreenMode((Boolean) r[5]);
			}
			// GameCanvas.image is the buffer the guest draws into through the
			// Graphics it got from getGraphics(); a fresh shadow has a blank
			// buffer while the guest's Graphics still references the revived
			// image — rebind so flushGraphics() pushes real pixels again.
			if (r.length > 6 && r[6] instanceof Image
					&& d instanceof javax.microedition.lcdui.game.GameCanvas) {
				rebindGameBuffer(
						(javax.microedition.lcdui.game.GameCanvas) d,
						(Image) r[6]);
			}
			if (r.length > 7 && r[7] instanceof String && d instanceof TextBox) {
				((TextBox) d).setString((String) r[7]);
			}
			if (r.length > 8 && r[8] instanceof Object[] && d instanceof Alert) {
				Alert a = (Alert) d;
				Object[] e = (Object[]) r[8];
				if (e[0] instanceof String) {
					a.setString((String) e[0]);
				}
				if (e[1] instanceof Image) {
					a.setImage((Image) e[1]);
				}
				if (e[2] instanceof AlertType) {
					a.setType((AlertType) e[2]);
				}
				if (e[3] instanceof Integer) {
					a.setTimeout((Integer) e[3]);
				}
			}
			if (r.length > 9 && r[9] instanceof Object[] && d instanceof List) {
				List l = (List) d;
				Object[] c = (Object[]) r[9];
				String[] texts = (String[]) c[0];
				Image[] imgs = (Image[]) c[1];
				boolean[] sel = (boolean[]) c[2];
				l.deleteAll();
				for (int i = 0; i < texts.length; i++) {
					l.append(texts[i], imgs == null ? null : imgs[i]);
				}
				if (sel != null && texts.length > 0) {
					l.setSelectedFlags(sel);
				}
			}
		}

		private static Object alertExtras(Displayable d) {
			if (!(d instanceof Alert)) {
				return null;
			}
			Alert a = (Alert) d;
			return new Object[]{a.getString(), a.getImage(), a.getType(),
					a.getTimeout()};
		}

		private static Object listContents(Displayable d) {
			if (!(d instanceof List)) {
				return null;
			}
			List l = (List) d;
			int n = l.size();
			String[] texts = new String[n];
			Image[] imgs = new Image[n];
			boolean[] sel = new boolean[n];
			for (int i = 0; i < n; i++) {
				texts[i] = l.getString(i);
				imgs[i] = l.getImage(i);
			}
			l.getSelectedFlags(sel);
			return new Object[]{texts, imgs, sel};
		}

		private static boolean fullScreenOf(Canvas c) {
			try {
				java.lang.reflect.Field f = Canvas.class.getDeclaredField("fullscreen");
				f.setAccessible(true);
				return f.getBoolean(c);
			} catch (Exception e) {
				return false;
			}
		}

		private static Image gameBufferOf(Displayable d) {
			if (!(d instanceof javax.microedition.lcdui.game.GameCanvas)) {
				return null;
			}
			try {
				java.lang.reflect.Field f =
						javax.microedition.lcdui.game.GameCanvas.class
								.getDeclaredField("image");
				f.setAccessible(true);
				return (Image) f.get(d);
			} catch (Exception e) {
				return null;
			}
		}

		private static void rebindGameBuffer(
				javax.microedition.lcdui.game.GameCanvas d, Image img) {
			try {
				java.lang.reflect.Field f =
						javax.microedition.lcdui.game.GameCanvas.class
								.getDeclaredField("image");
				f.setAccessible(true);
				f.set(d, img);
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Sprite/TiledLayer shadows: captures every instance field declared on the
	 * host classes (x/y/w/h/visible on Layer; frames/transform/collision on
	 * Sprite; cell matrix on TiledLayer). The walk starts at {@code scope},
	 * NOT the shadow's runtime class — the Snap*Shadow fields (guest, fwd)
	 * must never be serialized.
	 */
	private static final class LayerCodec implements ShadowCodec {
		private final Class<?> scope;

		LayerCodec(Class<?> scope) {
			this.scope = scope;
		}

		public Object capture(Vm vm, VmObject guest, Object shadow) {
			try {
				java.util.List<String> names = new java.util.ArrayList<>();
				java.util.List<Object> vals = new java.util.ArrayList<>();
				for (Class<?> c = scope; c != null && c != Object.class;
						c = c.getSuperclass()) {
					for (java.lang.reflect.Field f : c.getDeclaredFields()) {
						int m = f.getModifiers();
						if (java.lang.reflect.Modifier.isStatic(m)
								|| f.isSynthetic()) {
							continue;
						}
						f.setAccessible(true);
						names.add(c.getName() + "|" + f.getName());
						vals.add(f.get(shadow));
					}
				}
				return new Object[]{names.toArray(new String[0]), vals.toArray()};
			} catch (Exception e) {
				throw new org.snapvm.VmError(
						"Layer capture failed on " + shadow.getClass().getName(), e);
			}
		}

		public void restore(Vm vm, VmObject guest, Object shadow, Object rec) {
			Object[] r = (Object[]) rec;
			String[] names = (String[]) r[0];
			Object[] vals = (Object[]) r[1];
			try {
				for (int i = 0; i < names.length; i++) {
					int bar = names[i].lastIndexOf('|');
					java.lang.reflect.Field f = Class
							.forName(names[i].substring(0, bar))
							.getDeclaredField(names[i].substring(bar + 1));
					f.setAccessible(true);
					f.set(shadow, vals[i]);
				}
			} catch (Exception e) {
				throw new org.snapvm.VmError(
						"Layer restore failed on " + shadow.getClass().getName(), e);
			}
		}
	}
}
