package com.priscilacortez.ipdroidcamera;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.jar.Attributes;

/**
 * Created by priscilacortez on 2/27/17.
 */

public class BluetoothConnectionService {

    private static final String TAG = "BluetoothConnectionService";

    private static final UUID SECURE_UUID = UUID.fromString("f8386efc-fe95-11e6-bc64-92361f002671");
    private static final String NAME = "IPDroidCamera";

    // Member fields
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private int state;
    private int newState;

    // Constants
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public BluetoothConnectionService(Context context, Handler handler){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        newState = state;
        this.handler = handler;
    }

    public synchronized void start(){

        // Cancel any thread attempting to make a connection
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if(connectedThread != null){
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to listen to a BluetoothServerSocket
        if(acceptThread == null ){
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice device){
        Log.d(TAG, "Connected to: "+ device.getName());

        // Cancel any thread attempting to make a connection
        if(state == STATE_CONNECTING){
            if(connectThread != null){
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if(connectedThread != null){
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread currently running a connection
        connectThread = new ConnectThread(device);
        connectThread.start();
        // Update UI title
        updateUserInterfaceTitle(false);

    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        Log.d(TAG, "Connected");
        stopAllThreads();

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        handler.sendMessage(msg);
        // Update UI title
        updateUserInterfaceTitle(true);
    }

    public int getState(){
        return state;
    };

    public void write(byte[] out){
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this){
            if (state != STATE_CONNECTED) return;
            r = connectedThread;
        }

        // Perform the write unsynchronized
        r.write(out);
    }

    public synchronized void stopAllThreads(){
        // Cancel any thread attempting to make a connection
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if(connectedThread != null){
            connectedThread.cancel();
            connectedThread = null;
        }

        if(acceptThread != null){
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    public synchronized void stop(){
        Log.d(TAG, "Stop");

        stopAllThreads();

        state = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle(false);
    }

    private synchronized void updateUserInterfaceTitle(boolean server){
        state = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + newState + " -> " + state);
        newState = state;

        // Give teh new state the Handler so the UI activity can update
        handler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, newState, -1).sendToTarget();
    }

    private void connectionFailed(){
        // Send a failure message back to the Activity
        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        state = STATE_NONE;
        updateUserInterfaceTitle(false);

        // Start the service over to restart listening mode
        BluetoothConnectionService.this.start();
    }

    private void connectionLost(){
        // Send a failure message back to the Activity
        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        state = STATE_NONE;
        updateUserInterfaceTitle(false);

        // Start the service over to restart listening mode
        BluetoothConnectionService.this.start();
    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket serverSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try{
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, SECURE_UUID);
            } catch (IOException e){
                Log.e(TAG, "listen() failed", e);
            }

            serverSocket = tmp;
            state = STATE_LISTEN;
        }

        public void run(){
            Log.d(TAG, "Begin AcceptThread" + this);
            setName("AcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while(state != STATE_CONNECTED){
                try{
                    // Will only return on a successful connection or exception
                    socket = serverSocket.accept();
                } catch (IOException e){
                    Log.e(TAG, "accept() failed" ,e);
                    break;
                }

                // If a connection was accepted
                if(socket != null){
                    synchronized (BluetoothConnectionService.this){
                        switch (state){
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation is normal. Start the connected thread
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e){
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel(){
            Log.d(TAG, "cancel " + this);
            try {
                serverSocket.close();
            } catch (IOException e){
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    private class ConnectThread extends Thread{
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device){
            this.device = device;
            BluetoothSocket tmp = null;

            try{
                tmp = device.createRfcommSocketToServiceRecord(SECURE_UUID);
            } catch (IOException e){
                Log.e(TAG, "create() failed", e);
            }

            socket = tmp;
            state = STATE_CONNECTING;
        }

        public void run(){
            Log.i(TAG, "Begin ConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down connection
            bluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try{
                socket.connect();
            } catch (IOException e){
                try{
                    socket.close();
                } catch (IOException e2){
                    Log.e(TAG, "unable to close() socket during connection failure.",e);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we are done
            synchronized (BluetoothConnectionService.this){
                connectThread = null;
            }

            connected(socket,device);
        }

        public void cancel(){
            try{
                socket.close();
            }catch (IOException e){
                Log.e(TAG,"cancel() failed",e);
            }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;

        public ConnectedThread(BluetoothSocket socket){
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e){
                Log.e(TAG, "tmp sockets not created",e);
            }

            inStream = tmpIn;
            outStream = tmpOut;
            state = STATE_CONNECTED;
        }

        public void run(){
            Log.i(TAG,"Begin ConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while(state == STATE_CONNECTED){
                try{
                    bytes = inStream.read(buffer);
                    handler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e){
                    Log.e(TAG,"disconnected",e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer){
            try{
                outStream.write(buffer);

                handler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e){
                Log.e(TAG, "Exception during write()",e);
            }
        }

        public void cancel(){
            try{
                socket.close();
            } catch (IOException e){
                Log.e(TAG, "close() of connect socket failed",e);
            }
        }

    }
}
