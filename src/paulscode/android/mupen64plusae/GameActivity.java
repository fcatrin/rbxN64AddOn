/**
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If
 * not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: littleguy77
 */
package paulscode.android.mupen64plusae;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;
import paulscode.android.mupen64plusae.persistent.UserPrefs;
import retrobox.content.SaveStateInfo;
import retrobox.utils.GamepadInfoDialog;
import retrobox.utils.ImmersiveModeSetter;
import retrobox.utils.ListOption;
import retrobox.utils.RetroBoxDialog;
import retrobox.utils.RetroBoxUtils;
import retrobox.utils.SaveStateSelectorAdapter;
import retrobox.v2.paulscode.android.mupen64plus.free.R;
import retrobox.vinput.AnalogGamepad;
import retrobox.vinput.AnalogGamepad.Axis;
import retrobox.vinput.AnalogGamepadListener;
import retrobox.vinput.GamepadDevice;
import retrobox.vinput.GamepadMapping;
import retrobox.vinput.GamepadMapping.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.QuitHandler;
import retrobox.vinput.QuitHandler.QuitHandlerCallback;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrobox.vinput.overlay.Overlay;
import retrobox.vinput.overlay.OverlayGamepadController;
import retrobox.vinput.overlay.OverlayGamepadView;
import xtvapps.core.AndroidFonts;
import xtvapps.core.Callback;
import xtvapps.core.SimpleCallback;
import xtvapps.core.content.KeyValue;

public class GameActivity extends Activity
{
	private static final String LOGTAG = GameActivity.class.getName(); 
    private final GameLifecycleHandler mLifecycleHandler;
    private GameMenuHandler mMenuHandler;
    
    // Input controls for RetroBox
    public static final Overlay overlay = new Overlay();
	public static Mapper mapper;
	private VirtualInputDispatcher vinputDispatcher;
    private OverlayGamepadView overlayGamepadView;
    private OverlayGamepadController overlayGamepadController;
    private View mSurfaceView;
    
    private File stateFile = null;
    private int  saveSlot = 0;
    
    boolean is8bitdoN64[] = new boolean[4];
    
    AnalogGamepad analogGamepad;
    
	private GamepadInfoDialog gamepadInfoDialog;
    
    public GameActivity()
    {
        mLifecycleHandler = new GameLifecycleHandler( this );
    }
    
    private File getTempScreenshotFile() {
    	return new File(getCacheDir(), "mupen64.png");
    }
    
    private void openRetroBoxMenu(final boolean pause) {
    	new Handler().postDelayed(new Runnable(){

			@Override
			public void run() {
				openRetroBoxMenuPost(pause);
			}
		}, 100);
    }
    
