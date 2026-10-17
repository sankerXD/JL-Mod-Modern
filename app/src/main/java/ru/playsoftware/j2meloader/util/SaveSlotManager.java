/*
 * Copyright 2024 JL-Mod Modern contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.playsoftware.j2meloader.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ru.playsoftware.j2meloader.config.Config;

/**
 * Save slots for J2ME games.
 * <p>
 * The emulated JVM cannot be frozen, so this snapshots what actually carries the
 * progress: the game's own persistent data (RMS record stores, which JL-Mod
 * writes to {@code /sdcard/JL-Mod/data/<game>/} immediately on every save).
 * Loading a slot copies the files back and restarts the game, which makes the
 * MIDlet read them again.
 * <p>
 * Slots live in {@code /sdcard/JL-Mod/saves/<game>/slot<N>/}.
 */
public final class SaveSlotManager {
	public static final int SLOT_COUNT = 10;
	/** the state before loading a slot, so a wrong load can be undone */
	public static final int BACKUP_SLOT = 0;

	private SaveSlotManager() {
	}

	public static File getDataDir(String appName) {
		return new File(Config.getDataDir(), appName);
	}

	public static File getSavesDir(String appName) {
		return new File(Config.getEmulatorDir(), "saves" + File.separator + appName);
	}

	public static File getSlotDir(String appName, int slot) {
		return new File(getSavesDir(appName), slot == BACKUP_SLOT ? "backup" : "slot" + slot);
	}

	public static boolean hasSlot(String appName, int slot) {
		File dir = getSlotDir(appName, slot);
		String[] files = dir.list();
		return dir.isDirectory() && files != null && files.length > 0;
	}

	public static long getSlotTime(String appName, int slot) {
		return getSlotDir(appName, slot).lastModified();
	}

	public static boolean deleteSlot(String appName, int slot) {
		return deleteTree(getSlotDir(appName, slot));
	}

	/**
	 * Snapshots the current data of the game into the slot.
	 *
	 * @return number of copied files, or -1 on error
	 */
	public static int save(String appName, int slot) {
		File src = getDataDir(appName);
		File dst = getSlotDir(appName, slot);
		if (!src.isDirectory()) {
			return -1;
		}
		if (dst.exists() && !deleteTree(dst)) {
			return -1;
		}
		//noinspection ResultOfMethodCallIgnored
		dst.mkdirs();
		return copyTree(src, dst);
	}

	/**
	 * Restores a slot over the game data, keeping the previous state in
	 * {@link #BACKUP_SLOT}.
	 *
	 * @return number of restored files, or -1 on error
	 */
	public static int restore(String appName, int slot) {
		File src = getSlotDir(appName, slot);
		if (!hasSlot(appName, slot)) {
			return -1;
		}
		File data = getDataDir(appName);
		// keep what is on the device now, so an accidental load can be undone
		File backup = getSlotDir(appName, BACKUP_SLOT);
		if (data.isDirectory()) {
			deleteTree(backup);
			//noinspection ResultOfMethodCallIgnored
			backup.mkdirs();
			copyTree(data, backup);
			deleteContents(data);
		} else {
			//noinspection ResultOfMethodCallIgnored
			data.mkdirs();
		}
		return copyTree(src, data);
	}

	private static int copyTree(File src, File dst) {
		File[] files = src.listFiles();
		if (files == null) {
			return 0;
		}
		int count = 0;
		for (File f : files) {
			File target = new File(dst, f.getName());
			if (f.isDirectory()) {
				//noinspection ResultOfMethodCallIgnored
				target.mkdirs();
				int n = copyTree(f, target);
				if (n < 0) {
					return -1;
				}
				count += n;
			} else {
				if (!copyFile(f, target)) {
					return -1;
				}
				count++;
			}
		}
		return count;
	}

	private static boolean copyFile(File src, File dst) {
		try (InputStream in = new FileInputStream(src);
			 OutputStream out = new FileOutputStream(dst)) {
			byte[] buf = new byte[8192];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean deleteContents(File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			return true;
		}
		boolean ok = true;
		for (File f : files) {
			ok &= deleteTree(f);
		}
		return ok;
	}

	private static boolean deleteTree(File file) {
		if (!file.exists()) {
			return true;
		}
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File f : files) {
					deleteTree(f);
				}
			}
		}
		return file.delete();
	}
}
