package com.priscilacortez.ipdroidcamera;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.util.UUID;
import android.app.Application;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.icu.util.Output;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.Log;

public class BluetoothStreamApp extends Application {

    // Debugging and Log constants
    private static String TAG = "Bluetooth Stream App";

    // Time between sending idle filler to confirm communication
    private final int minCommInterval = 900;
    // Time after which the communication is deemed dead
    private final int timeout = 3000;
    private long lastComm;

    // Member fields
    private ConnectionThread connectionThread;
    private AcceptThread acceptThread;
    private BluetoothThread bluetoothThread;
    private TimeoutThread timeoutThread;
    private Handler activityHandler;
    private int state;
    private boolean busy, stoppingConnection;
    private BluetoothAdapter bluetoothAdapter;

    // Constants to indicate message contents
    public static final int MSG_OK = 0;
    public static final int MSG_READ = 1;
    public static final int MSG_WRITE = 2;
    public static final int MSG_CANCEL = 3;
    public static final int MSG_CONNECTED = 4;
    public static final int MSG_TOAST = 5;

    // constants that indicate the current connection state
    private static final int STATE_NONE = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // General UUID constant
    private static String GENERAL_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    public BluetoothStreamApp(){
        state = STATE_NONE;
        activityHandler = null;
    }

    /**
     * Sets the bluetoothAdapter
     * @param bluetoothAdapter: BluetoothAdapter
     */
    public void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter){
        this.bluetoothAdapter = bluetoothAdapter;
    }

    /**
    * Sets the current active activity handler so messages could be sent
    * @param handler: The current activity handler
    * */
    public void setActivityHandler(Handler handler){
        activityHandler = handler;
    }

    /**
    * Sends a message tot he current activity registered to the activityHandler variable
    * @param type: Type of message, using the public MSG_* constants
    * @param value: Optional object to attach to message
    */
    private synchronized void sendMessage(int type, Object value){
        Log.d(TAG,"Sending message of type: " + type);
        if(activityHandler != null){
            activityHandler.obtainMessage(type, value).sendToTarget();
        }
    }

    /**
    * set the current state of the chat connection
    * @param newState: An integer defining the new connection state
    */
    private synchronized void setState(int newState){
        state = newState;
    }

    /**
    * Updates the communication time counter, use with the timeout thread to
    * check for broken connection.
    */
    private synchronized void updateLastComm(){
        lastComm = System.currentTimeMillis();
    }

    /**
     * Start the ConnectionThread to initiate a connection to a remote device
     * @param device
     *              The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device){
        Log.d(TAG,"Connecting to: " + device.getName());
        stoppingConnection = false;
        busy = false;

        // Cancel any thread currently running a connection
        closeAllThreads();

        setState(STATE_CONNECTING);

        // Start the thread to connect with the given device
        bluetoothThread = new BluetoothThread(device);
        bluetoothThread.start();

        // Start the timeout thread to check the connecting status
        //timeoutThread = new TimeoutThread();
        //timeoutThread.start();

    }

    public synchronized void startServer(){
        Log.d(TAG,"Starting server");
        stoppingConnection = false;
        busy = false;

        // Cancel any thread currently running a connection
        closeAllThreads();

        // Start the new server thread
        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    private void closeAllThreads(){
        if(bluetoothThread != null){
            bluetoothThread.cancel();
            bluetoothThread = null;
        }

        if(acceptThread != null){
            acceptThread.cancel();
            acceptThread = null;
        }

        if(connectionThread != null){
            connectionThread.cancel();
            connectionThread = null;
        }
    }

    /**
     * This thread runs during a connection with a remote device. It handles the
     * initial connection and all incoming and outgoing transmissions.
     */
    private class BluetoothThread extends Thread{

        private final BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public BluetoothThread(BluetoothDevice device){
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(GENERAL_UUID));
            } catch (IOException e){
                Log.e(TAG, "Could not get a bluetooth socket", e);
            }
            socket = tmp;
        }

        public void run(){
            Log.d(TAG,"BluetoothThread starting");
            // Connect to socket
            try {
                socket.connect();
            } catch (IOException e){
                // If the user didn't cancel the connection then it has failed (timeout)
                if(!stoppingConnection){
                    Log.e(TAG, "Could not connect to socket");
                    try{
                        socket.close();
                    } catch(IOException err){
                        Log.e(TAG, "Could not close the socket", err);
                    }
                    disconnect();
                }
                return;
            }

            // Connected
            setState(STATE_CONNECTED);
            // Send message to activity to inform of success
            sendMessage(MSG_CONNECTED, socket.getRemoteDevice().getName());

            // Keep listening to the InputStream while connected
            connectionThread = new ConnectionThread(socket);
            connectionThread.run();
        }

        public void cancel(){
            try{
                if (socket != null) {
                    socket.close();
                }

            } catch (IOException e){
                Log.e(TAG, "could not close the connection socket", e);
            }
        }
    }

    private class TimeoutThread extends Thread{
        public TimeoutThread(){
            updateLastComm();
        }

        public void run(){
            while(state == STATE_CONNECTING || state == STATE_CONNECTED){
                synchronized (BluetoothStreamApp.this){
                    // Filler hash to confirm communication with device when idle
                    if(System.currentTimeMillis() - lastComm > minCommInterval && !busy && state == STATE_CONNECTED){
                         write(null);
                    }

                    // Communication timed out
                    if(System.currentTimeMillis() - lastComm > timeout){
                        Log.e(TAG, "Timeout");
                        disconnect();
                        break;
                    }
                }

                // This thread should ot run all the time
                try{
                    Thread.sleep(50);
                } catch(InterruptedException e){
                    Log.e(TAG,"Timeout Thread interrupted", e);
                }
            }
        }
    };

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket serverSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try{
                String appName = getApplicationInfo().name;
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(appName, UUID.fromString(GENERAL_UUID));
            } catch (IOException e){
                Log.e(TAG, "Socket's listening() method failed", e);
            }
            serverSocket = tmp;
        }

        public void run(){
            Log.d(TAG,"AcceptThread starting");
            BluetoothSocket socket = null;
            while(true){
                try{
                    socket = serverSocket.accept();
                } catch (IOException e){
                    Log.e(TAG, "Socket's accept() method failed",e);
                    break;
                }

                if (socket != null){
                    // TODO: PREFORM WORK ASSOCIATED WITH THE CONNECTION IN A SEPARATE THREAD

                    try {
                        connectionThread = new ConnectionThread(socket);
                        connectionThread.run();
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG,"Could not close server socket",e);
                    }
                    break;
                }
            }
        }

        public void cancel(){
            try{
                serverSocket.close();
            } catch (IOException e){
                Log.e(TAG, "Could not close the connection socket", e);
            }
        }
    }

    private class ConnectionThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private byte[] buffer;

        public ConnectionThread(BluetoothSocket socket){
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get in/output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e){
                Log.e(TAG, "Error when getting In/Output Streams",e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run(){
            Log.d(TAG,"ConnectionThread started");
            sendMessage(MSG_CONNECTED, socket.getRemoteDevice().getName());
            buffer = new byte[1024];
            int numBytes;

            // Keep listening to the InputStream until an exception occurs
            while(true){
                Log.d(TAG,"Listening to inputStream");
                try{
                    // Read from InputStream
                    numBytes = inputStream.read(buffer);
                    // TODO: send obtained bytes to the UI activity
                    Message readMsg = activityHandler.obtainMessage(
                            MSG_READ, numBytes, -1, buffer
                    );
                    readMsg.sendToTarget();
                } catch (IOException e){
                    Log.d(TAG, "Input stream was disconnected", e);
                    disconnect();
                    break;
                }
            }
        }

        public boolean write(byte[] bytes){
            try{
                outputStream.write(bytes);

                // Share the sent message with the UI activity
                Message writtenMsg = activityHandler.obtainMessage(
                        MSG_WRITE, -1, -1, buffer
                );
                writtenMsg.sendToTarget();
                return true;
            } catch(IOException e){

                // Send a failure message back to the activity
                Message writeErrorMsg = activityHandler.obtainMessage(MSG_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast","Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                activityHandler.sendMessage(writeErrorMsg);
            }
            return false;
        }

        public void cancel(){
            try{
                socket.close();
            } catch (IOException e){
                Log.e(TAG, "Could not close the connection socket",e);
            }
        }

    }

    public boolean write(String out){
        // The device hasn't finished processing last command, reset commands ("r") it always get sent
        if(busy && !out.equals(out)){
            return false;
        }
        busy = true;

        ConnectionThread r;
        synchronized(this){
            // Make sure the connection is live
            if(state != STATE_CONNECTED){
                return false;
            }
            r = connectionThread;
        }
        return r.write(out.getBytes());
    }

    /**
     * Stop all threads
     */
    public synchronized void disconnect(){
        Log.d(TAG,"Disconnecting");
        // Do not stop twice
        if(!stoppingConnection){
            stoppingConnection = true;
            if(bluetoothThread != null){
                bluetoothThread.cancel();
                bluetoothThread = null;
            }
            if(acceptThread != null){
                acceptThread.cancel();
                acceptThread = null;
            }
            if(connectionThread != null){
                connectionThread.cancel();
                connectionThread = null;
            }
            setState(STATE_NONE);
            sendMessage(MSG_CANCEL, "Connection ended");
        }
    }

    private final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener(){

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch(which){
                case DialogInterface.BUTTON_POSITIVE:

            }
        }
    };

}
