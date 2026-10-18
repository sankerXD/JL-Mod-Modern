/*
 * Copyright 2024 JL-Mod contributors
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

import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Properties;

import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.Config;

/**
 * Installs the shaders that ship inside the APK ({@code assets/bundled_shaders}) into
 * {@code <emulator dir>/shaders/}, so a fresh install already has them listed in
 * "设置 → 着色器（滤镜）" without the user copying any file.
 * <p>
 * Rules:
 * <ul>
 * <li>a missing file is always written;</li>
 * <li>an existing file is refreshed on app update <b>only</b> if it is still byte-identical
 * to the version we shipped last time (i.e. the user did not edit it);</li>
 * <li>a file the user edited is never touched.</li>
 * </ul>
 * The bookkeeping lives in {@code shaders/.bundled_shaders} (not *.ini, so the shader
 * picker never lists it).
 */
public final class BundledShaders {
	private static final String TAG = "BundledShaders";
	private static final String ASSET_DIR = "bundled_shaders";
	private static final String MARKER_NAME = ".bundled_shaders";
	private static final String KEY_VERSION = "version";
	private static final String[] FILES = {
			"hd.ini", "hd.fsh", "slotmask.ini", "slotmask.fsh", "slotmask.vsh"
	};

	private BundledShaders() {
	}

	/**
	 * @param workDir emulator root ("/sdcard/JL-Mod"); the shaders go to its {@code shaders/} subdir
	 */
	public static void install(File workDir) {
		if (workDir == null) {
			return;
		}
		try {
			doInstall(workDir);
		} catch (Throwable t) {
			// never let a cosmetic feature break startup
			Log.e(TAG, "install failed", t);
		}
	}

	private static void doInstall(File workDir) throws Exception {
		AssetManager assets = ContextHolder.getAppContext().getAssets();
		File dir = new File(workDir, Config.SHADERS_DIR);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			Log.e(TAG, "cannot create " + dir);
			return;
		}
		File marker = new File(dir, MARKER_NAME);
		Properties prev = load(marker);
		String version = String.valueOf(BuildConfig.VERSION_CODE);
		boolean sameVersion = version.equals(prev.getProperty(KEY_VERSION));
		if (sameVersion && allPresent(dir)) {
			return;
		}

		Properties now = new Properties();
		now.setProperty(KEY_VERSION, version);
		boolean dirty = false;
		for (String name : FILES) {
			String prevMd5 = prev.getProperty(name);
			byte[] data = readAsset(assets, name);
			if (data == null) {
				if (prevMd5 != null) {
					now.setProperty(name, prevMd5);
				}
				continue;
			}
			String newMd5 = md5(data);
			now.setProperty(name, newMd5);
			File dest = new File(dir, name);
			boolean write;
			if (!dest.isFile()) {
				write = true;
			} else if (newMd5.equals(md5(dest))) {
				write = false;
			} else {
				// only refresh files we shipped ourselves and the user did not touch
				write = prevMd5 != null && prevMd5.equals(md5(dest));
			}
			if (write) {
				writeFile(dest, data);
				dirty = true;
				Log.e(TAG, "installed " + name + " -> " + dest);
			}
		}
		if (dirty || !sameVersion) {
			save(marker, now);
		}
	}

	private static boolean allPresent(File dir) {
		for (String name : FILES) {
			if (!new File(dir, name).isFile()) {
				return false;
			}
		}
		return true;
	}

	private static byte[] readAsset(AssetManager assets, String name) {
		try (InputStream is = assets.open(ASSET_DIR + "/" + name)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
			byte[] buf = new byte[4096];
			int len;
			while ((len = is.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			return out.toByteArray();
		} catch (IOException e) {
			Log.e(TAG, "asset missing: " + name, e);
			return null;
		}
	}

	private static void writeFile(File dest, byte[] data) throws IOException {
		try (OutputStream os = new FileOutputStream(dest)) {
			os.write(data);
		}
	}

	private static String md5(File file) {
		try (InputStream is = new FileInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			byte[] buf = new byte[4096];
			int len;
			while ((len = is.read(buf)) > 0) {
				digest.update(buf, 0, len);
			}
			return hex(digest.digest());
		} catch (Exception e) {
			Log.e(TAG, "md5: " + file, e);
			return "";
		}
	}

	private static String md5(byte[] data) throws Exception {
		return hex(MessageDigest.getInstance("MD5").digest(data));
	}

	private static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16))
					.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}

	private static Properties load(File file) {
		Properties properties = new Properties();
		if (!file.isFile()) {
			return properties;
		}
		try (InputStream is = new FileInputStream(file)) {
			properties.load(is);
		} catch (Exception e) {
			Log.e(TAG, "load marker", e);
		}
		return properties;
	}

	private static void save(File file, Properties properties) {
		try (OutputStream os = new FileOutputStream(file)) {
			properties.store(os, "shaders bundled in the APK");
		} catch (Exception e) {
			Log.e(TAG, "save marker", e);
		}
	}
}
