package com.priscilacortez.ipdroidcamera;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
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

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int REQUEST_ACCESS_COARSE_LOCATION = 2;
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

        // set discoverability on
        bluetoothSwitch = (Switch) findViewById(R.id.switch_bluetooth);
        bluetoothSwitch.setOnCheckedChangeListener(switchChangeListener);
        bluetoothSwitch.performClick();
    }

    public boolean onCreateOptionsMenu(Menu menu){
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.e(TAG, String.valueOf(item.getItemId()));
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    final OnCheckedChangeListener switchChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            if (isChecked){
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
                startActivity(discoverableIntent);
                bluetoothSwitch.setClickable(false);
            }
        }
    };
}
