package com.priscilacortez.ipdroidcamera;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;


public class DeviceTypeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_type);

        Button clientButton = (Button) findViewById(R.id.start_client_bt);
        Button serverButton = (Button) findViewById(R.id.start_server_bt);

        clientButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent clientIntent = new Intent(DeviceTypeActivity.this, DeviceSelectActivity.class);
                DeviceTypeActivity.this.startActivity(clientIntent);
            }
        });

        serverButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serverIntent = new Intent(DeviceTypeActivity.this, AcceptVideoStreamActivity.class);
                DeviceTypeActivity.this.startActivity(serverIntent);
            }
        });
    }
}
