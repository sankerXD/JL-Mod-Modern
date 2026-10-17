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
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nononsenseapps.filepicker.FilePickerActivity;

import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerActivity;

/**
 * Lets the user pick a single file with the given extensions.
 * The input is the start directory, the result is the picked file (or null).
 */
public class PickFileResultContract extends ActivityResultContract<String[], Uri> {
	private String startPath;

	@NonNull
	@Override
	public Intent createIntent(@NonNull Context context, String[] extensions) {
		Intent intent = new Intent(context, FilteredFilePickerActivity.class);
		intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
		intent.putExtra(FilePickerActivity.EXTRA_SINGLE_CLICK, true);
		intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
		intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
		intent.putExtra(FilePickerActivity.EXTRA_START_PATH, startPath);
		intent.putExtra(FilteredFilePickerActivity.EXTRA_EXTENSIONS, extensions);
		return intent;
	}

	public void setStartPath(@Nullable String startPath) {
		this.startPath = startPath;
	}

	@Nullable
	@Override
	public Uri parseResult(int resultCode, @Nullable Intent intent) {
		if (intent == null) {
			return null;
		}
		Uri uri = intent.getData();
		if (uri == null) {
			return null;
		}
		return uri;
	}
}
