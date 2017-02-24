package com.priscilacortez.ipdroidcamera;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;

public class AcceptVideoStreamActivity extends AppCompatActivity {

    private Switch bluetoothSwitch;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothStreamApp appState;
    private ProgressDialog progressDialog;

    private final static int DISCOVERABILITY_TIME = 200;

    private final static int REQUEST_ENABLE_DISCOVERABILITY = 1;
    private String TAG = "Accept Video Stream";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept_video_stream);

        // Set the toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set up handler for setting up and managing bluetooth connections
        appState = (BluetoothStreamApp) getApplicationContext();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register a receiver to handle Bluetooth actions
        registerReceiver(Receiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        // set bluetooth server
        appState.setBluetoothAdapter(bluetoothAdapter);
        appState.startServer();

        // set discoverability on
        bluetoothSwitch = (Switch) findViewById(R.id.switch_bluetooth);
        bluetoothSwitch.setOnCheckedChangeListener(switchChangeListener);
        bluetoothSwitch.performClick();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                unregisterReceiver(Receiver);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setDiscoverability(int seconds){
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds);
        startActivityForResult(discoverableIntent, REQUEST_ENABLE_DISCOVERABILITY);

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == REQUEST_ENABLE_DISCOVERABILITY){
            if (resultCode != DISCOVERABILITY_TIME){
                bluetoothSwitch.performClick();
            }
        }
    }


    final OnCheckedChangeListener switchChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                setDiscoverability(DISCOVERABILITY_TIME);
            }
        }
    };


    private final BroadcastReceiver Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(TAG, action.toString());
            if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)){
                Bundle extras = intent.getExtras();
                String key = extras.keySet().iterator().next();
                // Check to see if it is no longer discoverable then allow user to click on the switch again
                if(extras.get(key).equals(BluetoothAdapter.SCAN_MODE_CONNECTABLE) || extras.get(key).equals(BluetoothAdapter.SCAN_MODE_NONE)){
                    bluetoothSwitch.setClickable(true);
                    bluetoothSwitch.performClick();
                } else if(extras.get(key).equals(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)){
                    bluetoothSwitch.setClickable(false);
                }
            }
        }
    };
}
