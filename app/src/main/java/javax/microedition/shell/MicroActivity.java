/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2021 Nikita Shakarun
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

package javax.microedition.shell;

import static android.content.pm.ActivityInfo.*;
import static ru.playsoftware.j2meloader.util.Constants.*;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.PopupMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.CanvasEvent;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.KeyMapper;
import javax.microedition.lcdui.keyboard.TriggerAxisHandler;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.lcdui.skin.SkinLayer;
import javax.microedition.util.ContextHolder;

import org.snapvm.adapter.SnapVm;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.config.ShaderInfo;
import ru.playsoftware.j2meloader.config.ShaderList;
import ru.playsoftware.j2meloader.config.model.Size;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import ru.playsoftware.j2meloader.databinding.DialogInputBinding;
import ru.playsoftware.j2meloader.settings.KeyMapperActivity;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.playsoftware.j2meloader.util.PickFileResultContract;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;

	private Displayable current;
	private boolean visible;
	private boolean actionBarEnabled;
	private DrawerLayout drawerLayout;
	private LinearLayout drawerItems;
	private boolean statusBarEnabled;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private int menuKey;
	private String appPath;
	private ActivityMicroBinding binding;
	private BroadcastReceiver keyInjector;
	private Menu drawerMenu;
	private int drawerMode;
	private int edgePullZone;
	private int touchSlop;
	private float edgeDownX;
	private float edgeDownY;
	private boolean edgePullArmed;
	private boolean frozenForKeyMap;
	private final PickFileResultContract shaderPickerContract = new PickFileResultContract();
	private final ActivityResultLauncher<String[]> shaderPicker =
			registerForActivityResult(shaderPickerContract, this::onShaderPicked);

	private static final int MODE_MAIN = 0;
	private static final int MODE_SNAP_SAVE = 1;
	private static final int MODE_SNAP_LOAD = 2;
	private static final int MODE_SNAP_EDIT = 3;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		lockNightMode();
		super.onCreate(savedInstanceState);
		ContextHolder.setCurrentActivity(this);
		binding = ActivityMicroBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		ViewConfiguration vc = ViewConfiguration.get(this);
		edgePullZone = vc.getScaledEdgeSlop();
		touchSlop = vc.getScaledTouchSlop();
		setSupportActionBar(binding.toolbar);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		actionBarEnabled = sp.getBoolean(PREF_TOOLBAR, false);
		statusBarEnabled = sp.getBoolean(PREF_STATUSBAR, false);
		if (sp.getBoolean(PREF_KEEP_SCREEN, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		if (!statusBarEnabled) {
			// window level flag: unlike SYSTEM_UI_FLAG_* it survives drawer gestures
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		ContextHolder.setVibration(sp.getBoolean(PREF_VIBRATION, true));
		Canvas.setScreenshotRawMode(sp.getBoolean(PREF_SCREENSHOT_SWITCH, false));
		Intent intent = getIntent();
		if (BuildConfig.FULL_EMULATOR) {
			appName = intent.getStringExtra(KEY_MIDLET_NAME);
			Uri data = intent.getData();
			if (data == null) {
				showErrorDialog("Invalid intent: app path is null");
				return;
			}
			appPath = data.toString();
		} else {
			appName = getTitle().toString();
			appPath = getApplicationInfo().dataDir + "/files/converted/midlet";
			File dir = new File(appPath);
			if (!dir.exists() && !dir.mkdirs()) {
				throw new RuntimeException("Can't access file system");
			}
		}
		microLoader = new MicroLoader(this, appPath);
		if (!microLoader.init()) {
			Config.startApp(this, appName, appPath, true);
			finish();
			return;
		}
		microLoader.applyConfiguration();
		SkinLayer skinLayer = SkinLayer.getInstance();
		if (skinLayer != null) {
			binding.overlay.addLayer(skinLayer);
			if (!statusBarEnabled && !actionBarEnabled) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					WindowManager.LayoutParams attributes = getWindow().getAttributes();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
						attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
					} else {
						attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
					}
					getWindow().setAttributes(attributes);
				}
			}
		}
		VirtualKeyboard vk = ContextHolder.getVk();
		int orientation = microLoader.getOrientation();
		if (vk != null) {
			vk.setView(binding.overlay);
			binding.overlay.addLayer(vk);
			flashHideAllButton();
			if (vk.isPhone()) {
				orientation = ORIENTATION_PORTRAIT;
			}
		}
		binding.vkEditDone.setOnClickListener(v -> endVkLayoutEdit(true));
		binding.vkEditSwap.setOnClickListener(v -> {
			VirtualKeyboard vkSwap = ContextHolder.getVk();
			if (vkSwap != null) {
				vkSwap.swapSides();
			}
		});
		binding.vkEditDiscard.setOnClickListener(v -> endVkLayoutEdit(false));
		binding.vkHideAll.setOnClickListener(v -> toggleHideAllKeys());
		setOrientation(orientation);
		menuKey = microLoader.getMenuKeyCode();
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		setupMenuDrawer();
		// adb-driven key injection for testing: broadcast delivers a J2ME
		// keycode straight into the lcdui event queue so down/up timing is
		// fully controllable (am broadcast -a ...KEYINJECT --ei key -7).
		keyInjector = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent i) {
				// snapshot test hook: --es op save|load --ei slot N runs the
				// same slot path the drawer uses (am broadcast -a ...KEYINJECT
				// --es op save --ei slot 0).
				String op = i.getStringExtra("op");
				if (op != null) {
					int slot = i.getIntExtra("slot", 0);
					runOnUiThread(() -> {
						if ("save".equals(op)) doSlotSave(slot);
						else if ("load".equals(op)) doSlotLoad(slot);
					});
					return;
				}
				Displayable d = getCurrent();
				Log.i("SnapVm", "keyinject key=" + i.getIntExtra("key", 0)
						+ " current=" + d);
				if (!(d instanceof Canvas)) {
					return;
				}
				int key = i.getIntExtra("key", 0);
				boolean up = i.getBooleanExtra("up", false);
				javax.microedition.lcdui.Display.postEvent(
						javax.microedition.lcdui.event.CanvasEvent.getInstance(
								(Canvas) d,
								up ? javax.microedition.lcdui.event.CanvasEvent.KEY_RELEASED
										: javax.microedition.lcdui.event.CanvasEvent.KEY_PRESSED,
								key));
			}
		};
		if (BuildConfig.DEBUG) {
			registerReceiver(keyInjector,
					new IntentFilter(getPackageName() + ".KEYINJECT"),
					ContextCompat.RECEIVER_EXPORTED);
		} else {
			keyInjector = null;
		}

		getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				// Intentionally overridden by empty due to support for back-key remapping.
			}
		});
		loadMIDlet();
	}

	public void lockNightMode() {
		int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (current == Configuration.UI_MODE_NIGHT_YES) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (frozenForKeyMap) {
			frozenForKeyMap = false;
			applyKeyMappingsFromDisk();
			setGameFrozen(false);
		}
		visible = true;
		MidletThread.resumeApp();
	}

	@Override
	public void onPause() {
		visible = false;
		triggerAxes.release(this);
		hideSoftInput();
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null && vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF) {
			endVkLayoutEdit(false);
		}
		MidletThread.pauseApp();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (keyInjector != null) {
			unregisterReceiver(keyInjector);
			keyInjector = null;
		}
		super.onDestroy();
	}

	private void hideSoftInput() {
		if (inputMethodManager != null) {
			IBinder windowToken = binding.displayableContainer.getWindowToken();
			inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
				current instanceof Canvas) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		setRequestedOrientation(switch (orientation) {
			case ORIENTATION_DEFAULT -> SCREEN_ORIENTATION_UNSPECIFIED;
			case ORIENTATION_AUTO -> SCREEN_ORIENTATION_FULL_SENSOR;
			case ORIENTATION_PORTRAIT -> SCREEN_ORIENTATION_SENSOR_PORTRAIT;
			case ORIENTATION_LANDSCAPE -> SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
			default -> SCREEN_ORIENTATION_UNSPECIFIED;
		});
	}

	private void loadMIDlet() {
		LinkedHashMap<String, String> midlets;
		try {
			midlets = microLoader.loadMIDletList();
		} catch (IOException e) {
			showErrorDialog(e.toString());
			return;
		}
		int size = midlets.size();
		String[] midletsNameArray = midlets.values().toArray(new String[0]);
		String[] midletsClassArray = midlets.keySet().toArray(new String[0]);
		if (size == 0) {
			showErrorDialog("No MIDlets found");
		} else if (size == 1) {
			MidletThread.create(microLoader, midletsClassArray[0]);
			microLoader.pushToRecentApps(appName);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] names, final String[] classes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(names, (d, n) -> {
					String clazz = classes[n];
					ErrorReporter errorReporter = ACRA.getErrorReporter();
					String report = errorReporter.getCustomData(Constants.KEY_APPCENTER_ATTACHMENT);
					StringBuilder sb = new StringBuilder();
					if (report != null) {
						sb.append(report).append("\n");
					}
					sb.append("Begin app: ").append(names[n]).append(", ").append(clazz);
					errorReporter.putCustomData(Constants.KEY_APPCENTER_ATTACHMENT, sb.toString());
					MidletThread.create(microLoader, clazz);
					MidletThread.resumeApp();
					microLoader.pushToRecentApps(appName);
				})
				.setOnCancelListener(d -> {
					d.dismiss();
					MidletThread.notifyDestroyed();
				});
		builder.show();
	}

	void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> MidletThread.notifyDestroyed());
		builder.setOnCancelListener(dialogInterface -> MidletThread.notifyDestroyed());
		builder.show();
	}

	private float getToolBarHeight() {
		TypedValue typedValue = new TypedValue();
		if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typedValue, true)) {
			return typedValue.getDimension(getResources().getDisplayMetrics());
		}
		return 0;
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			if (!statusBarEnabled) {
				flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
			}
			getWindow().getDecorView().setSystemUiVisibility(flags);
		} else if (!statusBarEnabled) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	private void showSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public void setCurrent(Displayable displayable) {
		ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
		current = displayable;
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return visible;
	}

	public void showExitConfirmation() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setTitle(R.string.exit_confirm)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					hideSoftInput();
					MidletThread.destroyApp();
				})
				.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.create().show();
	}

	private final TriggerAxisHandler triggerAxes = new TriggerAxisHandler();

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		if (triggerAxes.handle(this, event)) {
			return true;
		}
		return super.dispatchGenericMotionEvent(event);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		// any touch on the game window wakes the hide-all toggle for a bit
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			flashHideAllButton();
		}
		// while key-layout editing the drawer is locked; an edge pull that
		// isn't grabbing a key hints the layout hasn't been saved yet
		VirtualKeyboard vkEdit = ContextHolder.getVk();
		boolean editing =
				vkEdit != null && vkEdit.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF;
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				edgePullArmed = editing && event.getX() < edgePullZone;
				edgeDownX = event.getX();
				edgeDownY = event.getY();
			}
			case MotionEvent.ACTION_MOVE -> {
				if (edgePullArmed) {
					float dx = event.getX() - edgeDownX;
					float dy = event.getY() - edgeDownY;
					if (dx > touchSlop && dx > Math.abs(dy) * 2
							&& (vkEdit == null || !vkEdit.isKeyGrabbed())) {
						edgePullArmed = false;
						Toast.makeText(this, R.string.vk_layout_unsaved,
								Toast.LENGTH_SHORT).show();
					}
				}
			}
			case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> edgePullArmed = false;
		}
		return super.dispatchTouchEvent(event);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU)
			if (current instanceof Canvas && binding.displayableContainer.dispatchKeyEvent(event)) {
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.getRepeatCount() == 0) {
					event.startTracking();
					return true;
				} else if (event.isLongPress()) {
					return onKeyLongPress(event.getKeyCode(), event);
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				return onKeyUp(event.getKeyCode(), event);
			}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void openOptionsMenu() {
		VirtualKeyboard vkEdit = ContextHolder.getVk();
		if (vkEdit != null && vkEdit.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF) {
			Toast.makeText(this, R.string.vk_layout_unsaved, Toast.LENGTH_SHORT).show();
			return;
		}
		if (drawerLayout != null) {
			drawerLayout.openDrawer(binding.menuDrawer);
			return;
		}
		super.openOptionsMenu();
	}

	/**
	 * Builds the left-edge drawer from the same menu resource as the old
	 * action bar, so the entries behave (and toggle) exactly as before.
	 */
	private void setupMenuDrawer() {
		drawerLayout = binding.drawerLayout;
		drawerItems = binding.menuDrawerItems;
		drawerLayout.setScrimColor(0x99000000);
		drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
			@Override
			public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
				if (current instanceof Canvas) {
					hideSystemUI();
				}
			}

			@Override
			public void onDrawerOpened(@NonNull View drawerView) {
				if (current instanceof Canvas) {
					hideSystemUI();
				}
			}

			@Override
			public void onDrawerClosed(@NonNull View drawerView) {
				if (drawerMode != MODE_MAIN) {
					showMainDrawer();
				}
			}
		});
		PopupMenu popup = new PopupMenu(this, binding.displayableContainer);
		Menu menu = popup.getMenu();
		onCreateOptionsMenu(menu);
		drawerMenu = menu;
		fillDrawerItems(menu);
	}

	private void showMainDrawer() {
		drawerMode = MODE_MAIN;
		String title = current != null ? current.getTitle() : null;
		binding.menuDrawerTitle.setText(title == null ? appName : title);
		fillDrawerItems(drawerMenu);
	}

	private void fillDrawerItems(Menu menu) {
		drawerItems.removeAllViews();
		for (int i = 0, size = menu.size(); i < size; i++) {
			MenuItem item = menu.getItem(i);
			if (!item.isVisible()) {
				continue;
			}
			if (item.hasSubMenu()) {
				addDrawerHeader(item.getTitle());
				SubMenu subMenu = item.getSubMenu();
				if (subMenu != null) {
					for (int j = 0, count = subMenu.size(); j < count; j++) {
						MenuItem subItem = subMenu.getItem(j);
						if (subItem.isVisible()) {
							addDrawerItem(subItem, true);
						}
					}
				}
			} else {
				addDrawerItem(item, false);
			}
		}
	}

	private void addDrawerHeader(CharSequence title) {
		TextView header = new TextView(this);
		header.setText(title);
		header.setTextAppearance(this, android.R.style.TextAppearance_Small);
		int pad = (int) (getResources().getDisplayMetrics().density * 12);
		header.setPadding(pad * 2, pad * 2, pad, pad / 2);
		drawerItems.addView(header);
	}

	private void addDrawerItem(MenuItem item, boolean indented) {
		TextView row = new TextView(this);
		row.setText(item.getTitle());
		row.setTextAppearance(this, android.R.style.TextAppearance_Medium);
		row.setBackgroundResource(android.R.drawable.list_selector_background);
		int pad = (int) (getResources().getDisplayMetrics().density * 12);
		row.setPadding(indented ? pad * 2 : pad * 2, pad, pad, pad);
		row.setClickable(true);
		row.setFocusable(true);
		row.setOnClickListener(v -> {
			int id = item.getItemId();
			if (id == R.id.action_snap_save || id == R.id.action_snap_load
					|| id == R.id.action_snap_edit) {
				// slot pages replace the drawer contents — keep it open
				onOptionsItemSelected(item);
				return;
			}
			drawerLayout.closeDrawers();
			if (item.isCheckable()) {
				item.setChecked(!item.isChecked());
			}
			onOptionsItemSelected(item);
		});
		drawerItems.addView(row);
	}

	private TextView addDrawerRow(CharSequence text, boolean enabled, Runnable action) {
		TextView row = new TextView(this);
		row.setText(text);
		row.setTextAppearance(this, android.R.style.TextAppearance_Medium);
		row.setBackgroundResource(android.R.drawable.list_selector_background);
		int pad = (int) (getResources().getDisplayMetrics().density * 12);
		row.setPadding(pad * 2, pad, pad, pad);
		row.setClickable(enabled);
		row.setFocusable(enabled);
		row.setEnabled(enabled);
		if (!enabled) {
			row.setAlpha(0.38f);
		}
		if (action != null) {
			row.setOnClickListener(v -> action.run());
		}
		drawerItems.addView(row);
		return row;
	}

	private void showSlotDrawer(int mode) {
		drawerMode = mode;
		int titleRes = mode == MODE_SNAP_SAVE ? R.string.snap_save
				: mode == MODE_SNAP_LOAD ? R.string.snap_load : R.string.snap_edit;
		binding.menuDrawerTitle.setText(titleRes);
		drawerItems.removeAllViews();
		addDrawerRow(getString(R.string.snap_back), true, this::showMainDrawer);
		SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
		for (SnapVm.SlotInfo si : SnapVm.listSlots()) {
			String text = si.exists
					? (si.name != null ? si.name
							: getString(R.string.snap_auto_name, si.slot + 1,
									fmt.format(new Date(si.time))))
					: getString(R.string.snap_slot_empty);
			boolean enabled = si.exists || mode == MODE_SNAP_SAVE;
			final SnapVm.SlotInfo slot = si;
			addDrawerRow(text, enabled, () -> onSlotClicked(mode, slot));
		}
	}

	private void onSlotClicked(int mode, SnapVm.SlotInfo si) {
		switch (mode) {
		case MODE_SNAP_SAVE:
			if (si.exists) {
				new AlertDialog.Builder(this)
						.setMessage(R.string.snap_overwrite)
						.setPositiveButton(android.R.string.yes,
								(d, w) -> doSlotSave(si.slot))
						.setNegativeButton(android.R.string.no, null)
						.show();
			} else {
				doSlotSave(si.slot);
			}
			break;
		case MODE_SNAP_LOAD:
			if (si.exists) {
				doSlotLoad(si.slot);
			}
			break;
		case MODE_SNAP_EDIT:
			if (si.exists) {
				View anchor = drawerItems.getChildAt(si.slot + 1); // +1 = back row
				PopupMenu pm = new PopupMenu(this,
						anchor == null ? drawerItems : anchor);
				pm.getMenu().add(0, 1, 0, R.string.snap_rename);
				pm.getMenu().add(0, 2, 0, R.string.snap_delete);
				pm.setOnMenuItemClickListener(mi -> {
					if (mi.getItemId() == 1) {
						showSlotRenameDialog(si);
					} else {
						confirmSlotDelete(si);
					}
					return true;
				});
				pm.show();
			}
			break;
		}
	}

	private void doSlotSave(int slot) {
		try {
			int n = SnapVm.nextSaveIndex();
			String stamp = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
					.format(new Date());
			SnapVm.saveGame(slot, getString(R.string.snap_auto_name, n, stamp));
			Toast.makeText(this, R.string.snap_saved, Toast.LENGTH_SHORT).show();
		} catch (Throwable t) {
			Log.e("SnapVm", "save failed", t);
			Toast.makeText(this, getString(R.string.snap_failed) + ": " + t,
					Toast.LENGTH_LONG).show();
		}
		showSlotDrawer(drawerMode); // refresh: new name + any freed overwrite state
	}

	private void doSlotLoad(int slot) {
		try {
			SnapVm.loadGame(slot);
			Toast.makeText(this, R.string.snap_loaded, Toast.LENGTH_SHORT).show();
		} catch (Throwable t) {
			Log.e("SnapVm", "load failed", t);
			Toast.makeText(this, getString(R.string.snap_failed) + ": " + t,
					Toast.LENGTH_LONG).show();
		}
		drawerLayout.closeDrawers();
	}

	private void showSlotRenameDialog(SnapVm.SlotInfo si) {
		TextInputLayout layout = DialogInputBinding.inflate(getLayoutInflater()).getRoot();
		EditText et = layout.getEditText();
		if (et != null) {
			if (si.name != null) {
				et.setText(si.name);
				et.setSelection(si.name.length());
			}
		}
		new AlertDialog.Builder(this)
				.setTitle(R.string.snap_rename)
				.setView(layout)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					String nn = et == null ? "" : et.getText().toString().trim();
					if (!nn.isEmpty()) {
						SnapVm.renameSlot(si.slot, nn);
						showSlotDrawer(MODE_SNAP_EDIT);
					}
				})
				.show();
	}

	private void confirmSlotDelete(SnapVm.SlotInfo si) {
		new AlertDialog.Builder(this)
				.setMessage(R.string.snap_delete_msg)
				.setPositiveButton(android.R.string.yes, (d, w) -> {
					SnapVm.deleteSlot(si.slot);
					Toast.makeText(this, R.string.snap_deleted, Toast.LENGTH_SHORT).show();
					showSlotDrawer(MODE_SNAP_EDIT);
				})
				.setNegativeButton(android.R.string.no, null)
				.show();
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
			showExitConfirmation();
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
				&& (event.getFlags() & (KeyEvent.FLAG_LONG_PRESS | KeyEvent.FLAG_CANCELED)) == 0) {
			openOptionsMenu();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.midlet_displayable, menu);
		if (actionBarEnabled) {
			menu.findItem(R.id.action_take_screenshot).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		}
		if (ContextHolder.getVk() == null) {
			menu.findItem(R.id.action_submenu_vk).setVisible(false);
		}
		if (!SnapVm.enabled()) {
			menu.findItem(R.id.action_snap_save).setVisible(false);
			menu.findItem(R.id.action_snap_load).setVisible(false);
			menu.findItem(R.id.action_snap_edit).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (current instanceof Canvas) {
			menu.setGroupVisible(R.id.action_group_canvas, true);
		} else {
			menu.setGroupVisible(R.id.action_group_canvas, false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		VirtualKeyboard vkEdit = ContextHolder.getVk();
		if (vkEdit != null && vkEdit.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF) {
			// a layout edit session is active — ask whether to keep it
			// before running whatever the user picked in the drawer
			confirmLayoutEditThen(item);
			return true;
		}
		if (id == R.id.action_exit_midlet) {
			showExitConfirmation();
		} else if (id == R.id.action_save_log) {
			saveLog();
		} else if (id == R.id.action_snap_save) {
			showSlotDrawer(MODE_SNAP_SAVE);
		} else if (id == R.id.action_snap_load) {
			showSlotDrawer(MODE_SNAP_LOAD);
		} else if (id == R.id.action_snap_edit) {
			showSlotDrawer(MODE_SNAP_EDIT);
		} else if (id == R.id.action_lock_orientation) {
			if (item.isChecked()) {
				VirtualKeyboard vk = ContextHolder.getVk();
				int orientation = vk != null && vk.isPhone() ? ORIENTATION_PORTRAIT : microLoader.getOrientation();
				setOrientation(orientation);
				item.setChecked(false);
			} else {
				lockOrientation();
				item.setChecked(true);
			}
		} else if (id == R.id.action_shader) {
			showShaderDialog();
		} else if (id == R.id.action_native_resolution) {
			showNativeResolutionDialog();
		} else if (id == R.id.action_scale_settings) {
			showScaleSettingsDialog();
		} else if (id == R.id.action_take_screenshot) {
			takeScreenshot();
		} else if (id == R.id.action_limit_fps) {
			showLimitFpsDialog();
		} else if (ContextHolder.getVk() != null) {
			// Handled only when virtual keyboard is enabled
			handleVkOptions(id);
		}
		return true;
	}



	/**
	 * In-game shader switcher — same resolution rules as the config screen
	 * (per-game file wins; "follow global" resolves the global preference).
	 * The pick is applied live on the GL thread and written back to the
	 * game's config.json so it survives relaunch.
	 */
	private void showShaderDialog() {
		final String[] items = {
				getString(R.string.shader_follow_global),
				getString(R.string.shader_load_file),
		};
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_SHADER_FILTER)
				.setItems(items, (dialog, which) -> {
					if (which == 0) {
						microLoader.params.shader = null;
						applyShaderChanged();
					} else {
						pickShaderFile();
					}
				})
				.show();
	}

	private void pickShaderFile() {
		shaderPickerContract.setStartPath(microLoader.getShadersDir());
		shaderPicker.launch(new String[]{".ini"});
	}

	private void onShaderPicked(Uri uri) {
		if (uri == null || uri.getPath() == null) {
			return;
		}
		File file = new File(uri.getPath());
		String error = ShaderList.validate(file);
		if (error != null) {
			new AlertDialog.Builder(this)
					.setTitle(R.string.shader_load_failed)
					.setMessage(error)
					.setPositiveButton(android.R.string.ok, null)
					.show();
			return;
		}
		microLoader.params.shader = ShaderList.loadIni(file);
		applyShaderChanged();
	}

	private void applyShaderChanged() {
		microLoader.reloadShader();
		ProfilesManager.saveConfig(microLoader.params);
		ShaderInfo shader = microLoader.params.shader;
		if (current instanceof Canvas) {
			((Canvas) current).applyShader();
		}
		if (microLoader.params.graphicsMode != 1) {
			Toast.makeText(this, R.string.shader_needs_gles, Toast.LENGTH_LONG).show();
			return;
		}
		Toast.makeText(this, shader == null
						? getString(R.string.shader_none_selected)
						: getString(R.string.shader_loaded, shader.getName()),
				Toast.LENGTH_SHORT).show();
	}

	/**
	 * In-game virtual-resolution switcher. The jar does not declare supported
	 * resolutions (verified across the library — manifests carry none), so the
	 * list is the same preset set the config screen offers. Picking a size
	 * updates the guest-visible getWidth/getHeight live, fires sizeChanged to
	 * the MIDlet and persists to the game's config.json.
	 */
	private void showNativeResolutionDialog() {
		List<Size> presets = collectResolutionPresets();
		String[] labels = new String[presets.size()];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = presets.get(i).toString();
		}
		ProfileModel params = microLoader.params;
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.action_native_resolution)
						+ "（" + params.screenWidth + " x " + params.screenHeight + "）")
				.setItems(labels, (dialog, which) ->
						applyNativeResolution(presets.get(which)))
				.show();
	}

	private void applyNativeResolution(Size size) {
		ProfileModel params = microLoader.params;
		params.screenWidth = size.width;
		params.screenHeight = size.height;
		Displayable.setVirtualSize(size.width, size.height);
		if (current instanceof Canvas) {
			Canvas c = (Canvas) current;
			c.updateSize();
			Display.postEvent(CanvasEvent.getInstance(c,
					CanvasEvent.SIZE_CHANGED, c.getWidth(), c.getHeight()));
		}
		ProfilesManager.saveConfig(params);
		Toast.makeText(this, getString(R.string.action_native_resolution)
				+ ": " + size, Toast.LENGTH_SHORT).show();
	}

	/**
	 * In-game scale-type switcher — same options as the per-game config
	 * (none/fit/fill) plus integer scaling. Only the screen mapping is
	 * recalculated; the guest canvas size stays untouched, so no SIZE_CHANGED.
	 */
	private void showScaleSettingsDialog() {
		String[] base = getResources().getStringArray(R.array.pref_scale_type_entries);
		String[] items = Arrays.copyOf(base, base.length + 1);
		items[base.length] = getString(R.string.pref_scale_mode_integer);
		int cur = microLoader.params.screenScaleType;
		new AlertDialog.Builder(this)
				.setTitle(R.string.action_scale_settings)
				.setSingleChoiceItems(items, cur >= 0 && cur < items.length ? cur : -1,
						(dialog, which) -> {
							microLoader.params.screenScaleType = which;
							if (which == 3) {
								// integer scaling looks broken unless centered
								microLoader.params.screenGravity = 2;
							}
							if (current instanceof Canvas) {
								((Canvas) current).updateSize();
							}
							ProfilesManager.saveConfig(microLoader.params);
							dialog.dismiss();
						})
				.show();
	}

	private List<Size> collectResolutionPresets() {
		ArrayList<Size> presets = new ArrayList<>(Arrays.asList(
				new Size(128, 128), new Size(128, 160), new Size(132, 176),
				new Size(176, 208), new Size(176, 220), new Size(240, 320),
				new Size(240, 400), new Size(320, 240), new Size(352, 416),
				new Size(360, 640), new Size(640, 360), new Size(800, 480)));
		int w = getResources().getDisplayMetrics().widthPixels;
		int h = getResources().getDisplayMetrics().heightPixels;
		if (w > h) {
			presets.add(new Size(h * 3 / 4, h));
			presets.add(new Size(h * 4 / 3, h));
		} else {
			presets.add(new Size(w, w * 4 / 3));
			presets.add(new Size(w, w * 3 / 4));
		}
		presets.add(new Size(w, h));
		Set<String> user = PreferenceManager.getDefaultSharedPreferences(this)
				.getStringSet("ResolutionsPreset", null);
		if (user != null) {
			for (String s : user) {
				Size size = Size.parse(s);
				if (size != null) {
					presets.add(size);
				}
			}
		}
		Collections.sort(presets);
		ArrayList<Size> out = new ArrayList<>();
		Size prev = null;
		for (Size s : presets) {
			if (!s.equals(prev)) {
				out.add(s);
			}
			prev = s;
		}
		return out;
	}

	private void lockOrientation() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			setRequestedOrientation(SCREEN_ORIENTATION_LOCKED);
			return;
		}
		Configuration configuration = getResources().getConfiguration();
		int rotation = getWindowManager().getDefaultDisplay().getRotation();

		// Search for the natural position of the device
		if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
				(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) ||
				configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
						(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)) {
			// Natural position is Landscape
			setRequestedOrientation(switch (rotation) {
				case Surface.ROTATION_0 -> SCREEN_ORIENTATION_LANDSCAPE;
				case Surface.ROTATION_90 -> SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				case Surface.ROTATION_180 -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				case Surface.ROTATION_270 -> SCREEN_ORIENTATION_PORTRAIT;
				default -> SCREEN_ORIENTATION_UNSPECIFIED;
			});
		} else {
			// Natural position is Portrait
			setRequestedOrientation(switch (rotation) {
				case Surface.ROTATION_0 -> SCREEN_ORIENTATION_PORTRAIT;
				case Surface.ROTATION_90 -> SCREEN_ORIENTATION_LANDSCAPE;
				case Surface.ROTATION_180 -> SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				case Surface.ROTATION_270 -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				default -> SCREEN_ORIENTATION_UNSPECIFIED;
			});
		}
	}

	private void handleVkOptions(int id) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (id == R.id.action_layout_edit_mode) {
			beginVkLayoutEdit(VirtualKeyboard.LAYOUT_KEYS);
			Toast.makeText(this, R.string.layout_edit_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_scale_mode) {
			beginVkLayoutEdit(VirtualKeyboard.LAYOUT_SCALES);
			Toast.makeText(this, R.string.layout_scale_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_switch) {
			showSetLayoutDialog();
		} else if (id == R.id.action_hide_buttons) {
			showHideButtonDialog();
		} else if (id == R.id.action_key_mapping) {
			openKeyMapper();
		}
	}

	/**
	 * Opens the same key-mapping screen the per-game config offers, with the
	 * guest VM frozen underneath (SnapVm.setFrozen — same mechanism the VK
	 * layout editor uses). When the mapper returns, onResume picks the flag
	 * up: the possibly-rewritten config.json is merged back into the live
	 * profile and the freeze lifted.
	 */
	private void openKeyMapper() {
		ProfileModel params = microLoader.params;
		if (params == null || params.dir == null) {
			return;
		}
		frozenForKeyMap = true;
		setGameFrozen(true);
		try {
			startActivity(new Intent(this, KeyMapperActivity.class)
					.setData(Uri.parse(params.dir.getPath())));
		} catch (Exception e) {
			frozenForKeyMap = false;
			setGameFrozen(false);
			throw e;
		}
	}

	/**
	 * KeyMapperActivity edits a fresh ProfileModel loaded from config.json
	 * and persists it on finish; re-load the mapping into the running
	 * profile and rebuild the live key tables so changes apply immediately.
	 */
	private void applyKeyMappingsFromDisk() {
		ProfileModel params = microLoader.params;
		if (params == null || params.dir == null) {
			return;
		}
		ProfileModel fresh = ProfilesManager.loadConfig(params.dir);
		if (fresh != null) {
			params.keyMappings = fresh.keyMappings;
		}
		KeyMapper.setKeyMapping(params);
		menuKey = microLoader.getMenuKeyCode();
	}

	/**
	 * Freezes the game (SnapVM: hard freeze of the guest VM; dex path:
	 * pauseApp) and shows the on-screen done/discard bar while the key
	 * layout is being edited.
	 */
	private void beginVkLayoutEdit(int mode) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null) {
			return;
		}
		vk.beginLayoutEdit(mode);
		binding.vkEditSwap.setVisibility(
				mode == VirtualKeyboard.LAYOUT_KEYS ? View.VISIBLE : View.GONE);
		binding.vkEditBar.setVisibility(View.VISIBLE);
		// keep the drawer locked while editing — otherwise an edge pull steals
		// the touch stream from keys being dragged along the left edge
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
		// take the hide-all toggle away while editing — toggling key
		// visibility mid-edit makes no sense (all keys are shown anyway)
		binding.vkHideAll.removeCallbacks(vkHideAllFadeOut);
		binding.vkHideAll.setVisibility(View.GONE);
		setGameFrozen(true);
	}

	private void endVkLayoutEdit(boolean save) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null) {
			return;
		}
		if (save) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
			if (vk.isPhone()) {
				showSaveVkAlert(false);
			} else {
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
				Toast.makeText(this, R.string.layout_edit_finished, Toast.LENGTH_SHORT).show();
			}
		} else {
			vk.discardLayoutEdit();
		}
		binding.vkEditBar.setVisibility(View.GONE);
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		setGameFrozen(false);
		flashHideAllButton();
	}

	private void setGameFrozen(boolean frozen) {
		if (SnapVm.instance() != null) {
			SnapVm.setFrozen(frozen);
		} else if (frozen) {
			MidletThread.pauseApp();
		} else {
			MidletThread.resumeApp();
		}
	}

	private void confirmLayoutEditThen(MenuItem item) {
		new AlertDialog.Builder(this)
				.setMessage(R.string.vk_layout_save_prompt)
				.setPositiveButton(R.string.save, (d, w) -> {
					endVkLayoutEdit(true);
					onOptionsItemSelected(item);
				})
				.setNegativeButton(R.string.discard, (d, w) -> {
					endVkLayoutEdit(false);
					onOptionsItemSelected(item);
				})
				.setNeutralButton(R.string.CANCEL_CMD, (d, w) -> {
					// the drawer already flipped checkable items — revert so
					// the checkbox still reflects reality
					if (item.isCheckable()) {
						item.setChecked(!item.isChecked());
					}
				})
				.show();
	}

	private final Runnable vkHideAllFadeOut =
			() -> binding.vkHideAll.setVisibility(View.GONE);

	private static boolean allKeysHidden(VirtualKeyboard vk) {
		for (boolean hidden : vk.getKeysVisibility()) {
			if (!hidden) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Shows the hide-all toggle and refreshes its label. While keys are
	 * visible it simply stays on screen alongside the keyboard; once every
	 * key is hidden it fades out after a few seconds — any touch on the game
	 * window re-arms it so the "show all" escape hatch is never lost.
	 */
	private void flashHideAllButton() {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null || vk.getLayoutEditMode() != VirtualKeyboard.LAYOUT_EOF) {
			return;
		}
		binding.vkHideAll.setText(
				allKeysHidden(vk) ? R.string.vk_show_all : R.string.vk_hide_all);
		binding.vkHideAll.setVisibility(View.VISIBLE);
		binding.vkHideAll.removeCallbacks(vkHideAllFadeOut);
		if (allKeysHidden(vk)) {
			binding.vkHideAll.postDelayed(vkHideAllFadeOut, 3000);
		}
	}

	private void toggleHideAllKeys() {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null) {
			return;
		}
		boolean[] states = vk.getKeysVisibility();
		Arrays.fill(states, !allKeysHidden(vk));
		vk.setKeysVisibility(states);
		vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
		flashHideAllButton();
	}

	@SuppressLint("CheckResult")
	private void takeScreenshot() {
		microLoader.takeScreenshot(current, new SingleObserver<>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				Toast.makeText(MicroActivity.this, getString(R.string.screenshot_saved)
						+ " " + s, Toast.LENGTH_LONG).show();
				MediaScannerConnection.scanFile(MicroActivity.this, new String[]{s}, null, null);
			}

			@Override
			public void onError(@NonNull Throwable e) {
				e.printStackTrace();
				Toast.makeText(MicroActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void saveLog() {
		try {
			LogUtils.writeLog();
			Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		boolean[] states = vk.getKeysVisibility();
		boolean[] changed = states.clone();
		new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(vk.getKeyNames(), changed, (dialog, which, isChecked) -> {})
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					if (!Arrays.equals(states, changed)) {
						vk.setKeysVisibility(changed);
						showSaveVkAlert(true);
					}
					flashHideAllButton();
				}).show();
	}

	private void showSaveVkAlert(boolean keepScreenPreferred) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.CONFIRMATION_REQUIRED);
		builder.setMessage(R.string.pref_vk_save_alert);
		builder.setNegativeButton(android.R.string.no, null);
		AlertDialog dialog = builder.create();

		final VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.isPhone()) {
			AppCompatCheckBox cb = new AppCompatCheckBox(this);
			cb.setText(R.string.opt_save_screen_params);
			cb.setChecked(keepScreenPreferred);

			TypedValue out = new TypedValue();
			getTheme().resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding, out, true);
			int paddingH = getResources().getDimensionPixelOffset(out.resourceId);
			int paddingT = getResources().getDimensionPixelOffset(androidx.appcompat.R.dimen.abc_dialog_padding_top_material);
			dialog.setView(cb, paddingH, paddingT, paddingH, 0);

			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) -> {
				if (cb.isChecked()) {
					vk.saveScreenParams();
				}
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
			});
		} else {
			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) ->
					ContextHolder.getVk().onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM));
		}
		dialog.show();
	}

	private void showSetLayoutDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.layout_switch)
				.setSingleChoiceItems(R.array.PREF_VK_TYPE_ENTRIES, vk.getLayout(), null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					vk.setLayout(((AlertDialog) d).getListView().getCheckedItemPosition());
					flashHideAllButton();
					if (vk.isPhone()) {
						setOrientation(ORIENTATION_PORTRAIT);
					} else {
						setOrientation(microLoader.getOrientation());
					}
				});
		builder.show();
	}

	private void showLimitFpsDialog() {
		TextInputLayout inputLayout = DialogInputBinding.inflate(getLayoutInflater()).getRoot();
		EditText editText = Objects.requireNonNull(inputLayout.getEditText());
		editText.setHint(R.string.unlimited);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
		editText.setMaxLines(1);
		editText.setSingleLine(true);
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_LIMIT_FPS)
				.setView(inputLayout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					Editable text = editText.getText();
					int fps = 0;
					try {
						fps = TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text.toString().trim());
					} catch (NumberFormatException ignored) {
					}
					Canvas.setLimitFps(fps);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.reset, ((d, which) -> Canvas.setLimitFps(-1)))
				.show();
	}

	@Override
	public boolean onContextItemSelected(@NonNull MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		}

		return super.onContextItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		ContextHolder.notifyOnActivityResult(requestCode, resultCode, data);
	}

	public String getAppName() {
		return appName;
	}

	public void toast(@StringRes int message) {
		runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
	}

	private class SetCurrentEvent extends SimpleEvent {
		private final Displayable current;
		private final Displayable next;

		private SetCurrentEvent(Displayable current, Displayable next) {
			this.current = current;
			this.next = next;
		}

		@Override
		public void process() {
			closeOptionsMenu();
			if (current != null) {
				current.clearDisplayableView();
			}
			binding.displayableContainer.removeAllViews();
			// the top action bar is gone for good: it only stole vertical space
			// from the game screen, the menu now lives in the left edge drawer
			ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.hide();
			}
			if (next instanceof Canvas) {
				hideSystemUI();
			} else {
				showSystemUI();
			}
			final String title = next != null ? next.getTitle() : null;
			if (drawerLayout != null) {
				binding.menuDrawerTitle.setText(title == null ? appName : title);
			}
			binding.overlay.setLocation(0, 0);
			if (next != null) {
				binding.displayableContainer.addView(next.getDisplayableView());
			}
		}
	}
}
