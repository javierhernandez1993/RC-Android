package com.ece251.rcandroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.SyncStateContract;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity implements SensorEventListener {

    AnimationDrawable wheelAnimationBackward;
    AnimationDrawable wheelAnimationForward;
    AnimationDrawable wheelAnimationLeft;
    AnimationDrawable wheelAnimationRight;
    ImageView rcAndroid;

    char motor_control_code;
    boolean rmotor_forward,lmotor_forward,rc_on,rc_sit,landscape;
    boolean prf,plf,prr,plr;
    int rmotor_speed,lmotor_speed;
    int numSonar,sAngle,sData;
    int[] sonarData;
    public int mwidth,mheight;
    float r,theta,phi,lastTime,last_rms,lastUpdate;
    String mBluetooth_ID;
    UUID my_UUID = UUID.randomUUID();

    private float[] pla1 = new float[3];
    private float[] pla2 = new float[3];
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];

    private Button mOnOffButton;
    private FrameLayout sonarView;
    private GestureDetector gestureDetector;

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;

    private ConnectBTDevice btDevice;
    private Thread background;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        rcAndroid = (ImageView) findViewById(R.id.imageRC);
        rcAndroid.setBackgroundResource(R.drawable.forward); //left is a drawable XML file
        wheelAnimationForward = (AnimationDrawable) rcAndroid.getBackground();
        //wheelAnimationForward.start();

        gestureDetector = new GestureDetector(new SwipeGestureDetector());
        mOnOffButton = (Button)findViewById(R.id.onoff_button);
        sonarView = (FrameLayout) findViewById(R.id.imageView);

        ShowSonar();

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter==null){
            Toast.makeText(getApplicationContext(), "Your device does not support Bluetooth", Toast.LENGTH_LONG).show();
        }

        btDevice = new ConnectBTDevice();

        background = new Thread(btDevice);

        mOnOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rc_on && rc_sit) {
                    mOnOffButton.setText("RC Off");
                    rc_sit = false;
                } else if (rc_on) {
                    mOnOffButton.setText("RC Sit");
                    disableRC();
                } else {
                    mOnOffButton.setText("RC On");
                    rc_sit = true;
                    rc_on = true;
                }
            }
        });


    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
