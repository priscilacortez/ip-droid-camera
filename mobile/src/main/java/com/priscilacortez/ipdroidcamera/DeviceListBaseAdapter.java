package com.priscilacortez.ipdroidcamera;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceListBaseAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> deviceArrayList;
    private LayoutInflater layoutInflater;

    public DeviceListBaseAdapter(Context context, ArrayList<BluetoothDevice> devices){
        deviceArrayList = devices;
        layoutInflater = LayoutInflater.from(context);
    }

    public int getCount(){
        return deviceArrayList.size();
    }

    public BluetoothDevice getItem(int position) {
        return deviceArrayList.get(position);
    }

    public long getItemId(int position){
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent){
        ViewHolder holder;

        if(convertView == null){
            convertView = layoutInflater.inflate(R.layout.device_row_layout, null);
            holder = new ViewHolder();
            holder.tvDeviceName = (TextView) convertView.findViewById(R.id.tv_device_name);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.tvDeviceName.setText(deviceArrayList.get(position).getName());

        return convertView;
    }

    static class ViewHolder {
        TextView tvDeviceName;
    }

}
