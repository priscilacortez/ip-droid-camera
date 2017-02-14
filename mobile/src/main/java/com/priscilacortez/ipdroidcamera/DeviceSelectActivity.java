package com.priscilacortez.ipdroidcamera;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

public class DeviceSelectActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // get spinner in upper right corner
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        // set custom toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup Bluetooth devices list with custom rows

        // Register a receiver to handle Bluetooth actions

        // Setup action button to search for devices


        scanDevices();

    }

    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bluetooth_scan_action,menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_scan:
                scanDevices();
                return true;
            default:
                // User's action was not recognized
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    public void scanDevices(){

        // Prevent phone without bluetooth from using application
        if(!hasBluetooth()){
            finish();
            return;
        }

        // Show search progress spinner
        setProgressBarIndeterminateVisibility(true);
        // Disable button
        System.out.println("made it");

    }

    public boolean hasBluetooth(){
        return true;
    }
}