//        btDevice.cancel();
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        mBluetoothAdapter.cancelDiscovery();
    }

    public void initialize() {
        mwidth = 500;
        mheight = 500;
        last_rms = 100;
        rmotor_forward = lmotor_forward = true;
        rc_on = rc_sit = landscape= false;
        rmotor_speed = lmotor_speed = 0;
        motor_control_code = 0;
        prf = plf = prr = plr = false;
        lastTime = 0;
        lastUpdate = 0;
        mBluetooth_ID = "";
        for (int i=0; i<3; i++) {
            gravity[i] = 0;
            linear_acceleration[i] = 0;
            pla1[i] = 0;
            pla2[i] = 0;
        }
        sAngle = sData = 0;
        numSonar = 7;
        sonarData = new int[numSonar+1];
        int data = 127;
        for (int i=0; i<numSonar+1; i++) {
            //sonarData[i] = data;
            //sonarData[i] = 127 - i*12;
            sonarData[i] = 127;
            if (data == 0) {
                data = 127;
            } else {
                data = 0;
            }
        }
    }

    public void onSensorChanged(SensorEvent event) {

        int i;
        float alpha,calibration,scale,mr_speed,ml_speed,turn_scale,rm,rms,trigger;
        float forward,rightleft;
        float[] dldt = new float[3];

        trigger = 8;
        turn_scale = 2.0F;
        alpha = 0.8F;
        calibration = 9.8F / 10.24F;
        scale = 0.60F;

        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0] * calibration;
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1] * calibration;
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2] * calibration;

        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        rm = 0;
        for (i=0; i<3; i++)  {
            dldt[i] = linear_acceleration[i] - 2 * pla1[i] + pla2[i];
            rm += dldt[i] * dldt[i];
        }
        rm = rm / 3;
        rms = (float) Math.sqrt(rm);
        for (i=0; i<3; i++){
            pla2[i] = pla1[i];
            pla1[i] = linear_acceleration[i];
        }


        r = (float) Math.sqrt(gravity[0]*gravity[0]+gravity[1]*gravity[1]+gravity[2]*gravity[2]);
        if (r > 0) {
            theta = (float) Math.acos(gravity[1] / r);
            phi = (float) Math.acos(gravity[0] / r);
        } else {
            theta = 0;
            phi = 0;
        }
        if (gravity[1] > 0) {
            theta = -1 * theta;
        }
        if (theta >= 0) {
            theta = (theta * 180 / (float) Math.PI) - 90;
        } else {
            theta = -90 - (theta * 180 / (float) Math.PI);
        }
        if (phi >= 0) {
            phi = (phi * 180 / (float) Math.PI) - 90;
        } else {
            phi = -180 - (phi * 180 / (float) Math.PI);
        }


        theta = theta + 30;


        forward = scale * theta;
        rightleft = scale * phi;

        if ((forward>=-1) && (forward<=1)) {
            forward = 0;
        } else if (forward > 0) {
            forward = forward - 1;
            if (forward > 7) {
                forward = 7;
            }
        } else if (forward < 0) {
            forward = forward + 1;
            if (forward < -7) {
                forward = -7;
            }
        }

        if ((rightleft>=-1) && (rightleft<=1)) {
            rightleft = 0;
        } else if (rightleft > 0) {
            rightleft = rightleft - 1;
            if (rightleft > 7) {
                rightleft = 7;
            }
        } else if (rightleft < 0) {
            rightleft = rightleft + 1;
            if (rightleft < -7) {
                rightleft = -7;
            }
        }

        mr_speed = 0;
        ml_speed = 0;
        if (rc_sit) {
            if ((int) rightleft == 0) {
                rmotor_forward = true;
                lmotor_forward = true;
                mr_speed = 0;
                ml_speed = 0;
            } else if (rightleft > 0) {
                rmotor_forward = false;
                lmotor_forward = true;
                mr_speed = 0.75f * rightleft;
                ml_speed = 0.75f * rightleft;
            } else {
                rmotor_forward = true;
                lmotor_forward = false;
                mr_speed = -0.75f * rightleft;
                ml_speed = -0.75f * rightleft;
            }
        } else {
            if (forward >= 0) {
                rmotor_forward = true;
                lmotor_forward = true;
                if ((int) rightleft == 0) {
                    mr_speed = forward;
                    ml_speed = forward;
                } else if (rightleft > 0) {
                    mr_speed = forward;
                    ml_speed = forward - rightleft / turn_scale;
                    if (ml_speed < 0) {
                        ml_speed = 0;
                    }
                } else {
                    mr_speed = forward + rightleft / turn_scale;
                    ml_speed = forward;
                    if (mr_speed < 0) {
                        mr_speed = 0;
                    }
                }
            } else if (forward < 0) {
                rmotor_forward = false;
                lmotor_forward = false;
                if ((int) rightleft == 0) {
                    mr_speed = forward;
                    ml_speed = forward;
                } else if (rightleft > 0) {
                    mr_speed = forward;
                    ml_speed = forward + rightleft / turn_scale;
                    if (ml_speed > 0) {
                        ml_speed = 0;
                    }
                } else {
                    mr_speed = forward - rightleft / turn_scale;
                    ml_speed = forward;
                    if (mr_speed > 0) {
                        mr_speed = 0;
                    }
                }
            }
        }

        if (mr_speed < 0) {
            mr_speed = -1 * mr_speed;
        }
        if (ml_speed < 0) {
            ml_speed = -1 * ml_speed;
        }

        rmotor_speed = (int) mr_speed;
        lmotor_speed = (int) ml_speed;

        if (rmotor_speed > 7) {
            rmotor_speed = 7;
        } else if (rmotor_speed < 0) {
            rmotor_speed = 0;
        }
        if (lmotor_speed > 7) {
            lmotor_speed = 7;
        } else if (lmotor_speed < 0) {
            lmotor_speed = 0;
        }

        motor_control_code = 0;
        if (lmotor_forward) {
            motor_control_code += 1;
        }
        if (rmotor_forward) {
            motor_control_code += 16;
        }
        motor_control_code += 2*(rmotor_speed&7);
        motor_control_code += 32*(lmotor_speed&7);

        float time = System.nanoTime()/1000000;
        float delta = time - lastTime;

        if ((rms > trigger) && (last_rms < (0.9*trigger))) {
            mOnOffButton.setText("RC Sit");
            disableRC();
        } else {
            if ((delta > 50) || (lastTime == 0)) {
                lastTime = time;
                if (rc_on) {
                    updateRC();
                }
            }
        }

        last_rms = rms;

        delta = time - lastUpdate;
        if ((lastUpdate == 0) || (delta > 500)) {
            ShowSonar();
            lastUpdate = time;
        }

    }

    protected void disableRC() {
        if (rc_on && btDevice.btOk()) {
            char zero = 0;
            btDevice.write(zero);
        }
        rcAndroid.setBackgroundResource(R.drawable.forward);
        rc_on = false;
        rc_sit = false;
    }

    protected void updateRC() {

        boolean rf,lf,rr,lr;

        if (rc_on && btDevice.btOk()) {
            btDevice.write(motor_control_code);
        }

        //if (rc_on) {

            rf = lf = rr = lr = false;
            if ((motor_control_code & 14) > 0) {
                if ((motor_control_code & 1) > 0) {
                    rf = true;
                } else {
                    rr = true;
                }
            }
            if ((motor_control_code & 224) > 0) {
                if ((motor_control_code & 16) > 0) {
                    lf = true;
                } else {
                    lr = true;
                }
            }

            if ((rf != prf) || (lf != plf) || (rr != prr) || (lr != plr)) {
                if (rf && lf) {
                    rcAndroid.setBackgroundResource(R.drawable.forward); //left is a drawable XML file
                    wheelAnimationForward = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationForward.start();
                } else if (rr && lr) {
                    rcAndroid.setBackgroundResource(R.drawable.backward); //backward is a drawable XML file
                    wheelAnimationBackward = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationBackward.start();
                } else if (rf && lr) {
                    rcAndroid.setBackgroundResource(R.drawable.right); //right is a drawable XML file
                    wheelAnimationRight = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationRight.start();
                } else if (rr && lf) {
                    rcAndroid.setBackgroundResource(R.drawable.left); //left is a drawable XML file
                    wheelAnimationLeft = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationLeft.start();
                } else {
                    rcAndroid.setBackgroundResource(R.drawable.forward); //left is a drawable XML file
                }
                prf = rf;
                plf = lf;
                prr = rr;
                plr = lr;
            }

/*
                if ((motor_control_code >= 16 && motor_control_code <= 31) || (motor_control_code >= 48 && motor_control_code <= 63) // 4th bit is 1
                        || (motor_control_code >= 80 && motor_control_code <= 95) || (motor_control_code >= 112 && motor_control_code <= 127)
                        || (motor_control_code >= 144 && motor_control_code <= 159) || (motor_control_code >= 176 && motor_control_code <= 191)
                        || (motor_control_code >= 208 && motor_control_code <= 223) || (motor_control_code >= 240 && motor_control_code <= 255)) {
                    rcAndroid.setBackgroundResource(R.drawable.rc_android); //left is a drawable XML file
                    wheelAnimationLeft = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationLeft.start();
                }
            }

            // MOVING RIGHT :
            // first look at left wheel (it has to move forward)
            else if ((motor_control_code % 2 == 1) && (motor_control_code > 0) && (motor_control_code <= 255)) { // odd - 0th bit is 1
                // second look at right wheel (it has to move backward)
                if ((motor_control_code >= 0 && motor_control_code <= 15) || (motor_control_code >= 32 && motor_control_code <= 47) // 4th bit is 0
                        || (motor_control_code >= 64 && motor_control_code <= 79) || (motor_control_code >= 96 && motor_control_code <= 111)
                        || (motor_control_code >= 128 && motor_control_code <= 143) || (motor_control_code >= 160 && motor_control_code <= 175)
                        || (motor_control_code >= 192 && motor_control_code <= 207) || (motor_control_code >= 224 && motor_control_code <= 239)) {
                    rcAndroid.setBackgroundResource(R.drawable.right); //right is a drawable XML file
                    wheelAnimationRight = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationRight.start();
                }
            }

            // MOVING FORWARD :
            // first look at left wheel (it has to move forward)
            else if ((motor_control_code % 2 == 1) && (motor_control_code > 0) && (motor_control_code <= 255)) { // odd - 0th bit is 1
                // second look at right wheel (it has to move forward)
                if ((motor_control_code >= 16 && motor_control_code <= 31) || (motor_control_code >= 48 && motor_control_code <= 63) // 4th bit is 1
                        || (motor_control_code >= 80 && motor_control_code <= 95) || (motor_control_code >= 112 && motor_control_code <= 127)
                        || (motor_control_code >= 144 && motor_control_code <= 159) || (motor_control_code >= 176 && motor_control_code <= 191)
                        || (motor_control_code >= 208 && motor_control_code <= 223) || (motor_control_code >= 240 && motor_control_code <= 255)) {
                    rcAndroid.setBackgroundResource(R.drawable.forward); //forward is a drawable XML file
                    wheelAnimationForward = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationForward.start();
                }
            }

            // MOVING BACKWARD :
            // first look at left wheel (it has to move backward)
            else if ((motor_control_code % 2 == 0) && (motor_control_code >= 0) && (motor_control_code < 255)) { // odd - 0th bit is 0
                // second look at right wheel (it has to move backward)
                if ((motor_control_code >= 0 && motor_control_code <= 15) || (motor_control_code >= 32 && motor_control_code <= 47) // 4th bit is 0
                        || (motor_control_code >= 64 && motor_control_code <= 79) || (motor_control_code >= 96 && motor_control_code <= 111)
                        || (motor_control_code >= 128 && motor_control_code <= 143) || (motor_control_code >= 160 && motor_control_code <= 175)
                        || (motor_control_code >= 192 && motor_control_code <= 207) || (motor_control_code >= 224 && motor_control_code <= 239)) {
                    rcAndroid.setBackgroundResource(R.drawable.backward); //backward is a drawable XML file
                    wheelAnimationBackward = (AnimationDrawable) rcAndroid.getBackground();
                    wheelAnimationBackward.start();
                }
            }
*/
            // @@ END

        //}

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            Toast.makeText(getApplicationContext(),"null", Toast.LENGTH_SHORT).show();
            return;
        }
        mBluetooth_ID = data.getStringExtra(BluetoothActivity.BLUETOOTH_ID_SELECTED);

        int i,mark;
        i = mark = 0;
        while (i<mBluetooth_ID.length()) {
            if (mBluetooth_ID.charAt(i) == '\n') {
                mark = i+1;
                i = mBluetooth_ID.length();
            }
            i++;
        }
        mBluetooth_ID = mBluetooth_ID.substring(mark,mBluetooth_ID.length());
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mBluetooth_ID);
        btDevice.Connect(mBluetoothDevice);
        background.start();
    }

    //private class ConnectThread extends Thread {
    private class ConnectBTDevice implements Runnable {

        private boolean bt_ok = false;
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        BroadcastReceiver broadcastBtDevices=null;

        public ConnectBTDevice() {
            BluetoothSocket tmp = null;
        }

        public void Connect(BluetoothDevice device) {

            BluetoothSocket tmp = null;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            registerReceiver(broadcastBtDevices, new IntentFilter(BluetoothDevice.ACTION_FOUND));

            mmDevice = device;

            Log.i("MainActivity","Trying to connect to device");
            try {
                try {
                    Method m=mmDevice.getClass().getMethod("createRfcommSocket",int.class);
                    mmSocket=(BluetoothSocket)m.invoke(mmDevice,1);
                    Log.i("MainActivity","create socket 1");
                }
                catch (NoSuchMethodException e) {
                    mmSocket=mmDevice.createRfcommSocketToServiceRecord(my_UUID);
                    Log.i("MainActivity","create socket 2");
                }
            }
            catch (    Exception e) {
                Log.i("MainActivity","Couldn't connect to device [" + mmDevice.getName() + ", "+ mmDevice.getAddress()+ "]",e);
                mmSocket=null;
            }

            try {
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.i("MainActivity", "Can't get input/output streams");
                return;
            }
            Log.i("MainActivity", "Input/output streams ok");

        }

        public boolean btOk() {
            return(bt_ok);
        }


        public void ConnectSocket2() {
            try {
                mmSocket.connect();
            } catch (IOException e) {
                Log.i("MainActivity", "Can't connect socket");
                return;
            }
            bt_ok = true;
            Log.i("MainActivity", "Socket connected");

        }

        public void run() {

            ConnectSocket2();

            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    Message msg = handler.obtainMessage();
                    msg.arg1 = buffer[0];
                    msg.arg2 = buffer[1];
                    handler.dispatchMessage(msg);
                } catch (IOException e) {
                    Log.i("MainActivity",e.getMessage());
                    break;
                }
            }

        }

        public void write(char mbyte) {
            char[] bytes = { 0, 0 };
            bytes[0] = mbyte;
            bytes[1] = '\n';
            try {
                mmOutStream.write(bytes[0]);
            } catch (IOException e) {
                return;
            }
            //Log.i("MainActivity", "Write " + bytes[0]);
        }

        public void cancel() {
            try {
                mmSocket.close();
                Log.i("MainActivity", "cancel - closing socket");
            } catch (IOException e) { }
        }

    }

    private final Handler handler = new Handler() {

        public void handleMessage(Message msg) {

            int data = msg.arg1;
            if (data < 0) {
                data = 256 +data;
            }

            sAngle = data&7;
            sData = ((data/8)*4);

            int index = 6-sAngle;
            if (index < 0) {
                index = 0;
            } else if (index > numSonar) {
                index = numSonar;
            }
            if (sData < 0) {
                sData = -1 * sData;
            }
            sonarData[index] = sData;

            //Log.i("MainActivity", "sAngle=" + sAngle + ", sData=" + sData);
            Log.i("MainActivity", "data=" + data);

        }
    };


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
           return true;
        }
        return super.onTouchEvent(event);
    }

    private void onLeftSwipe() {
        Intent i = new Intent(MainActivity.this,BluetoothActivity.class);
        startActivityForResult(i,0);
    }

    private void onRightSwipe() {
        // Do something
    }

    // Private class for gestures
    private class SwipeGestureDetector extends GestureDetector.SimpleOnGestureListener {
        // Swipe properties, you can change it to make the swipe
        // longer or shorter and speed
        private static final int SWIPE_MIN_DISTANCE = 120;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 200;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,float velocityX, float velocityY) {
            try {
                float diffAbs = Math.abs(e1.getY() - e2.getY());
                float diff = e1.getX() - e2.getX();

                if (diffAbs > SWIPE_MAX_OFF_PATH)
                    return false;
                if (diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    MainActivity.this.onLeftSwipe();
                } else if (-diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    MainActivity.this.onRightSwipe();
                }
            } catch (Exception e) {
                Log.e("YourActivity", "Error on gestures");
            }
            return false;
        }
    }

    public void ShowSonar() {

        int i,j,k,start,end,delta;
        int border = 2;

        int[] gdata = new int[mwidth+10];
        int[] gdatab = new int[mwidth+10];
        int[] gdatar = new int[mwidth+10];

        for (i=0; i<(numSonar-1); i++) {
            k = 0;
            start = i*mwidth/(numSonar-1);
            end = (i+1)*mwidth/(numSonar-1)+1;
            delta = end - start;
            for (j=start; j<end; j++) {
                gdata[j] = 51+(8*(127-(sonarData[i]+(k*(sonarData[i+1]-sonarData[i])/delta))))/5;
                k++;
            }
        }

        int limit = 164;
        for (i=0; i<mwidth; i++) {
            if (gdata[i] < limit) {
                gdatab[i] = gdata[i];
                gdatar[i] = 0;
            } else {
                gdatab[i] = limit-(limit*(gdata[i]-limit)/(255-limit))/2;
                gdatar[i] = 255*(gdata[i]-limit)/(255-limit);
            }
        }

        Bitmap bm = Bitmap.createBitmap(mheight, mwidth, Bitmap.Config.RGB_565);
        bm.eraseColor(Color.BLACK);
        Canvas mycanvas = new Canvas(bm);

        Paint paint = new Paint();
        paint.setAlpha(100);
        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);

        for (i=0; i<mwidth; i++) {
            paint.setColor(Color.rgb(gdatar[i],0,gdatab[i]));
            mycanvas.drawLine(i,0,i,mheight,paint);
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Configuration config = getResources().getConfiguration();
        Resources res = new Resources(getAssets(), metrics, config);
        BitmapDrawable bmd = new BitmapDrawable(res, bm);
        sonarView.setForeground(bmd);

    }



}
