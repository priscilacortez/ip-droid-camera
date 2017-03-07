package com.priscilacortez.ipdroidcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Created by priscilacortez on 2/20/17.
 */

public class StreamActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, Handler.Callback {

    private static BluetoothStreamApp appState;
    protected boolean preventCancel;
    private static String TAG = "StreamActivity";

    private Mat imageSceneRgba;
    private Mat imageSceneGray;

    private Mat imageObjectGray;

    int functionMode = 0;

    private boolean isObjectSelected = false;
    private boolean drawSelectedRect = false;

    private CameraBridgeViewBase openCvCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Launch when the activity is created
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_stream);

        // Create toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

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
    }

    protected boolean write(String message) {
        // Send command to the Bluetooth device
        return appState.write(message);
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
    protected void onActivityResult(int RequestCode, int resultCode, Intent data) {
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

    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface.SUCCESS:
                    openCvCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
            }
        }
    };
}
