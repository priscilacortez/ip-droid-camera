package com.priscilacortez.ipdroidcamera;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Set;
import java.util.jar.*;

public class DeviceSelectActivity extends AppCompatActivity {

    private ArrayList<BluetoothDevice> availableDevicesList, pairedDevicesList;
    private ListView availableDevicesListView, pairedDevicesListView;
    private BluetoothAdapter bluetoothAdapter;
    private MenuItem scanActionButton;
    private Menu menu;
    private DeviceListBaseAdapter pairedDevicesListAdapter;
    private DeviceListBaseAdapter availableDevicesListAdapter;

    private final static int REQUEST_ENABLE_BT = 1;

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
        pairedDevicesListView = (ListView) findViewById(R.id.lv_paired_devices);
        pairedDevicesList = new ArrayList<BluetoothDevice>();
        pairedDevicesListAdapter = new DeviceListBaseAdapter(this, pairedDevicesList);
        pairedDevicesListView.setAdapter(pairedDevicesListAdapter);
        // TODO: ADD setOnItemClickListener

        availableDevicesListView = (ListView) findViewById(R.id.lv_available_devices);
        availableDevicesList = new ArrayList<BluetoothDevice>();
        availableDevicesListAdapter = new DeviceListBaseAdapter(this, availableDevicesList);
        availableDevicesListView.setAdapter(availableDevicesListAdapter);
        // TODO: ADD setOnItemClickListener

        // TODO: Setup handler for setting up and managing bluetooth connections

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevicesSet = bluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevicesSet){
            pairedDevicesList.add(device);
        }

        // Register a receiver to handle Bluetooth actions
        registerReceiver(Receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(Receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

    }

    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bluetooth_scan_action,menu);
        this.menu = menu;
        this.scanActionButton = menu.findItem(R.id.action_scan);

        // After these toolbar has been initialized, begin scanning for devices
        scanDevices();

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
        // TODO: Fix progress bar
        setProgressBarIndeterminateVisibility(true);

        // Disable scan action button\
        scanActionButton.setTitle(getString(R.string.scanning));
        scanActionButton.setEnabled(false);

        // Remove title for available devices
        findViewById(R.id.tv_available_devices).setVisibility(View.GONE);

        pairedDevicesList.clear();
        pairedDevicesListAdapter.notifyDataSetChanged();

        // Show already paired devices in the upper list
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0){
            findViewById(R.id.tv_paired_devices).setVisibility(View.VISIBLE);
            for(BluetoothDevice device: pairedDevices){
                pairedDevicesList.add(device);
            }

            pairedDevicesListAdapter.notifyDataSetChanged();
        }

        availableDevicesList.clear();
        availableDevicesListAdapter.notifyDataSetChanged();

        bluetoothAdapter.startDiscovery();
    }

    public boolean hasBluetooth(){

        // Check to see if bluetooth is supported
        if (bluetoothAdapter == null){
            return false;
        }

        // Check to see if bluetooth is on
        if (!bluetoothAdapter.isEnabled()){
            // If not, ask permission to turn it on
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        return true;
    }

    // Add found device to the devices list
    private final BroadcastReceiver Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("IN RECEIVER");
            String action = intent.getAction();
            System.out.println("INTENT: " + action );
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                // Found a device in range
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // If not paired add to list
                if(device.getBondState() != BluetoothDevice.BOND_BONDED){
                    availableDevicesList.add(device);
                    availableDevicesListAdapter.notifyDataSetChanged();
                    findViewById(R.id.tv_available_devices).setVisibility(View.VISIBLE);
                }
            } else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                // When finished (timeout) remove the progress indicated and re-enable button
                // TODO: PROGRESS BAR STUFF

                scanActionButton.setTitle(getString(R.string.action_scan));
                scanActionButton.setEnabled(true);
            }
        }
    };


}
