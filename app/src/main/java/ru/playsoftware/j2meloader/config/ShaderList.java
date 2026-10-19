/*
 * Copyright 2024 JL-Modern contributors
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
package ru.playsoftware.j2meloader.config;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * Reads shader descriptors ({@code *.ini}).
 * <p>
 * Format (the same one the config screen always used):
 * <pre>
 * [Shader name]
 * Name=Shader name
 * Author=someone
 * Fragment=shader.fsh
 * Vertex=shader.vsh
 * SettingName1=...
 * SettingDefaultValue1=...
 * </pre>
 * The fragment/vertex paths are relative to the ini file.
 */
public final class ShaderList {
	private static final String TAG = "ShaderList";

	private ShaderList() {
	}

	/**
	 * Loads every shader descriptor found in {@code dir}.
	 */
	public static List<ShaderInfo> load(File dir) {
		ArrayList<ShaderInfo> shaders = new ArrayList<>();
		String[] files = dir.list();
		if (files == null) {
			return shaders;
		}
		for (String fileName : files) {
			if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ini")) {
				continue;
			}
			shaders.addAll(parse(new File(dir, fileName)));
		}
		Collections.sort(shaders);
		return shaders;
	}

	public static ShaderInfo find(File dir, String name) {
		if (name == null) {
			return null;
		}
		for (ShaderInfo info : load(dir)) {
			if (name.equals(info.getName())) {
				return info;
			}
		}
		return null;
	}

	/**
	 * Parses a single descriptor file, returns the first usable section.
	 */
	public static ShaderInfo loadIni(File file) {
		List<ShaderInfo> shaders = parse(file);
		return shaders.isEmpty() ? null : shaders.get(0);
	}

	/**
	 * Checks that the descriptor and the files it references are usable.
	 *
	 * @return {@code null} when everything looks fine, otherwise the reason
	 */
	public static String validate(File iniFile) {
		if (iniFile == null) {
			return "no file selected";
		}
		if (!iniFile.isFile() || !iniFile.canRead()) {
			return "cannot read " + iniFile.getAbsolutePath();
		}
		List<ShaderInfo> shaders = parse(iniFile);
		if (shaders.isEmpty()) {
			return "no valid [section] with Fragment= and Vertex= in " + iniFile.getName();
		}
		ShaderInfo info = shaders.get(0);
		File dir = iniFile.getParentFile();
		String fragment = dir == null ? null
				: FileUtils.getText(new File(dir, info.fragment).getAbsolutePath());
		String vertex = dir == null ? null
				: FileUtils.getText(new File(dir, info.vertex).getAbsolutePath());
		if (fragment == null || fragment.trim().isEmpty()) {
			return "fragment shader not found or empty: " + info.fragment;
		}
		if (vertex == null || vertex.trim().isEmpty()) {
			return "vertex shader not found or empty: " + info.vertex;
		}
		if (!fragment.contains("main")) {
			return "fragment shader has no main(): " + info.fragment;
		}
		if (!vertex.contains("main")) {
			return "vertex shader has no main(): " + info.vertex;
		}
		return null;
	}

	private static List<ShaderInfo> parse(File file) {
		ArrayList<ShaderInfo> shaders = new ArrayList<>();
		String text = FileUtils.getText(file.getAbsolutePath());
		if (text == null) {
			return shaders;
		}
		String dir = file.getParent();
		ShaderInfo info = null;
		for (String line : text.split("[\\n\\r]+")) {
			if (line.startsWith("[")) {
				if (info != null && info.fragment != null && info.vertex != null) {
					shaders.add(finish(info, dir));
				}
				info = new ShaderInfo(line.replaceAll("[\\[\\]]", ""), "unknown");
			} else if (info != null) {
				try {
					info.set(line);
				} catch (Exception e) {
					Log.e(TAG, "parse: " + line, e);
				}
			}
		}
		if (info != null && info.fragment != null && info.vertex != null) {
			shaders.add(finish(info, dir));
		}
		return shaders;
	}

	private static ShaderInfo finish(ShaderInfo info, String dir) {
		if (dir != null) {
			info.dir = dir.endsWith("/") ? dir : dir + "/";
		}
		// the engine only sends u_setting when values != null, so always fill them
		float[] values = new float[4];
		ShaderInfo.Setting[] settings = info.settings;
		if (settings != null) {
			for (int i = 0; i < values.length && i < settings.length; i++) {
				if (settings[i] != null) {
					values[i] = settings[i].def;
				}
			}
		}
		info.values = values;
		return info;
	}
}
