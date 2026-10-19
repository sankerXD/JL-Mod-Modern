package org.snapvm.adapter;

import android.util.Log;

import java.io.File;
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

	public static MIDlet load(File appDir, String mainClass) throws Exception {
		if (instance != null) {
			throw new IllegalStateException("SnapVm already running");
		}
		SnapVm s = new SnapVm();
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
		s.vm.registerParkedCall("javax/microedition/lcdui/Canvas.serviceRepaints()V",
				(v, t, args, done) -> SnapVmBridge.serviceRepaints(v, t, args[0], done));
		VmObject guest = s.vm.newGuestObject(mainClass);
		s.start();
		instance = s;
		Object shadow = guest.hostShadow;
		if (!(shadow instanceof MIDlet)) {
			throw new IllegalStateException("midlet shadow not created: " + shadow);
		}
		return (MIDlet) shadow;
	}

	private void start() {
		running = true;
		pumpThread = new Thread(() -> {
			while (running) {
				try {
					vm.pump(PUMP_BUDGET);
					Thread.sleep(1);
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
