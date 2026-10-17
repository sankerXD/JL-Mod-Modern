/*
 * Copyright 2017-2019 Nikita Shakarun
 * Copyright 2019-2024 Yury Kharchenko
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

package ru.playsoftware.j2meloader.settings;

import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.widget.Toast;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

import ru.playsoftware.j2meloader.EmulatorApplication;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.config.ShaderInfo;
import ru.playsoftware.j2meloader.config.ShaderList;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.PickDirResultContract;
import ru.playsoftware.j2meloader.util.PickFileResultContract;
import ru.playsoftware.j2meloader.util.XmlUtils;

public class SettingsFragment extends PreferenceFragmentCompat {
	private static final String TAG = "SettingsFragment";

	private Preference prefFolder;
	private Preference prefShader;
	private final PickFileResultContract shaderPickerContract = new PickFileResultContract();
	private final ActivityResultLauncher<String[]> shaderPicker =
			registerForActivityResult(shaderPickerContract, this::onShaderPicked);
	private final ActivityResultLauncher<String> openDirLauncher = registerForActivityResult(
			new PickDirResultContract(),
			this::onPickDirResult);

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		Objects.<Preference>requireNonNull(findPreference("pref_default_settings"))
				.setIntent(new Intent(requireActivity(), ProfilesActivity.class));
		initLanguages();
		initShaders();
		initGlobalKeyMap();
		prefFolder = Objects.requireNonNull(findPreference(PREF_EMULATOR_DIR));
		prefFolder.setSummary(Config.getEmulatorDir());
		prefFolder.setOnPreferenceClickListener(preference -> {
			openDirLauncher.launch(null);
			return true;
		});
	}

	private void initGlobalKeyMap() {
		Preference pref = findPreference("pref_global_keymap");
		if (pref == null) {
			return;
		}
		pref.setOnPreferenceClickListener(p -> {
			Intent intent = new Intent(requireActivity(), KeyMapperActivity.class);
			intent.setData(Uri.parse("global"));
			startActivity(intent);
			return true;
		});
	}

	private void initShaders() {
		prefShader = findPreference(Constants.PREF_SHADER);
		if (prefShader == null) {
			return;
		}
		updateShaderSummary();
		prefShader.setOnPreferenceClickListener(preference -> {
			showShaderDialog();
			return true;
		});
	}

	/**
	 * @return the shader stored in preferences, or null when nothing usable is loaded
	 */
	private ShaderInfo loadedShader() {
		String path = getShaderPath();
		if (path == null || path.isEmpty()) {
			return null;
		}
		File file = new File(path);
		if (!file.isFile()) {
			// stale entry (e.g. old format) - forget it
			saveShaderPath("");
			return null;
		}
		return ShaderList.loadIni(file);
	}

	private void updateShaderSummary() {
		ShaderInfo info = loadedShader();
		if (info == null) {
			prefShader.setSummary(R.string.pref_shader_off);
			return;
		}
		String name = info.getName() == null ? "shader" : info.getName();
		prefShader.setSummary(getString(R.string.pref_shader_global, name));
	}

	private String getShaderPath() {
		return PreferenceManager.getDefaultSharedPreferences(requireContext())
				.getString(Constants.PREF_SHADER, null);
	}

	private void showShaderDialog() {
		ShaderInfo loaded = loadedShader();
		String[] items = new String[]{
				getString(R.string.pref_shader_off),
				loaded == null ? getString(R.string.pref_shader_load)
						: getString(R.string.pref_shader_global,
						loaded.getName() == null ? "shader" : loaded.getName())
		};
		new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.pref_shader_title)
				.setItems(items, (dialog, which) -> {
					if (which == 0) {
						saveShaderPath("");
					} else {
						pickShaderFile();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void pickShaderFile() {
		shaderPickerContract.setStartPath(Config.getEmulatorDir() + Config.SHADERS_DIR);
		shaderPicker.launch(new String[]{".ini"});
	}

	private void onShaderPicked(Uri uri) {
		if (uri == null || uri.getPath() == null) {
			return;
		}
		File file = new File(uri.getPath());
		String error = ShaderList.validate(file);
		if (error != null) {
			new AlertDialog.Builder(requireActivity())
					.setTitle(R.string.shader_load_failed)
					.setMessage(error)
					.setPositiveButton(android.R.string.ok, null)
					.show();
			return;
		}
		saveShaderPath(file.getAbsolutePath());
		ShaderInfo info = ShaderList.loadIni(file);
		Toast.makeText(requireActivity(),
				getString(R.string.shader_loaded, info == null ? file.getName() : info.getName()),
				Toast.LENGTH_SHORT).show();
	}

	private void saveShaderPath(String path) {
		PreferenceManager.getDefaultSharedPreferences(requireContext())
				.edit().putString(Constants.PREF_SHADER, path).apply();
		updateShaderSummary();
	}

	private void initLanguages() {
		ListPreference prefLanguage = Objects.requireNonNull(findPreference("pref_language"));
		StringBuilder sb = new StringBuilder();
		Context context = EmulatorApplication.getInstance();
		Resources resources = context.getResources();
		@SuppressLint("DiscouragedApi")
		int id = resources.getIdentifier("_generated_res_locale_config", "xml", context.getPackageName());
		try (XmlResourceParser parser = resources.getXml(id)) {
			while (XmlUtils.nextElement(parser, "locale")) {
				sb.append(',').append(parser.getAttributeValue(0));
			}
		} catch (Exception e) {
			Log.e(TAG, "loadLanguagesList: ", e);
		}
		String[] languageTags = sb.toString().split(",");
		LocaleListCompat locales = LocaleListCompat.forLanguageTags(sb.toString());
		int size = languageTags.length;
		String[] languageNames = new String[size];
		languageNames[0] = context.getString(R.string.pref_theme_system);
		for (int i = 1; i < size; i++) {
			Locale l = locales.get(i);
			if (l != null) {
				languageNames[i] = l.getDisplayName(l);
			}
		}
		prefLanguage.setEntryValues(languageTags);
		prefLanguage.setEntries(languageNames);
		Locale locale = AppCompatDelegate.getApplicationLocales().get(0);
		prefLanguage.setValue(locale != null ? locale.getLanguage() : "");
		prefLanguage.setOnPreferenceChangeListener((preference, value) -> {
			LocaleListCompat list = LocaleListCompat.forLanguageTags((String) value);
			AppCompatDelegate.setApplicationLocales(list);
			return true;
		});
	}

	private void onPickDirResult(Uri uri) {
		if (uri == null || uri.getPath() == null) {
			return;
		}
		File file = new File(uri.getPath());
		String path = file.getAbsolutePath();
		if (!FileUtils.initWorkDir(file)) {
			new AlertDialog.Builder(requireActivity())
					.setTitle(R.string.error)
					.setCancelable(false)
					.setMessage(getString(R.string.create_apps_dir_failed, path))
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(R.string.choose, (d, w) -> openDirLauncher.launch(null))
					.show();
			return;
		}
		Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit()
				.putString(PREF_EMULATOR_DIR, path)
				.apply();
		prefFolder.setSummary(path);
	}
}
