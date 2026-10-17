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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseIntArray;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The global gamepad key mapping (Settings -> Global key mapping).
 * <p>
 * Priority when a game starts: built-in defaults &lt; this global map &lt;
 * the per-game map stored in config.json. So a game only overrides the global
 * mapping when it has its own "KeyMappings" entry.
 */
public final class GlobalKeyMap {
	public static final String PREF_KEYMAP = "pref_keymap";

	private GlobalKeyMap() {
	}

	private static Gson gson() {
		return new GsonBuilder()
				.registerTypeAdapter(SparseIntArray.class, new SparseIntArrayAdapter())
				.create();
	}

	/**
	 * @return the global mapping, or {@code null} when nothing was set
	 */
	public static SparseIntArray load(Context context) {
		if (context == null) {
			return null;
		}
		String json = PreferenceManager.getDefaultSharedPreferences(context)
				.getString(PREF_KEYMAP, null);
		if (json == null || json.isEmpty()) {
			return null;
		}
		try {
			return gson().fromJson(json, SparseIntArray.class);
		} catch (Exception e) {
			return null;
		}
	}

	public static void save(Context context, SparseIntArray map) {
		SharedPreferences.Editor editor = PreferenceManager
				.getDefaultSharedPreferences(context).edit();
		if (map == null || map.size() == 0) {
			editor.remove(PREF_KEYMAP).apply();
			return;
		}
		try {
			editor.putString(PREF_KEYMAP, gson().toJson(map)).apply();
		} catch (Exception ignored) {
		}
	}
}
