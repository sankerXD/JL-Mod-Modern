package ru.woesss.j2me.installer;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.Locale;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.config.Config;

/**
 * Headless batch installer for mass frontend testing: converts every jar in
 * a directory through the same {@link AppInstaller} pipeline the dialog uses
 * (dex + res jar + manifest + DB row), skipping games already installed.
 * Triggered by the {@code <package>.BATCHINSTALL} broadcast (debug builds):
 *
 *   am broadcast -a io.github.sankerxd.jlmodern.debug.BATCHINSTALL \
 *       --es dir /sdcard/roms/JAVA
 *
 * Runs on a worker thread; keep the app in the foreground for the duration
 * (each jar gets dexed — a full library takes a while). Progress + final
 * counts go to logcat under TAG.
 */
public final class BatchInstall {
	private static final String TAG = "BatchInstall";

	private static volatile Thread worker;

	private BatchInstall() {
	}

	public static void start(String dirPath) {
		if (worker != null && worker.isAlive()) {
			Log.w(TAG, "already running — ignoring " + dirPath);
			return;
		}
		worker = new Thread(() -> run(dirPath), "BatchInstall");
		worker.start();
	}

	private static void run(String dirPath) {
		File[] jars = new File(dirPath).listFiles((d, n) ->
				n.toLowerCase(Locale.ROOT).endsWith(".jar"));
		if (jars == null || jars.length == 0) {
			Log.w(TAG, "no jars under " + dirPath);
			return;
		}
		AppListModel model = new AppListModel();
		model.setEmulatorDirectory(Config.getEmulatorDir());
		int ok = 0, skip = 0, fail = 0, i = 0;
		for (File jar : jars) {
			i++;
			String name = jar.getName();
			try {
				AppInstaller ins = new AppInstaller(null,
						Uri.fromFile(jar), model);
				int st = Single.create(ins::loadInfo).blockingGet();
				if (st == AppInstaller.STATUS_NEW)
					st = Single.create(ins::install).blockingGet();
				if (st == AppInstaller.STATUS_SUCCESS)
					ok++;
				else
					skip++;
				Log.i(TAG, "[" + i + "/" + jars.length + "] " + name
						+ " -> " + st);
			} catch (Throwable t) {
				fail++;
				Log.e(TAG, "[" + i + "/" + jars.length + "] " + name, t);
			}
		}
		Log.i(TAG, "done: ok=" + ok + " skip=" + skip + " fail=" + fail
				+ " of " + jars.length);
	}
}
