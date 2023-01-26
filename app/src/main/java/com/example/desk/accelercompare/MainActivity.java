package com.example.desk.accelercompare;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
//Sensor
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
//GPS
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
//SMS
import android.os.CountDownTimer;
import android.telephony.SmsManager;
//ESP32 CAM
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
//
import android.os.Build;
import android.os.Bundle;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager Sensor_Manager;
    /*가속 센서는 중력을 포함하여 기기에 적용되는 가속을 측정합니다.*/
    private Sensor Sensor_AcceleroMeter;
    /*선형 가속 센서는 각 기기 축을 따라서 중력을 제외한 가속을 나타내는 3D 벡터를 제공합니다.*/
    private Sensor Sensor_linear_AcceleroMeter;

    private float gAccX, gAccY, gAccZ, LaxisX, LaxisY, LaxisZ;
    private double G_Total, L_Total;
    private float Last_X, Last_Y, Last_Z;

    private long lastUpdate = 0;
    private int COLLISION_THRESHOLD = 20000;  // 충돌 임계값

    TextView Gravity_X, Gravity_Y, Gravity_Z, Gravity_Total; //중력을 포함한 가속도
    TextView Linear_X, Linear_Y, Linear_Z, Linear_Total; //선형 가속도

    //None Gravity
    /*
    private float alpha = 0.8f;
    private float accx, accy, accz;
    private float TempX, TempY, TempZ;
    private double NG_Total;

    TextView None_X, None_Y, None_Z, None_Total;
*/

    //GPS
    private LocationManager GPS_Manager;
    private Location Now_Location = null;
    private TextView GPS_Provider, GPS_Coordinate;
    private String provider;
    private double longitude, latitude, altitude;

    //SMS
    private EditText textPhoneNo;
    String phoneNo, sms;
    private Button buttonSend;

    //ESP32 Cam
    private HandlerThread stream_thread;
    private Handler stream_handler;
    private Button Connect_button;
    private ImageView monitor;
    private EditText ip_text;
    Bitmap bitmap;
    byte[] buffer;

    private final int ID_CONNECT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {//Activity가 생성될 때
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //중력을 포함한 가속도
        Gravity_X = (TextView) findViewById(R.id.GAxis_X); //X축을 따라 측정한 가속.
        Gravity_Y = (TextView) findViewById(R.id.GAxis_Y); //Y축을 따라 측정한 가속.
        Gravity_Z = (TextView) findViewById(R.id.GAxis_Z); //Z축을 따라 측정한 가속.
        //중력을 제외한 선형가속도
        Linear_X = (TextView) findViewById(R.id.LAxis_X); //편향 보상 없이 X축을 따라 측정한 가속.
        Linear_Y = (TextView) findViewById(R.id.LAxis_Y); //편향 보상 없이 Y축을 따라 측정한 가속.
        Linear_Z = (TextView) findViewById(R.id.LAxis_Z); //편향 보상 없이 Z축을 따라 측정한 가속.

        Gravity_Total = (TextView) findViewById(R.id.G_Total);
        Linear_Total = (TextView) findViewById(R.id.L_Total);

        //None
        /*
        None_X = (TextView) findViewById(R.id.NGAxis_X); //X축을 따라 측정한 가속.
        None_Y = (TextView) findViewById(R.id.NGAxis_Y); //Y축을 따라 측정한 가속.
        None_Z = (TextView) findViewById(R.id.NGAxis_Z); //Z축을 따라 측정한 가속.
        None_Total = (TextView) findViewById(R.id.NG_Total);
*/
        Sensor_Manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor_AcceleroMeter = Sensor_Manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor_linear_AcceleroMeter = Sensor_Manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        //GPS
        GPS_Provider = (TextView) findViewById(R.id.View_GPS_Provider);
        GPS_Coordinate = (TextView) findViewById(R.id.View_GPS_Coordinate);

        if(Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},0);
        }//권한 체크
        GPS_Manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Now_Location = GPS_Manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); //GPS 사용가능 여부 확인

        provider    = Now_Location.getProvider();
        longitude   = Now_Location.getLongitude();
        latitude    = Now_Location.getLatitude();
        altitude    = Now_Location.getAltitude();

        GPS_Manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, gpsLocationListener);
        GPS_Manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, gpsLocationListener);

        //SMS
        buttonSend =(Button)findViewById(R.id.TestBT) ;
        buttonSend.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if ("SoS Send!".equals(buttonSend.getText())) {
                    CountDownTimer.start();
                } else {
                    CountDownTimer.cancel();
                    buttonSend.setText("SoS Send!");
                }
            }
        });

        textPhoneNo = (EditText) findViewById(R.id.editTextPhoneNo);
        textPhoneNo.setText("5555215554");

        //ESP32
        Connect_button = (Button)findViewById(R.id.connect);
        Connect_button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                stream_handler.sendEmptyMessage(ID_CONNECT);
            }
        });

        monitor = findViewById(R.id.monitor);
        ip_text = findViewById(R.id.ip);
        ip_text.setText("192.168.219.112");

        stream_thread = new HandlerThread("http");
        stream_thread.start();
        stream_handler = new HttpHandler(stream_thread.getLooper());
    }

    //GPS 실시간
    final LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            provider    = location.getProvider();
            longitude   = location.getLongitude();
            latitude    = location.getLatitude();
            altitude    = location.getAltitude();

            //SMS 글자수 제한 때문에 일정 글자 이상 넘어가면 전송이 안됨
            GPS_Provider.setText("(제공) " + provider
                    + "\n" + "(고도) " + String.format("%.1f",altitude));
            GPS_Coordinate.setText("(위도) " + String.format("%.6f",latitude)
                    + "\n" + "(경도) " + String.format("%.6f",longitude));
        }
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
        public void onProviderEnabled(String provider) {
        }
        public void onProviderDisabled(String provider) {
        }
    };
    //가속도/자이로 센서
    @Override//콜백 메서드, 사용자와 상호작용 하는 단계
    protected void onResume() {
        super.onResume();
        Sensor_Manager.registerListener(this, Sensor_AcceleroMeter, SensorManager.SENSOR_DELAY_NORMAL);
        Sensor_Manager.registerListener(this, Sensor_linear_AcceleroMeter, SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override//콜백 메서드, Activity가 잠시 멈춘 단계, 베터리 소모를 생각해서 Unregister
    protected void onPause() {
        super.onPause();
        Sensor_Manager.unregisterListener(this);
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor == Sensor_AcceleroMeter) {//include gravity
            gAccX = event.values[0];
            gAccY = event.values[1];
            gAccZ = event.values[2];
            G_Total = Math.sqrt(Math.pow(gAccX,2) + Math.pow(gAccY, 2) + Math.pow(gAccZ , 2));

            Gravity_X.setText("X axis : " + String.format("%.2f", gAccX));
            Gravity_Y.setText("Y axis : " + String.format("%.2f", gAccY));
            Gravity_Z.setText("Z axis : " + String.format("%.2f", gAccZ));
            Gravity_Total.setText("Total Gravity : "  + String.format("%.2f", G_Total) + " m/s\u00B2");
            //None
            /*
            TempX = alpha * TempX + (1-alpha) * event.values[0];
            TempY = alpha * TempY + (1-alpha) * event.values[1];
            TempZ = alpha * TempZ + (1-alpha) * event.values[2];
            accx = event.values[0] - TempX;
            accy = event.values[1] - TempY;
            accz = event.values[2] - TempZ;
            NG_Total = Math.sqrt(Math.pow(accx,2) + Math.pow(accy, 2) + Math.pow(accz , 2));
            None_X.setText("X axis : " + String.format("%.2f", accx));
            None_Y.setText("Y axis : " + String.format("%.2f", accy));
            None_Z.setText("Z axis : " + String.format("%.2f", accz));
            None_Total.setText("Total None Gravity : "  + String.format("%.2f", NG_Total) + " m/s\u00B2");

            long Now_CurrentTime = System.currentTimeMillis();  //현재 시간
            if((Now_CurrentTime - lastUpdate)>100){    //.1초 간격 가속도 값 업데이트
                long Time_Diff = (Now_CurrentTime - lastUpdate); // 시차
                lastUpdate = Now_CurrentTime;
                //충돌량
                double Collision_Detect = Math.sqrt(Math.pow(accz - Last_Z,2)*100 + Math.pow(accx- Last_X,2)*100 +  Math.pow(accy- Last_Y,2)*100)/ Time_Diff * 10000;
                if (Collision_Detect > COLLISION_THRESHOLD) {
                    //Toast.makeText(this, "Collision Detected", Toast.LENGTH_SHORT).show();
                    Gps_Result.setText("위치정보 : " + provider + "\n" +
                            "위도 : " + latitude + "\n" +
                            "경도 : " + longitude + "\n" +
                            "고도  : " + altitude);
                    Toast.makeText(getApplicationContext(),
                            "Collision Detected, -\n제공: " + provider + "\n위도: " + latitude
                                    + "\n경도: " + longitude + "\n고도: " + altitude,
                            Toast.LENGTH_LONG).show();
                }
                Last_X = accx;
                Last_Y = accy;
                Last_Z = accz;
            }*/
        }

        if(event.sensor == Sensor_linear_AcceleroMeter) {//exclude gravity
            LaxisX = event.values[0];
            LaxisY = event.values[1];
            LaxisZ = event.values[2];
            L_Total = Math.sqrt(Math.pow(LaxisX,2) + Math.pow(LaxisY, 2) + Math.pow(LaxisZ, 2));

            Linear_X.setText("X axis : " + String.format("%.2f", LaxisX));
            Linear_Y.setText("Y axis : " + String.format("%.2f", LaxisY));
            Linear_Z.setText("Z axis : " + String.format("%.2f", LaxisZ));
            Linear_Total.setText("Total Linear : "  + String.format("%.2f", L_Total) + " m/s\u00B2");

            long Now_CurrentTime = System.currentTimeMillis();  //현재 시간
            if((Now_CurrentTime - lastUpdate)>100){    //.1초 간격 가속도 값 업데이트
                long Time_Diff = (Now_CurrentTime - lastUpdate); // 시차
                lastUpdate = Now_CurrentTime;
                //충돌량
                double Collision_Detect = Math.sqrt(Math.pow(LaxisZ - Last_Z,2)*100 + Math.pow(LaxisX- Last_X,2)*100 +  Math.pow(LaxisY- Last_Y,2)*100)/ Time_Diff * 10000;
                if (Collision_Detect > COLLISION_THRESHOLD) {
                    Toast.makeText(getApplicationContext(),
                            "Collision Detected, -\n제공: " + provider + "\n위도: " + latitude
                                    + "\n경도: " + longitude + "\n고도: " + altitude,
                            Toast.LENGTH_LONG).show();
                    CountDownTimer.start();
                }
                Last_X = LaxisX;
                Last_Y = LaxisY;
                Last_Z = LaxisZ;
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //CountDown
    final CountDownTimer CountDownTimer = new CountDownTimer( 10000, 1000) {
        public void onTick(long millisUntilFinished) {
            buttonSend.setText("Wait For SoS: " + millisUntilFinished / 1000);
        }
        public void onFinish() {
            buttonSend.setText("SoS Send!");
            sendSMSMessage();
        }
    };
    //SMS
    protected void sendSMSMessage() {
        phoneNo = textPhoneNo.getText().toString();
        sms = GPS_Provider.getText().toString() + "\n" + GPS_Coordinate.getText().toString();
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, sms, null, null);
            Toast.makeText(getApplicationContext(), "전송 완료!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "전송 실패!", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    //ESP32 CAM
    private class HttpHandler extends Handler {
        public HttpHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            VideoStream();
        }
    }
    private void VideoStream() {
        String stream_url = "http://" + ip_text.getText() + ":81/stream";
        BufferedInputStream bis = null; //Byte단위로 파일을 읽어올때 사용하는 버퍼 스트림
        FileOutputStream fos = null; //데이터를 바이트 스트림으로 저장하기 위해 사용
        try {
            URL url = new URL(stream_url); //URL객체 생성
            try {//프로토콜이 HTTP인경우 반환된 객체를 캐스팅
                HttpURLConnection huc = (HttpURLConnection) url.openConnection(); //선언한 URL 객체를 다른 클래스의 객체로 선언한 URL 객체를 다른 클래스의 객체로
                huc.setRequestMethod("GET");//HTTP 메소드중 하나인 URL요청에 대한 메소드를 설정
                huc.setConnectTimeout(1000 * 5);//연결 타임아웃 값
                huc.setReadTimeout(1000 * 5);//읽기 타임아웃 값
                huc.setDoInput(true);//URLConnection이 서버에 데이터를 보내는데 사용할 수 있는지 여부를 설정
                huc.connect();

                if (huc.getResponseCode() == 200) { //서버에 보낸 HTTP 응답코드 승인이 200일듯?
                    InputStream in = huc.getInputStream();
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr); //*데이터를 문자열로 읽기 위해 InputStream을 BufferedReader로 래핑
                    String data; //*
                    int len; //*

                    while ((data = br.readLine()) != null) { //*
                        if (data.contains("Content-Type:")) { //*
                            data = br.readLine(); //*
                            len = Integer.parseInt(data.split(":")[1].trim());
                            bis = new BufferedInputStream(in);
                            buffer = new byte[len];
                            int t = 0;
                            while (t < len) { t += bis.read(buffer, t, len - t); }

                            Bytes2ImageFile(buffer, getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpeg");
                            bitmap = BitmapFactory.decodeFile(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpeg"); //Decode a file path into a bitmap.

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() { monitor.setImageBitmap(bitmap); }
                            });
                        }
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
        catch (MalformedURLException e) { e.printStackTrace(); }
        finally {
            try {
                if (bis != null) { bis.close(); } //연결 종료
                if (fos != null) { fos.close(); } //연결 종료
                stream_handler.sendEmptyMessageDelayed(ID_CONNECT,3000);
            } catch (IOException e) { e.printStackTrace(); }
        }

    }
    private void Bytes2ImageFile(byte[] bytes, String fileName) {
        try {
            File file = new File(fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes, 0, bytes.length);
            fos.flush();
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}

    /*
    private void sendMMSMessage(){
        final Bitmap bitmap = BitmapFactory.decodeFile(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "Title", null);

        Uri uri = Uri.parse(path);

        Intent it = new Intent(Intent.ACTION_SEND);
        it.putExtra("sms_body", "some text");
        it.putExtra(Intent.EXTRA_STREAM, uri);
        it.setType("image/*");
        // 삼성 단말에서만 허용 ( 앱 선택 박스 없이 호출 )
        it.setComponent(new ComponentName("com.sec.mms", "com.sec.mms.Mms"));
        startActivity(it);
    }

    private Uri getImageUri(Context context) {
        final Bitmap bitmap = BitmapFactory.decodeFile(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, "Title", null);
        return Uri.parse(path);
    }*/
    /*
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void sendMMSMessage(){

        phoneNo = textPhoneNo.getText().toString();

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra("addredd",phoneNo);
        intent.putExtra("bitmap",bitmap);
        intent.setType("image/png");
        startActivity(intent);

        phoneNo = textPhoneNo.getText().toString();

        ByteArrayOutputStream bytes= new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,bytes); //압축
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "/0A.jpg", null);


        Uri providerURI = Uri.fromFile();

        try{
            SmsManager smsManager = SmsManager.getDefault();
            //smsManager.sendTextMessage(phoneNo, null, path, null, null);
            smsManager.sendMultimediaMessage(context.getApplicationContext(), providerURI, phoneNo, null, null);
            Toast.makeText(getApplicationContext(), "전송 완료!", Toast.LENGTH_LONG).show();
        }catch (Exception e) {
            Toast.makeText(getApplicationContext(), "SMS faild, please try again later!", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }*/
