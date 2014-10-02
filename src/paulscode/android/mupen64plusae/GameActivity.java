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

import paulscode.android.mupen64plusae.persistent.UserPrefs;
import retrobox.paulscode.android.mupen64plus.free.R;
import retrobox.vinput.GenericGamepad;
import retrobox.vinput.GenericGamepad.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrobox.vinput.overlay.GamepadController;
import retrobox.vinput.overlay.GamepadView;
import retrobox.vinput.overlay.Overlay;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

public class GameActivity extends Activity
{
    private final GameLifecycleHandler mLifecycleHandler;
    private GameMenuHandler mMenuHandler;
    
    // Input controls for RetroBox
    public static final Overlay overlay = new Overlay();
	private static Mapper mapper;
	private VirtualInputDispatcher vinputDispatcher;
    private GamepadView gamepadView;
    private GamepadController gamepadController;
    private View mSurfaceView;
    
    public GameActivity()
    {
        mLifecycleHandler = new GameLifecycleHandler( this );
    }
    
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        mMenuHandler.onCreateOptionsMenu( menu );
        return super.onCreateOptionsMenu( menu );
    }
    
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        mMenuHandler.onOptionsItemSelected( item );
        return super.onOptionsItemSelected( item );
    }
    
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        UserPrefs userPrefs = new UserPrefs( this );
        mMenuHandler = new GameMenuHandler( this, userPrefs.manualSaveDir, userPrefs.selectedGameAutoSavefile );

        mLifecycleHandler.onCreateBegin( savedInstanceState );
        super.onCreate( savedInstanceState );
        mLifecycleHandler.onCreateEnd( savedInstanceState );
        
        if (MainActivity.fromRetroBox) {
        	gamepadController = new GamepadController();
        	vinputDispatcher = new VirtualInputDispatcher();
            mapper = new Mapper(MainActivity.publicIntent, vinputDispatcher);
            Mapper.initGestureDetector(this);
            gamepadView = new GamepadView(this, overlay);
            
        	mSurfaceView = findViewById(R.id.gameSurface);
            ViewGroup root = (ViewGroup)findViewById(R.id.root);
        	setupGamepadOverlay(root);
        }
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
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
	public boolean dispatchTouchEvent(MotionEvent ev) {
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
	
	private void setupGamepadOverlay(final ViewGroup root) {
		ViewTreeObserver observer = root.getViewTreeObserver();
		observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				int w = root.getWidth();
				int h = root.getHeight();
				if (needsOverlay()) {
					Log.d("OVERLAY", "set dimensions " + w + "x" + h);
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

    	
    	
    	public static final int NUM_N64_BUTTONS = 16;

    	
    	int buttonMap[] = {
    			DPD_U, DPD_D, DPD_L, DPD_R,
    			BTN_A, BTN_B, CPD_U, CPD_D,
    			BTN_L, BTN_R, BTN_Z, BTN_Z,
    			BTN_L, BTN_R, START, START
    	};
    	
    	public int originCodes[] = {
    			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    			KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
    			KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
    			KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START
    		};

    	
    	public boolean[] buttons = new boolean[NUM_N64_BUTTONS];
    	
    	private static final int ANALOG_MAX_X = 80;
    	private static final int ANALOG_MAX_Y = 80;
    	int analogX = 0;
    	int analogY = 0;

    	
    	@Override
    	public void sendAnalog(GenericGamepad.Analog index, double x, double y) {
    		if (index!=Analog.LEFT) return;
    		
    		int newX = (int)(ANALOG_MAX_X * x);
    		int newY = (int)(ANALOG_MAX_Y * y);
    		
    		if (newX == analogX && newY == analogY) return;
    		analogX = newX;
    		analogY = newY;
    		notifyChange();
    	};
    	
    	private void notifyChange() {
            CoreInterfaceNative.setControllerState( 0 /*player*/, buttons, analogX, analogY);
    	}
    	
		@Override
		public void sendKey(int keyCode, boolean down) {
			int index = Mapper.genericJoysticks[0].getOriginIndex(keyCode);
			if (index>=0) {
				int translatedIndex = buttonMap[index];
				buttons[translatedIndex] = down;
				notifyChange();
			}
		}

		@Override
		public void sendMouseButton(MouseButton button, boolean down) {}

		@Override
		public boolean handleShortcut(ShortCut shortcut, boolean down) {
			/*
			switch(shortcut) {
			case EXIT: if (!down) uiQuitConfirm(); return true;
			case LOAD_STATE: if (!down) uiLoadState(); return true;
			case SAVE_STATE: if (!down) uiSaveState(); return true;
			case MENU : if (!down) openOptionsMenu(); return true;
			default:
				return false;
			}
			*/
			return false;
		}
    }
}
