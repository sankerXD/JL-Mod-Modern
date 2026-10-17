/*
 * Copyright 2018 Nikita Shakarun
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

package ru.playsoftware.j2meloader.filepicker;

import androidx.annotation.Nullable;

import com.nononsenseapps.filepicker.AbstractFilePickerActivity;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;

import java.io.File;
import java.util.Arrays;

public class FilteredFilePickerActivity extends AbstractFilePickerActivity<File> {
	public static final String EXTRA_EXTENSIONS = "ru.playsoftware.j2meloader.EXTRA_EXTENSIONS";

	@Override
	protected AbstractFilePickerFragment<File> getFragment(@Nullable String startPath, int mode, boolean allowMultiple,
														   boolean allowCreateDir, boolean allowExistingFile, boolean singleClick) {
		FilteredFilePickerFragment fragment = new FilteredFilePickerFragment();
		fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
		String[] extensions = getIntent().getStringArrayExtra(EXTRA_EXTENSIONS);
		if (extensions != null) {
			fragment.setExtensions(Arrays.asList(extensions));
		}
		return fragment;
	}
}
