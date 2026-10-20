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
	}
}
