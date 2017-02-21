package com.priscilacortez.ipdroidcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;

/**
 * Created by priscilacortez on 2/20/17.
 */

public class SendVideoStreamActivity extends Activity implements Handler.Callback {

    private static BluetoothStreamApp appState;
    protected boolean preventCancel;
    private static String TAG;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        // Launch when the activity is created
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        appState = (BluetoothStreamApp) getApplicationContext();
        System.out.println("IN SEND VIDEO STREAM ACTIVITY");
    }

    protected boolean write(String message){
        // Send command to the Bluetooth device
        return appState.write(message);
    }

    protected void disconnect(){
        // Disconnect from the Bluetooth device
        appState.disconnect();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what){
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
    protected void onActivityResult(int RequestCode, int resultCode, Intent data){
        Message.obtain(new Handler(this), resultCode).sendToTarget();
    }

    @Override
    protected void onResume(){
        // This is called when the activity is resumed
        TAG = getLocalClassName();
        Log.i(TAG, "Set handler");
        // Set the handler to receive messages from the main application class
        appState.setActivityHandler(new Handler(this));
        preventCancel = false;
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if(item.getItemId() == android.R.id.home){
            // Behave as if the back button was clicked
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();
        // Pressing the back button quits the activity and informs the parent activity
        setResult(BluetoothStreamApp.MSG_OK, new Intent());
    }

    @Override
    public void finish(){
        // Remove the handler from the main application class
        appState.setActivityHandler(null);
        super.finish();
    }

    @Override
    protected void onPause(){
        // Pausing an activity isn't allowed, unless it has been prevented
        if(!preventCancel){
            // Tell itself to cancel
            Message.obtain(new Handler(this), BluetoothStreamApp.MSG_CANCEL).sendToTarget();
        }
        super.onPause();
    }


}
