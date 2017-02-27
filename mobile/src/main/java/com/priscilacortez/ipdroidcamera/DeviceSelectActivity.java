package com.priscilacortez.ipdroidcamera;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class DeviceSelectActivity extends AppCompatActivity {

    private ArrayList<BluetoothDevice> availableDevicesList, pairedDevicesList;
    private HashSet<String> availableDevicesSet;
    private ListView availableDevicesListView, pairedDevicesListView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothStreamApp appState;
    private MenuItem scanActionButton;
    private DeviceListBaseAdapter pairedDevicesListAdapter;
    private DeviceListBaseAdapter availableDevicesListAdapter;
    private ProgressDialog progressDialog;

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int REQUEST_ACCESS_COARSE_LOCATION = 2;
    private String TAG = "Device Select Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // get spinner in upper right corner
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_select);

        // set custom toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Setup Bluetooth devices list with custom rows
        pairedDevicesListView = (ListView) findViewById(R.id.lv_paired_devices);
        pairedDevicesList = new ArrayList<BluetoothDevice>();
        pairedDevicesListAdapter = new DeviceListBaseAdapter(this, pairedDevicesList);
        pairedDevicesListView.setAdapter(pairedDevicesListAdapter);
        pairedDevicesListView.setOnItemClickListener(deviceClickListener);


        availableDevicesListView = (ListView) findViewById(R.id.lv_available_devices);
        availableDevicesList = new ArrayList<BluetoothDevice>();
        availableDevicesSet = new HashSet<String>();
        availableDevicesListAdapter = new DeviceListBaseAdapter(this, availableDevicesList);
        availableDevicesListView.setAdapter(availableDevicesListAdapter);
        availableDevicesListView.setOnItemClickListener(deviceClickListener);

        appState = (BluetoothStreamApp) getApplicationContext();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevicesSet = bluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevicesSet){
            pairedDevicesList.add(device);
        }

        // Register a receiver to handle Bluetooth actions
        registerReceiver(Receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(Receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        registerReceiver(Receiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

    }

    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.bluetooth_scan_action,menu);
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
            case android.R.id.home:
                appState.disconnect();
                finish();
                return true;
            default:
                // User's action was not recognized
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    final OnItemClickListener deviceClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            bluetoothAdapter.cancelDiscovery();
            BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(position);

            // TODO: CONNECTION DIALOG AND MAKE IT SO THAT THE CONNECTION CAN BE CANCELLED
            // Show connection dialog and allow connection to be cancelled
            progressDialog = ProgressDialog.show(DeviceSelectActivity.this, "", "Establishing connection...", false, true);
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    progressDialog.dismiss();
                    appState.disconnect();
                    // TODO: LOGS
                }
            });
            appState.connect(device);
        }
    };

    public void scanDevices(){

        // Prevent phone without bluetooth from using application
        Log.e(TAG, Boolean.toString(hasBluetooth()));
        if(!hasBluetooth()){
            finish();
            return;
        }

        // Show search progress spinner
        // TODO: progress bar

        // Disable scan action button\
        scanActionButton.setTitle(getString(R.string.scanning));
        scanActionButton.setEnabled(false);

        // Remove title for available devices
        findViewById(R.id.tv_available_devices).setVisibility(View.GONE);
        findViewById(R.id.tv_paired_devices).setVisibility(View.GONE);

        pairedDevicesList.clear();
        pairedDevicesListAdapter.notifyDataSetChanged();

        // Show already paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0){
            findViewById(R.id.tv_paired_devices).setVisibility(View.VISIBLE);
            for(BluetoothDevice device: pairedDevices){
                pairedDevicesList.add(device);
            }

            pairedDevicesListAdapter.notifyDataSetChanged();
        }


        availableDevicesSet.clear();
        availableDevicesList.clear();
        availableDevicesListAdapter.notifyDataSetChanged();

        // Request permission on runtime
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_ACCESS_COARSE_LOCATION);


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
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                // Found a device in range
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // If not paired, has a name and not already in list, add to list
                if(device.getBondState() != BluetoothDevice.BOND_BONDED && device.getName() != null && !availableDevicesSet.contains(device.getAddress())){
                    availableDevicesList.add(device);
                    availableDevicesSet.add(device.getAddress());
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

    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(Receiver);
    }


}
