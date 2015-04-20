package com.ece251.rcandroid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

/**
 * Created by shane on 2/15/2015.
 */
public class BluetoothActivity extends Activity {

    private Button bluetoothOn;
    private Button bluetoothScan;
    private ArrayAdapter<String> btArrayAdapter;
    private ListView listDevicesFound;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothAdapter myBluetoothAdapter;

    public static final String BLUETOOTH_ID = "com.ece251.rcandroid.bluetooth_id";
    public static final String BLUETOOTH_ID_SELECTED = "com.ece251.rcandroid.bluetooth_id_selected";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_layout);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        bluetoothOn= (Button)findViewById(R.id.btnOn);
        bluetoothScan = (Button)findViewById(R.id.btnScan);
        listDevicesFound = (ListView)findViewById(R.id.devicesfound);

        btArrayAdapter = new ArrayAdapter<String>(BluetoothActivity.this, android.R.layout.simple_list_item_1);

        listDevicesFound.setAdapter(btArrayAdapter);

        myBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(myBluetoothAdapter==null){
            Toast.makeText(getApplicationContext(), "Your device does not support Bluetooth", Toast.LENGTH_SHORT).show();
        }

        registerReceiver(myBluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        Log.i("BluetoothActivity", "registerReceiver");

        if (myBluetoothAdapter.isEnabled()) {
            bluetoothOn.setText("Switch Off");
            scanDevices();
        } else {
            bluetoothOn.setText("Switch On");
        }

        bluetoothOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!myBluetoothAdapter.isEnabled()) {
                    Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(turnOn, 0);
                    bluetoothOn.setText("Switch Off");
                    //Toast.makeText(getApplicationContext(),"Start bluetooth",Toast.LENGTH_LONG).show();
                } else {
                    myBluetoothAdapter.disable();
                    bluetoothOn.setText("Switch On");
                    //Toast.makeText(getApplicationContext(), "Bluetooth already on",Toast.LENGTH_LONG).show();
                }
            }
        });

        bluetoothScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDevices();
            }
        });

        listDevicesFound.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,int position, long id) {
                String selectedFromList =(String) (listDevicesFound.getItemAtPosition(position));
                Intent data = new Intent();
                data.putExtra(BLUETOOTH_ID_SELECTED,selectedFromList);
                setResult(RESULT_OK,data);
                myBluetoothAdapter.cancelDiscovery();
                Log.i("BluetoothActivity", "unregisterReceiver");
                finish();
            }

        });


    }

    private void scanDevices() {
        Toast.makeText(getApplicationContext(),"Scanning Devices",Toast.LENGTH_SHORT).show();
        listDevicesFound.setAdapter(btArrayAdapter);
        myBluetoothAdapter.cancelDiscovery();
        btArrayAdapter.clear();
        myBluetoothAdapter.startDiscovery();
    }

    private final BroadcastReceiver myBluetoothReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                btArrayAdapter.notifyDataSetChanged();
            }
        }};

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        myBluetoothAdapter.cancelDiscovery();
        unregisterReceiver(myBluetoothReceiver);
        Log.i("BluetoothActivity", "unregisterReceiver");
    }




}


