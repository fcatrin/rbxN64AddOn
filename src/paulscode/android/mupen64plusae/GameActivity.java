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

import paulscode.android.mupen64plusae.persistent.UserPrefs;
import retrobox.paulscode.android.mupen64plus.free.R;
import retrobox.utils.ImmersiveModeSetter;
import retrobox.utils.ListOption;
import retrobox.utils.RetroBoxDialog;
import retrobox.utils.RetroBoxUtils;
import retrobox.vinput.AnalogGamepad;
import retrobox.vinput.AnalogGamepad.Axis;
import retrobox.vinput.AnalogGamepadListener;
import retrobox.vinput.GenericGamepad;
import retrobox.vinput.GenericGamepad.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.QuitHandler;
import retrobox.vinput.QuitHandler.QuitHandlerCallback;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrobox.vinput.overlay.GamepadController;
import retrobox.vinput.overlay.GamepadView;
import retrobox.vinput.overlay.Overlay;
import xtvapps.core.AndroidFonts;
import xtvapps.core.Callback;
import xtvapps.core.content.KeyValue;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;

public class GameActivity extends Activity
{
    private final GameLifecycleHandler mLifecycleHandler;
    private GameMenuHandler mMenuHandler;
    
    // Input controls for RetroBox
    public static final Overlay overlay = new Overlay();
	public static Mapper mapper;
	private VirtualInputDispatcher vinputDispatcher;
    private GamepadView gamepadView;
    private GamepadController gamepadController;
    private View mSurfaceView;
    
    private File stateFile = null;
    
    AnalogGamepad analogGamepad;
    
    public GameActivity()
    {
        mLifecycleHandler = new GameLifecycleHandler( this );
    }
    