    private void openRetroBoxMenuPost(boolean pause) {

    	if (pause) onPause();
    	
    	List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("", getString(R.string.emu_opt_cancel)));
    	options.add(new ListOption("load", getString(R.string.emu_opt_state_load)));
    	options.add(new ListOption("save", getString(R.string.emu_opt_state_save)));
    	if (Mapper.hasGamepads()) {
    		options.add(new ListOption("controls", getString(R.string.emu_n64_control_opt_title)));
    	}
    	options.add(new ListOption("help", getString(R.string.emu_opt_help)));
    	options.add(new ListOption("quit", getString(R.string.emu_opt_quit)));
    	
    	
    	RetroBoxDialog.showListDialog(this, getString(R.string.emu_opt_title), options, new Callback<KeyValue>() {
			@Override
			public void onResult(KeyValue result) {
				String key = result.getKey();
				if (key.equals("load")) {
					uiSelectSaveState(true);
					return;
				} else if (key.equals("save")) {
					uiSelectSaveState(false);
					return;
				} else if (key.equals("quit")) {
					uiQuit();
				} else if (key.equals("help")) {
					uiHelp();
					return;
				} else if (key.equals("controls")) {
					uiControlOptions();
					return;
				}
				onResume();
			}

			@Override
			public void onError() {
				onResume();
			}
		});
    	
    }
    
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        UserPrefs userPrefs = new UserPrefs( this );
        if (MainActivity.fromRetroBox) {
        	mMenuHandler = new GameMenuHandler( this, userPrefs.manualSaveDir, userPrefs.selectedGameAutoSavefile );
        }

        mLifecycleHandler.onCreateBegin( savedInstanceState );
        super.onCreate( savedInstanceState );
        mLifecycleHandler.onCreateEnd( savedInstanceState );
        
        AndroidFonts.setViewFont(findViewById(R.id.txtDialogListTitle), RetroBoxUtils.FONT_DEFAULT_M);
        
        AndroidFonts.setViewFont(findViewById(R.id.txtGamepadInfoTop), RetroBoxUtils.FONT_DEFAULT_M);
        AndroidFonts.setViewFont(findViewById(R.id.txtGamepadInfoBottom), RetroBoxUtils.FONT_DEFAULT_M);

        gamepadInfoDialog = new GamepadInfoDialog(this);
        gamepadInfoDialog.loadFromIntent(MainActivity.publicIntent);
        
        if (MainActivity.fromRetroBox) {
        	setImmersiveMode();
        	
        	for(int i=0; i<is8bitdoN64.length; i++) {
        		is8bitdoN64[i] = MainActivity.publicIntent.getBooleanExtra("is8bitdoN64." + i, false);
        	}
        	
        	String saveStateDir = MainActivity.publicIntent.getStringExtra("saveStatePath"); 
        	stateFile = new File(saveStateDir, "save");

        	overlayGamepadController = new OverlayGamepadController();
        	vinputDispatcher = new VirtualInputDispatcher();
        	
            mapper = new Mapper(MainActivity.publicIntent, vinputDispatcher);
            Mapper.initGestureDetector(this);
            
            String controls =  MainActivity.publicIntent.getStringExtra("controls");
            if (controls!=null) {
            	if (controls.equals("swapped")) controlType = ControlType.Swapped;
            	else if (controls.equals("goldeneye")) controlType = ControlType.GoldenEye;
            }
            
            overlayGamepadView = new OverlayGamepadView(this, overlay);
            
        	mSurfaceView = findViewById(R.id.gameSurface);
            ViewGroup root = (ViewGroup)findViewById(R.id.root);
        	setupGamepadOverlay(root);
        	analogGamepad = new AnalogGamepad(0, 0, new AnalogGamepadListener() {
    			
    			@Override
    			public void onMouseMoveRelative(float mousex, float mousey) {}
    			
    			@Override
    			public void onMouseMove(int mousex, int mousey) {}
    			
    			@Override
    			public void onAxisChange(GamepadDevice gamepad, float axisx, float axisy, float hatx, float haty, float raxisx, float raxisy) {
    				if (controlType == ControlType.Original) {
    					vinputDispatcher.sendAnalog(gamepad, Analog.LEFT, axisx, -axisy, hatx, haty);
    				} else if (controlType == ControlType.Swapped) {
    					vinputDispatcher.sendAnalog(gamepad, Analog.LEFT, raxisx, -raxisy, hatx, haty);
    				} else if (controlType == ControlType.GoldenEye) {
    					vinputDispatcher.sendAnalog(gamepad, Analog.LEFT, raxisx, -axisy, hatx, haty);
    				}
    				if (gamepad!=null && !is8bitdoN64[gamepad.player]) {
        				if (controlType == ControlType.Original) {
        					vinputDispatcher.sendAnalog(gamepad, Analog.RIGHT, raxisx, raxisy, 0, 0);
        				} else if (controlType == ControlType.Swapped) {
        					vinputDispatcher.sendAnalog(gamepad, Analog.RIGHT, axisx, axisy, 0, 0);
        				} else if (controlType == ControlType.GoldenEye) {
        					vinputDispatcher.sendAnalog(gamepad, Analog.RIGHT, axisx, raxisy, 0, 0);
        				}
    				}
    			}

				@Override
				public void onDigitalX(GamepadDevice gamepad, Axis axis, boolean on) {}

				@Override
				public void onDigitalY(GamepadDevice gamepad, Axis axis, boolean on) {}
				
				@Override
				public void onTriggers(String deviceName, int deviceId, boolean left, boolean right) {
					mapper.handleTriggerEventByDeviceName(deviceName, deviceId, left, right);
				}

				@Override
				public void onTriggersAnalog(GamepadDevice gamepad, int deviceId, float left, float right) {}

    		});
        }
    }
    
    private void setImmersiveMode() {
    	ImmersiveModeSetter.get().setImmersiveMode(getWindow(), isStableLayout());
	}

	private boolean isStableLayout() {
		return Mapper.hasGamepads();
	}
    
    @Override
    protected void onResume()
    {
        super.onResume();
        ImmersiveModeSetter.postImmersiveMode(new Handler(), getWindow(), isStableLayout());
        mLifecycleHandler.onResume();
    }
    
    @Override
    protected void onPause()
    {
        super.onPause();
        mLifecycleHandler.onPause();
    }
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mLifecycleHandler.onDestroy();
    }
    
    @Override
	public boolean onGenericMotionEvent(MotionEvent event) {
    	if (RetroBoxDialog.isDialogVisible(this)) {
    		return super.onGenericMotionEvent(event);
    	}
    	
		if (analogGamepad != null && analogGamepad.onGenericMotionEvent(event)) return true;
		return super.onGenericMotionEvent(event);
	}
    
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (RetroBoxDialog.isDialogVisible(this)) {
			return super.dispatchTouchEvent(ev);
		}
		
		if (overlayGamepadView.isVisible() && overlayGamepadController.onTouchEvent(ev)) {
			if (Overlay.requiresRedraw) {
				Overlay.requiresRedraw = false;
				overlayGamepadView.invalidate();
			}
			return true;
		}
		
		mapper.onTouchEvent(ev);
		
		return super.dispatchTouchEvent(ev);
	}
	
	private int last_w = 0;
	private int last_h = 0;
	private enum ControlType {Original, Swapped, GoldenEye};
	private ControlType controlType = ControlType.Original;
	
	private void setupGamepadOverlay(final ViewGroup root) {
		ViewTreeObserver observer = root.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				int w = root.getWidth();
				int h = root.getHeight();
				if (w == last_w || h == last_h) return;
				last_w = w;
				last_h = h;
				
				Log.d("OVERLAY", "set dimensions " + w + "x" + h);
				mLifecycleHandler.updateScreenSize(w, h);
				if (needsOverlay()) {
					String overlayConfig = MainActivity.publicIntent.getStringExtra("OVERLAY");
					float alpha = MainActivity.publicIntent.getFloatExtra("OVERLAY_ALPHA", 0.8f);
					if (overlayConfig!=null) overlay.init(overlayConfig, w, h, alpha);
				}
				}
			});
		
		Log.d("OVERLAY", "setupGamepadOverlay");
		if (needsOverlay()) {
			Log.d("OVERLAY", "has Overlay");
			overlayGamepadView.addToLayout(root);
			overlayGamepadView.showPanel();
		}
	}
	
	private boolean needsOverlay() {
		return !Mapper.hasGamepads();
	}

    @Override
	public void onBackPressed() {
    	if (RetroBoxDialog.cancelDialog(this)) return;
    	
    	openRetroBoxMenu(true);
	}

    private void toastMessage(final String message) {
    	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String getSaveStateFileName() {
    	return getSaveStateFileName(saveSlot);
    }

    private String getSaveStateFileName(int fileSlot) {
    	return stateFile.getAbsolutePath() + "_" + fileSlot + ".state";
    }
    
    private void uiLoadState() {
    	CoreInterfaceNative.emuLoadFile(getSaveStateFileName());

    	String msg = getString(R.string.emu_slot_loaded).replace("{n}", String.valueOf(saveSlot+1));
    	toastMessage(msg);
    }

    private void uiSaveState() {
    	String fileName = getSaveStateFileName();
    	// String fileNameScreenShot = fileName + ".png";
    	// CoreInterfaceNative.emuScreenshot(fileNameScreenShot);
    	CoreInterfaceNative.emuSaveFile(fileName);
    	String msg = getString(R.string.emu_slot_saved).replace("{n}", String.valueOf(saveSlot+1));
    	toastMessage(msg);
    }
    
    protected void uiControlOptions() {
    	List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("original",  getString(R.string.emu_n64_control_opt_original)));
    	options.add(new ListOption("swapped",   getString(R.string.emu_n64_control_opt_swapped)));
    	options.add(new ListOption("goldeneye", getString(R.string.emu_n64_control_opt_goldeneye)));
    	
    	RetroBoxDialog.showListDialog(this, getString(R.string.emu_n64_control_opt_title), options, new Callback<KeyValue>() {// TODO TRANSLATE

			@Override
			public void onResult(KeyValue result) {
				String key = result.getKey();
				if (key.equals("original")) {
					controlType = ControlType.Original;
				}
				if (key.equals("swapped")) {
					controlType = ControlType.Swapped;
				}
				if (key.equals("goldeneye")) {
					controlType = ControlType.GoldenEye;
				}
				toastActiveControlType();
				onResume();
			} 
		});
    }
    
    protected void uiHelp() {
		RetroBoxDialog.showGamepadDialogIngame(this, gamepadInfoDialog, Mapper.hasGamepads(), new SimpleCallback() {
			@Override
			public void onResult() {
				onResume();
			}
		});
    }

	private void uiQuit() {
		finish();
	}
	
    protected void uiQuitConfirm() {
    	QuitHandler.askForQuit(this, new QuitHandlerCallback() {
			@Override
			public void onQuit() {
				uiQuit();
			}
		});
    }
    
	private void uiSelectSaveState(final boolean isLoadingState) {
		List<SaveStateInfo> list = new ArrayList<SaveStateInfo>();
		for(int i=0; i<6; i++) {
			String fileName = getSaveStateFileName(i);
			Log.d(LOGTAG, "Reading filestate from " + fileName);
			list.add(new SaveStateInfo(new File(fileName), R.drawable.platform_n64));
		}
		
		final SaveStateSelectorAdapter adapter = new SaveStateSelectorAdapter(this, list, saveSlot);
		
		Callback<Integer> callback = new Callback<Integer>() {

			@Override
			public void onResult(Integer index) {
				System.out.println("setting save slot to " + index + " loading " + isLoadingState);
				boolean invalidSlot = isLoadingState && 
						!((SaveStateInfo)adapter.getItem(index)).exists();
				
				if (!invalidSlot) {
					saveSlot = index;
					if (isLoadingState) {
						uiLoadState();
					} else {
						uiSaveState();
					}
					RetroBoxDialog.cancelDialog(GameActivity.this);
				}
			}

			@Override
			public void onFinally() {
				onResume();
			}
			
		};
		
		String title = isLoadingState ?
				getString(R.string.emu_slot_load_title) :
				getString(R.string.emu_slot_save_title);
		
		RetroBoxDialog.showSaveStatesDialog(this, title, adapter, callback);
	}

	class VirtualInputDispatcher implements VirtualEventDispatcher {
		
        /** N64 button: dpad-right. */
        public static final int DPD_R = 0;
        
        /** N64 button: dpad-left. */
        public static final int DPD_L = 1;
        
        /** N64 button: dpad-down. */
        public static final int DPD_D = 2;
        
        /** N64 button: dpad-up. */
        public static final int DPD_U = 3;
        
        /** N64 button: start. */
        public static final int START = 4;
        
        /** N64 button: trigger-z. */
        public static final int BTN_Z = 5;
        
        /** N64 button: b. */
        public static final int BTN_B = 6;
        
        /** N64 button: a. */
        public static final int BTN_A = 7;
        
        /** N64 button: cpad-right. */
        public static final int CPD_R = 8;
        
        /** N64 button: cpad-left. */
        public static final int CPD_L = 9;
        
        /** N64 button: cpad-down. */
        public static final int CPD_D = 10;
        
        /** N64 button: cpad-up. */
        public static final int CPD_U = 11;
        
        /** N64 button: shoulder-r. */
        public static final int BTN_R = 12;
        
        /** N64 button: shoulder-l. */
        public static final int BTN_L = 13;
        
        public static final int MODE = 14;  // toggle analog/digital

    	public static final int NUM_N64_BUTTONS = 16;
    	
    	int buttonMapOverlay[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			BTN_B, BTN_A, CPD_D, CPD_U,
    			BTN_L, BTN_R, CPD_R, CPD_L,
    			BTN_Z, BTN_Z, MODE, START
    	};

    	int buttonMapOriginal[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			BTN_A, BTN_B, CPD_D, CPD_U,
    			CPD_L, CPD_R, BTN_Z, BTN_R,
    			BTN_L, BTN_R, MODE, START
    	};
    	
    	int buttonMapGoldenEye[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			BTN_A, BTN_B, CPD_D, CPD_U,
    			CPD_L, CPD_R, BTN_L, BTN_Z,
    			BTN_L, BTN_R, MODE, START
    	};
    	
    	public boolean[][] buttons = new boolean[4][NUM_N64_BUTTONS];
    	
    	private static final int ANALOG_MAX_X = 80;
    	private static final int ANALOG_MAX_Y = 80;
    	int analogX[] = new int[4];
    	int analogY[] = new int[4];

    	private long lastUpdate = 0;
    	
    	private int getDpadSignature(int player) {
    		boolean dpad[] = buttons[player];
    		return (dpad[DPD_L]?1:0) * 8 + (dpad[DPD_R]?1:0) * 4 + (dpad[DPD_U]?1:0) * 2 + (dpad[DPD_D]?1:0); 
    	}

    	private int getCameraSignature(int player) {
    		boolean dpad[] = buttons[player];
    		return (dpad[CPD_L]?1:0) * 8 + (dpad[CPD_R]?1:0) * 4 + (dpad[CPD_U]?1:0) * 2 + (dpad[CPD_D]?1:0); 
    	}

    	@Override
    	public void sendAnalog(GamepadDevice gamepad, Analog index, double x, double y, double hatx, double haty) {
    		int player = gamepad.player;
    		
    		if (index == Analog.LEFT) {
    		
	    		int newX = (int)(ANALOG_MAX_X * x);
	    		int newY = (int)(ANALOG_MAX_Y * y);

	    		analogX[player] = newX;
	    		analogY[player] = newY;
	    		
	    		long t = System.currentTimeMillis();
	    		if (t-lastUpdate>64 || (newX == 0 && newY == 0)) {
	    			lastUpdate = t;
		    		overlayGamepadView.postInvalidate();
	    		}
    		} else if (index == Analog.RIGHT) {
    			double threshold = 0.2;
    			int oldCamera = getCameraSignature(player); 
    			buttons[player][CPD_U] = y < -threshold;
    			buttons[player][CPD_D] = y >  threshold;
    			buttons[player][CPD_L] = x < -threshold;
    			buttons[player][CPD_R] = x >  threshold;
    			
    			int newCamera = getCameraSignature(player);
    			if (newCamera == oldCamera) return;
    		}
    		
    		buttons[player][DPD_U] = haty < 0;
    		buttons[player][DPD_D] = haty > 0;
    		buttons[player][DPD_L] = hatx < 0;
    		buttons[player][DPD_R] = hatx > 0;
    		
    		notifyChange(gamepad==null?0:gamepad.player);
    	};
    	
    	private void notifyChange(int player) {
    		// Log.d(LOGTAG, "Send change for player " + player + " " + buttons[player] + " x, y = " + analogX + ", " + analogY);
    		CoreInterfaceNative.setControllerState(player, buttons[player], analogX[player], analogY[player]);
    	}
    	
		@Override
		public void sendKey(GamepadDevice gamepad, int keyCode, boolean down) {

			int index = GamepadMapping.getOriginIndex(keyCode);
			if (index == MODE) {
				if (!down) {
					int mode = controlType.ordinal() + 1;
					if (mode >= ControlType.values().length) {
						mode = 0;
					}
					
					controlType = ControlType.values()[mode];
					toastActiveControlType();
				}
				return;
			} else {
				int map[] = controlType == ControlType.GoldenEye ? buttonMapGoldenEye: buttonMapOriginal;
				int translatedIndex = gamepad.isOverlay?
						buttonMapOverlay[index]:
						map[index];
				buttons[gamepad.player][translatedIndex] = down;
				notifyChange(gamepad.player);
			}
			
		}

		@Override
		public void sendMouseButton(MouseButton button, boolean down) {}

		@Override
		public boolean handleShortcut(ShortCut shortcut, boolean down) {
			switch(shortcut) {
			case EXIT: if (!down) uiQuitConfirm(); return true;
			case LOAD_STATE: if (!down) {
				if (is8bitdoN64[0])
					openRetroBoxMenu(true);
				else 
					uiLoadState(); 
				return true;
			}
			case SAVE_STATE: if (!down) uiSaveState(); return true;
			case MENU : if (!down) openRetroBoxMenu(true); return true;
			default:
				return false;
			}
		}
    }

	public void toastActiveControlType() {
		String modeNames[] = {
				getString(R.string.emu_n64_control_original),
				getString(R.string.emu_n64_control_swapped),
				getString(R.string.emu_n64_control_goldeneye)
		};
		
		String msg = modeNames[controlType.ordinal()];
		
		toastMessage(msg);
	}
}
