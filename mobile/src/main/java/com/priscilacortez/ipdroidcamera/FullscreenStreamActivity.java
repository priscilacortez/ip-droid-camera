package com.priscilacortez.ipdroidcamera;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenStreamActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, Handler.Callback {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    CameraBridgeViewBase openCvCameraView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            openCvCameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private static BluetoothStreamApp appState;
    protected boolean preventCancel;
    private static String TAG = "FullStreamActivity";

    private Mat imageSceneRgba;
    private Mat imageSceneGray;

    private Mat imageObjectGray;

    int functionMode = 0;

    private boolean isObjectSelected = false;
    private boolean drawSelectedRect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen_stream);

        // set action bar
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        openCvCameraView = (CameraBridgeViewBase) findViewById(R.id.stream_surface_view);
        openCvCameraView.setCvCameraViewListener(this);

        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
            openCvCameraView.enableFpsMeter();
            openCvCameraView.enableView();
        }

        appState = (BluetoothStreamApp) getApplicationContext();

        // Set up the user interaction to manually show or hide the system UI.
        openCvCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        openCvCameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    protected void disconnect() {
        // Disconnect from the Bluetooth device
        appState.disconnect();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case BluetoothStreamApp.MSG_OK:
                // When a child activity returns safely
                break;
            case BluetoothStreamApp.MSG_CANCEL:
                // When a child activity returns after being canceled cancel this activity
                setResult(BluetoothStreamApp.MSG_CANCEL, new Intent());
                finish();
                break;
        }
        return false;
    }

    @Override
    public void onActivityResult(int RequestCode, int resultCode, Intent data){
        Message.obtain(new Handler(this), resultCode).sendToTarget();
    }

    @Override
    protected void onResume() {
        // Set the handler to receive messages from the main application class
        appState.setActivityHandler(new Handler(this));
        preventCancel = false;
        super.onResume();
        openCvCameraView.enableView();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (openCvCameraView != null){
            openCvCameraView.disableView();
        }
        Log.e(TAG,"DESTROYED");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Behave as if the back button was clicked
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        disconnect();
        super.onBackPressed();
        // Pressing the back button quits the activity and informs the parent activity
        finishActivity(BluetoothStreamApp.MSG_OK);

    }

    @Override
    public void finish() {
        // Remove the handler from the main application class
        appState.setActivityHandler(null);
        super.finish();
    }

    @Override
    protected void onPause() {
        // Pausing an activity isn't allowed, unless it has been prevented
        if (!preventCancel) {
            // Tell itself to cancel
            Message.obtain(new Handler(this), BluetoothStreamApp.MSG_CANCEL).sendToTarget();
        }
        super.onPause();

        if(openCvCameraView != null){
            openCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        imageSceneRgba = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        imageSceneRgba.release();
        if(imageSceneGray != null){
            imageSceneGray.release();
            imageSceneGray = null;
        }
        if (imageObjectGray != null){
            imageObjectGray.release();
            imageObjectGray = null;
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // input frame from RGBA format
        imageSceneRgba = inputFrame.rgba();
        imageSceneGray = inputFrame.gray();

        // TODO: OBJECT DETECTION

        return inputFrame.rgba();
    }
}