    private void openRetroBoxMenu() {
    	onPause();
    	
    	List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("", "Cancel"));
    	options.add(new ListOption("load", "Load State"));
    	options.add(new ListOption("save", "Save State"));
    	options.add(new ListOption("quit", "Quit"));
    	
    	
    	RetroBoxDialog.showListDialog(this, "RetroBoxTV", options, new Callback<KeyValue>() {
			@Override
			public void onResult(KeyValue result) {
				String key = result.getKey();
				if (key.equals("load")) {
					uiLoadState();
				} else if (key.equals("save")) {
					uiSaveState();
				} else if (key.equals("quit")) {
					uiQuit();
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
        
        if (MainActivity.fromRetroBox) {
        	setImmersiveMode();
        	
        	stateFile = new File(MainActivity.publicIntent.getStringExtra("romPath") + ".state");
        	
        	gamepadController = new GamepadController();
        	vinputDispatcher = new VirtualInputDispatcher();
        	
        	boolean analogMode = MainActivity.publicIntent.getBooleanExtra("GAMEPAD_ANALOG", true);
        	vinputDispatcher.setAnalogMode(analogMode);
        	
            mapper = new Mapper(MainActivity.publicIntent, vinputDispatcher);
            Mapper.initGestureDetector(this);
            gamepadView = new GamepadView(this, overlay);
            
        	mSurfaceView = findViewById(R.id.gameSurface);
            ViewGroup root = (ViewGroup)findViewById(R.id.root);
        	setupGamepadOverlay(root);
        	analogGamepad = new AnalogGamepad(0, 0, new AnalogGamepadListener() {
    			
    			@Override
    			public void onMouseMoveRelative(float mousex, float mousey) {}
    			
    			@Override
    			public void onMouseMove(int mousex, int mousey) {}
    			
    			@Override
    			public void onAxisChange(float axisx, float axisy) {
    				vinputDispatcher.sendAnalog(Analog.LEFT, axisx, -axisy);
    			}

				@Override
				public void onDigitalX(Axis axis, boolean on) {}

				@Override
				public void onDigitalY(Axis axis, boolean on) {}

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
		
		if (gamepadView.isVisible() && gamepadController.onTouchEvent(ev)) {
			Log.d("TOUCH", "dispatched to gamepadController");
			if (Overlay.requiresRedraw) {
				Overlay.requiresRedraw = false;
				gamepadView.invalidate();
			}
			return true;
		}
		
		mapper.onTouchEvent(ev);
		
		return super.dispatchTouchEvent(ev);
	}
	
	private int last_w = 0;
	private int last_h = 0;
	
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
			gamepadView.addToLayout(root);
			gamepadView.showPanel();
		}
	}
	
	private boolean needsOverlay() {
		return !Mapper.hasGamepads();
	}

    @Override
	public void onBackPressed() {
    	if (RetroBoxDialog.cancelDialog(this)) return;
    	
    	openRetroBoxMenu();
	}

    private void toastMessage(final String message) {
    	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void uiLoadState() {
    	CoreInterfaceNative.emuLoadFile( stateFile.getAbsolutePath() );

    	toastMessage("State was restored");
    }

    private void uiSaveState() {
    	CoreInterfaceNative.emuSaveFile( stateFile.getAbsolutePath() );
    	toastMessage("State was saved");
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
	
	class VirtualInputDispatcher implements VirtualEventDispatcher {
		
		private boolean analogMode = true;
		
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
    			BTN_A, BTN_B, CPD_U, CPD_D,
    			BTN_L, BTN_R, BTN_Z, BTN_Z,
    			BTN_L, BTN_R, START, START
    	};

    	int buttonMapRealAnalog[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			CPD_D, BTN_A, CPD_U, BTN_B,
    			BTN_Z, BTN_R, CPD_L, CPD_R,
    			BTN_L, BTN_R, MODE, START
    	};

    	int buttonMapRealDigital[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			CPD_D, BTN_A, CPD_U, BTN_B,
    			BTN_L, BTN_R, CPD_L, CPD_R,
    			BTN_Z, BTN_R, MODE, START
    	};

    	
    	public boolean[] buttons = new boolean[NUM_N64_BUTTONS];
    	
    	private static final int ANALOG_MAX_X = 80;
    	private static final int ANALOG_MAX_Y = 80;
    	int analogX = 0;
    	int analogY = 0;

    	public void setAnalogMode(boolean analogMode) {
    		this.analogMode = analogMode;
    	}
    	
    	private long lastUpdate = 0;
    	
    	@Override
    	public void sendAnalog(GenericGamepad.Analog index, double x, double y) {
    		if (index!=Analog.LEFT) return;
    		
    		int newX = (int)(ANALOG_MAX_X * x);
    		int newY = (int)(ANALOG_MAX_Y * y);
    		
    		if (newX == analogX && newY == analogY) return;
    		analogX = newX;
    		analogY = newY;
    		
    		long t = System.currentTimeMillis();
    		if (t-lastUpdate>64 || (newX == 0 && newY == 0)) {
    			lastUpdate = t;
	    		gamepadView.postInvalidate();
    		}
    		notifyChange();
    	};
    	
    	private void notifyChange() {
            CoreInterfaceNative.setControllerState( 0 /*player*/, buttons, analogX, analogY);
    	}
    	
		@Override
		public void sendKey(int keyCode, boolean down) {
			int index = Mapper.genericJoysticks[0].getOriginIndex(keyCode);
			if (index == MODE) {
				if (!down) {
					analogMode = !analogMode;
					toastMessage("Using " + (analogMode?"ANALOG":"DIGITAL" + " mode"));
				}
				return;
			}
			if (index>=0) {
				int translatedIndex = Mapper.genericJoysticks[0].getDeviceDescriptor()==null?
						buttonMapOverlay[index]:
						(analogMode?buttonMapRealAnalog[index]:buttonMapRealDigital[index]);
				buttons[translatedIndex] = down;
				notifyChange();
			}
		}

		@Override
		public void sendMouseButton(MouseButton button, boolean down) {}

		@Override
		public boolean handleShortcut(ShortCut shortcut, boolean down) {
			switch(shortcut) {
			case EXIT: if (!down) uiQuitConfirm(); return true;
			case LOAD_STATE: if (!down) uiLoadState(); return true;
			case SAVE_STATE: if (!down) uiSaveState(); return true;
			case MENU : if (!down) openRetroBoxMenu(); return true;
			default:
				return false;
			}
		}
    }
}
