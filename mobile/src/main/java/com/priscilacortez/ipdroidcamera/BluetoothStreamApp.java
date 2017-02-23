package com.priscilacortez.ipdroidcamera;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
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
    private BluetoothThread bluetoothThread;
    private TimeoutThread timeoutThread;
    private Handler activityHandler;
    private int state;
    private boolean busy, stoppingConnection;

    // Constants to indicate message contents
    public static final int MSG_OK = 0;
    public static final int MSG_READ = 1;
    public static final int MSG_WRITE = 2;
    public static final int MSG_CANCEL = 3;
    public static final int MSG_CONNECTED = 4;

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
     *TODO: COME BACK
     */
    public synchronized void connect(BluetoothDevice device){
        stoppingConnection = false;
        busy = false;

        // Cancel any thread currently running a connection
        if(bluetoothThread != null){
            bluetoothThread.cancel();
            bluetoothThread = null;
        }

        setState(STATE_CONNECTING);

        // Start the thread to connect with the given device
        bluetoothThread = new BluetoothThread(device);
        bluetoothThread.start();

        // Start the timeout thread to check the connecting status
        timeoutThread = new TimeoutThread();
        timeoutThread.start();

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
            sendMessage(MSG_CONNECTED, null);

            // Get BluetoothSocket input and ouput streams
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e){
                disconnect();
                Log.e(TAG, "Failed to get streams", e);
            }

            byte[] buffer = new byte[1024];
            byte ch;
            int bytes;
            String input;

            // Keep listening to the InputStream while connected
            // TODO: HERE I WANT SEND STUFF INSTEAD OF READING IT
        }

        public boolean write(String out){
            if(outputStream == null){
                return false;
            }

            try{
                if(out != null){
                    // Show sent message to the active activity
                    sendMessage(MSG_WRITE, out);
                    outputStream.write(out.getBytes());
                } else {
                    // This is a special case for the filler
                    outputStream.write(0);
                }
                // End packet with a new line
                outputStream.write('\n');
                return true;
            } catch (IOException e){
                Log.e(TAG, "Could not write", e);
            }
            return false;
        }

        public void cancel(){
            try{
                if(inputStream != null){
                    inputStream.close();
                }

                if(outputStream != null){
                    outputStream.close();
                }

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
    }

    public boolean write(String out){
        // The device hasn't finished processing last command, reset commands ("r") it always get sent
        if(busy && !out.equals(out)){
            return false;
        }
        busy = true;

        BluetoothThread r;
        synchronized(this){
            // Make sure the connection is live
            if(state != STATE_CONNECTED){
                return false;
            }
            r = bluetoothThread;
        }
        return r.write(out);
    }

    /**
     * Stop all threads
     */
    public synchronized void disconnect(){
        // Do not stop twice
        if(!stoppingConnection){
            stoppingConnection = true;
            if(bluetoothThread != null){
                bluetoothThread.cancel();
                bluetoothThread = null;
            }
            setState(STATE_NONE);
            sendMessage(MSG_CANCEL, "Connection ended");
        }
    }

}
