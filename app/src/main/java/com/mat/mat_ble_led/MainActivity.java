package com.mat.mat_ble_led;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private LinearLayout scanLayout, stateLayout, btnLayout, controlLayout;
    private TextView macAddr, readState, sGroupid, sOntime, sSendGroup, sUniqid, sSensorOn, sSensorOff, sFixed, sSensorState;
    private Button scanBtn, setMainBtn, setOperBtn, groupBtn, allBtn;
    private Spinner setCri, setGroup, sendGroup, setDimOn, setDimOff, setOper, fixedDim;
    private EditText uniqid, keepSec;
    private ScrollView scroll, mainScroll;
    private ListView bleList;
    private ProgressDialog proDialog;

    //위치 권한 요청
    private static int REQUEST_ACCESS_FINE_LOCATION = 1;
    //스캔 시간
    private static final long SCAN_PERIOD = 5000;
    //각 조건 boolean 값
    private boolean mScanning = false;
    private boolean mConnect = false;
    private boolean infoChk = false;
    //Gatt 에서 받은 데이터
    private int[] readData;
    //선택한 Device mac
    private String selectAdd;
    //read, write temp
    private int temp = 0;
    private int writeTemp = 0;

    //Ble 통신 변수
    private BleService bleService;
    private BluetoothAdapter bleAdapter;
    private BluetoothGattService gattService;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothDevice selectDevice;
    private ArrayAdapter<String> bleListAdapter;
    private List<String> list = new ArrayList<>();
    private Handler handler;

    //뒤로가기 종료 이벤트 변수
    private final long FINISH_INTERVAL_TIME = 2000; //키 간격 2초안에 더블 클릭시 종료 인터벌
    private long backPressedTime = 0;

    //BleService.java 서비스와 연결 되었을 시 callback
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bleService = ((BleService.LocalBinder) service).getService();
            if (!bleService.initialize()) {
                Log.e("TAG", "initialize 실패");
                finish();
            }
            //Gatt Server와 연결이 안되었을 시 스캔 실행
            if (!mConnect) {
                startScan();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
        }
    };

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {

            //블루투스 스캔 콜백 정의
            boolean dataAdd = true;
            //rssi 값 99이하 중복 리스트 중복 방지 list는 비교를 위한 임시 객체
            if (list.contains(device.getAddress()) && (rssi * -1) < 100) {
                dataAdd = false;

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < bleListAdapter.getCount(); i++) {
                            String[] addr = bleListAdapter.getItem(i).split("\n");
                            if (addr[1].equals(device.getAddress())) {
                                bleListAdapter.remove(bleListAdapter.getItem(i));
                                //insert로 해당 위치에 삽입(rssi 값이 너무 빨리 변하므로 방지하고자)
                                bleListAdapter.insert(device.getName() + "\n" + device.getAddress() + "\n" + "RSSI : " + rssi, i);
                            }
                        }
                        //ArrayAdapter 정렬 메서드
                        bleListAdapter.sort(new Comparator<String>() {
                            @Override
                            public int compare(String o1, String o2) {
                                return o1.substring(o1.indexOf("RSSI : ") + 8).compareTo(o2.substring(o2.indexOf("RSSI : ") + 8));
                            }
                        });
                    }
                }, 3000);

            } else {
                //rssi 99이하, "M" 로 시작되는 리스트만 검색
                if (device.getName() != null && device.getName().contains("MA") && (rssi * -1) < 100)
                    list.add(device.getAddress());
                else
                    dataAdd = false;
            }
            if (dataAdd) {
                //실제 스레드를 돌리는 로직 위에 조건 만족시 스캔된 디바이스 목록을 추가
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        devices.add(device);
                        bleListAdapter.add(device.getName() + "\n" + device.getAddress() + "\n" + "RSSI : " + rssi);
                        bleListAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    };


    //Gatt Receiver 정의
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (bleService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnect = true;
                scanBtn.setText("DISCONNECT");
                scanLayout.setVisibility(View.GONE);
                stateLayout.setVisibility(View.VISIBLE);
                controlLayout.setVisibility(View.VISIBLE);
                macAddr.setText(selectDevice.getAddress() + " " + selectDevice.getName());

            } else if (bleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnect = false;
                scanBtn.setText("SCAN");

                if (proDialog != null) {
                    proDialog.dismiss();
                }

                if (bleAdapter != null) {
                    startScan();
                } else {
                    Toast.makeText(getApplicationContext(), "블루투스를 활성화 시켜주세요", Toast.LENGTH_SHORT).show();
                    finish();
                }
                stateLayout.setVisibility(View.GONE);
                controlLayout.setVisibility(View.GONE);
            } else if (bleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                gattService = bleService.getSupportedGattServices();

                if (gattService == null) {
                    Toast.makeText(getApplicationContext(), "서비스 연결 요청 실패 어플리케이션을 다시 실행시켜 주세요", Toast.LENGTH_SHORT).show();
                }

                try {
                    //SETUP DATA UUID
                    getData("4efa48aa-6daa-4786-b89c-ae8357e84cc6");
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "SETUP DATA READ FAIL", Toast.LENGTH_SHORT);
                    bleService.disconnect();
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //FIXED_ON DATA UUID
                            getData("7c213917-9fb7-48ea-a379-a2e2ca4c9d03");
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "FIXED_ON DATA READ FAIL", Toast.LENGTH_SHORT);
                            bleService.disconnect();
                        }
                    }
                }, 300);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //DIM_CONTROL DATA UUID
                            getData("4efa48aa-6daa-423e-b89c-ae8357e84cc6");
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "DIM_CONTROL DATA READ FAIL", Toast.LENGTH_SHORT);
                            bleService.disconnect();
                        }
                    }
                }, 600);

            } else if (bleService.ACTION_DATA_AVAILABLE.equals(action)) {
                readData = intent.getIntArrayExtra(BleService.EXTRA_DATA);

                if (temp == 0) {
                    try {
                        setView1(readData);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "데이터 읽기 실패", Toast.LENGTH_SHORT).show();
                    }
                } else if (temp == 1) {
                    try {
                        setView2(readData);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "데이터 읽기 실패", Toast.LENGTH_SHORT).show();
                    }
                } else if (temp == 2) {
                    try {
                        setView3(readData);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "데이터 읽기 실패", Toast.LENGTH_SHORT).show();
                    }
                }

            } else if (bleService.ACTION_DATA_WRITE.equals(action)) {
                Toast.makeText(getApplicationContext(), "값을 변경 하였습니다", Toast.LENGTH_SHORT).show();
                proDialog.dismiss();
            }
    }
};

@Override
protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewSetting();
        stateLayout.setVisibility(View.GONE);
        controlLayout.setVisibility(View.GONE);

        //handler 객체 초기화
        handler = new Handler();

        //Manager 객체를 통한 bleAdapter 호출
        final BluetoothManager bleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();

        if (bleAdapter == null) {
            Toast.makeText(this, "블루투스 기능을 지원하지 않는 디바이스 입니다", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!bleAdapter.isEnabled()) {
            Toast.makeText(this, "블루투스 기능을 활성화 해주세요", Toast.LENGTH_SHORT).show();
        }

        bleListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        bleList.setAdapter(bleListAdapter);

        int permissionChk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

        if (permissionChk == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        } else {
            Intent gattServiceIntent = new Intent(MainActivity.this, BleService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bleService != null) {
            final boolean result = bleService.connect(selectAdd);
            Log.d("TAG", "Connect request result =" + result);
        }

        bleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (mScanning) {
                    bleAdapter.stopLeScan(leScanCallback);
                    mScanning = false;
                }

                String[] data = bleListAdapter.getItem(position).split("\n");
                selectAdd = data[1];

                proDialog = new ProgressDialog(MainActivity.this);
                proDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                proDialog.setMessage("로딩중...");
                proDialog.show();
                selectDevice = bleAdapter.getRemoteDevice(selectAdd);
                bleService.connect(selectAdd);

            }
        });

        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mConnect && !mScanning) {
                    startScan();
                } else {
                    bleService.disconnect();
                }
            }
        });


        //write btn event
        setMainBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final byte[] pushData = new byte[8];

                if (setCri.getSelectedItem().equals("개별")) {
                    pushData[0] = (byte) Integer.parseInt((String) setGroup.getSelectedItem());
                } else if (setCri.getSelectedItem().equals("그룹")) {
                    pushData[0] = (byte) 0xFE;
                } else if (setCri.getSelectedItem().equals("전체")) {
                    pushData[0] = (byte) 0xFF;
                }

                pushData[1] = (byte) Integer.parseInt((String) setDimOn.getSelectedItem());
                pushData[2] = (byte) Integer.parseInt((String) setDimOff.getSelectedItem());
                pushData[3] = (byte) Integer.parseInt(keepSec.getText().toString());
                pushData[4] = (byte) Integer.parseInt((String) sendGroup.getSelectedItem());
                pushData[5] = (byte) (Integer.parseInt(uniqid.getText().toString()) / 256 & 0xff);
                pushData[6] = (byte) (Integer.parseInt(uniqid.getText().toString()) % 256 & 0xff);
                pushData[7] = 0x04;

                try {
                    setData("4efa48aa-6daa-4786-b89c-ae8357e84cc6", pushData);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "상태 데이터 변경 실패", Toast.LENGTH_SHORT).show();
                }

                for (byte data : pushData) {
                    System.out.print((data & 0xff) + " ");
                }
                System.out.println();

            }
        });

        setOperBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] pushData = new byte[3];
                if (setCri.getSelectedItem().equals("개별")) {
                    pushData[0] = (byte) Integer.parseInt((String) setGroup.getSelectedItem());
                } else if (setCri.getSelectedItem().equals("그룹")) {
                    pushData[0] = (byte) 0xFE;
                } else if (setCri.getSelectedItem().equals("전체")) {
                    pushData[0] = (byte) 0xFF;
                }

                String oper = setOper.getSelectedItem().toString();

                switch (oper) {
                    case "공장모드":
                        pushData[1] = 0x00;
                        break;
                    case "자동모드":
                        pushData[1] = 0x01;
                        break;
                    case "고정(홀수)":
                        pushData[1] = 0x05;
                        break;
                    case "고정(짝수)":
                        pushData[1] = 0x06;
                        break;
                    case "고정모드":
                        pushData[1] = 0x07;
                        break;
                }

                pushData[2] = (byte) (Integer.parseInt((String) fixedDim.getSelectedItem()));

                try {
                    setData("7c213917-9fb7-48ea-a379-a2e2ca4c9d03", pushData);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "고정 데이터 변경 실패", Toast.LENGTH_SHORT).show();
                }

                for (byte data : pushData) {
                    System.out.print((data & 0xff) + " ");
                }
                System.out.println();

            }
        });

        groupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mode = groupBtn.getText().toString();
                byte[] pushData = new byte[2];

                if (mode.equals("그룹 ON")) {
                    pushData[0] = (byte) 0xFE;
                    pushData[1] = 100;
                    groupBtn.setText("그룹 OFF");
                } else {
                    pushData[0] = (byte) 0xFE;
                    pushData[1] = 0;
                    groupBtn.setText("그룹 ON");
                }

                try {
                    setData("4efa48aa-6daa-423e-b89c-ae8357e84cc6", pushData);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "그룹 제어 실패", Toast.LENGTH_SHORT).show();
                }
            }
        });

        allBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mode = allBtn.getText().toString();
                byte[] pushData = new byte[2];
                if (mode.equals("전체 ON")) {
                    pushData[0] = (byte) 0xFF;
                    pushData[1] = 100;
                    allBtn.setText("전체 OFF");
                } else {
                    pushData[0] = (byte) 0xFF;
                    pushData[1] = 0;
                    allBtn.setText("전체 ON");
                }

                try {
                    setData("4efa48aa-6daa-423e-b89c-ae8357e84cc6", pushData);
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "전체 제어 실패", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        bleService = null;
    }

    @Override
    public void onBackPressed() {
        long tempTime = System.currentTimeMillis();
        long intervalTime = tempTime - backPressedTime;

        if (intervalTime >= 0 && FINISH_INTERVAL_TIME >= intervalTime) {
            super.onBackPressed();
        } else {
            backPressedTime = tempTime;
            Toast.makeText(getApplicationContext(), "한번 더 누르면 종료합니다.", Toast.LENGTH_LONG).show();
        }
    }

    //권한 요청후 액션
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == -1) {
            Toast.makeText(getApplicationContext(), "위치 권한을 켜야 블루투스 장치를 스캔 할 수 있습니다.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Intent gattServiceIntent = new Intent(MainActivity.this, BleService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    private void startScan() {
        if (scanLayout.getVisibility() == View.GONE) {
            scanLayout.setVisibility(View.VISIBLE);
        }
        if (!mScanning) {
            scanLeDevice(true);
        } else {
            scanLeDevice(false);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_DATA_WRITE);
        return intentFilter;
    }

    private void scanLeDevice(final boolean enable) {
        temp = 0;
        bleListAdapter.clear();
        devices.clear();
        list.clear();
        if (enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bleAdapter.stopLeScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            bleAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            bleAdapter.stopLeScan(leScanCallback);
        }
    }

    private void getData(String uuid) throws Exception {
        BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString(uuid));
        bleService.readCharacteristic(data);
    }

    private void setData(String uuid, byte[] pushData) throws Exception {
        final BluetoothGattCharacteristic data = gattService.getCharacteristic(UUID.fromString(uuid));
        data.setValue(pushData);
        bleService.writeCharacteristic(data);
        proDialog.show();
    }

    private void setView1(int[] readData) throws Exception {
        String read = "상태 : ";
        for (int i = 0; i < readData.length; i++) {
            read += readData[i] + " ";
        }

        readState.setText(read);
        sGroupid.setText(String.valueOf(readData[0]));
        sSensorOn.setText(String.valueOf(readData[1]));
        sSensorOff.setText(String.valueOf(readData[2]));
        sOntime.setText(String.valueOf(readData[3]));
        sSendGroup.setText(String.valueOf(readData[4]));
        String[] hexData = hexCating(readData);
        sUniqid.setText(String.valueOf(Integer.decode("0x" + hexData[5] + hexData[6])));

        uniqid.setText(String.valueOf(Integer.decode("0x" + hexData[5] + hexData[6])));
        setGroup.setSelection(readData[0]);
        sendGroup.setSelection(readData[4]);
        for (int i = 0; i < 11; i++) {
            if (Integer.parseInt((String) setDimOn.getItemAtPosition(i)) == readData[1])
                setDimOn.setSelection(i);
            if (Integer.parseInt((String) setDimOff.getItemAtPosition(i)) == readData[2])
                setDimOff.setSelection(i);
        }
        keepSec.setText(String.valueOf(readData[3]));
        temp++;
    }

    private void setView2(int[] readData) throws Exception {
        sFixed.setText(String.valueOf(readData[1]) + " " + String.valueOf(readData[2]));
        sSensorState.setText(String.valueOf(readData[0]));

        if (readData[0] == 254) {
            setCri.setSelection(1);
        }
        if (readData[0] == 255) {
            setCri.setSelection(2);
        } else {
            setCri.setSelection(0);
        }

        if (readData[1] == 0) {
            setOper.setSelection(0);
        } else if (readData[1] == 1) {
            setOper.setSelection(1);
        } else if (readData[1] == 5) {
            setOper.setSelection(2);
        } else if (readData[1] == 6) {
            setOper.setSelection(3);
        } else if (readData[1] == 7) {
            setOper.setSelection(4);
        }

        for (int i = 0; i < 11; i++) {
            if (Integer.parseInt((String) fixedDim.getItemAtPosition(i)) == readData[2]) {
                fixedDim.setSelection(i);
            }
        }
        temp++;
    }

    private void setView3(int[] readData) throws Exception {

        if (readData[0] == 254 && readData[1] == 0) {
            groupBtn.setText("그룹 ON");
        } else if (readData[0] == 255 && readData[1] == 0) {
            allBtn.setText("전체 ON");
        } else if (readData[0] == 254 && readData[1] == 100) {
            groupBtn.setText("그룹 OFF");
        } else if (readData[0] == 255 && readData[1] == 100) {
            allBtn.setText("전체 OFF");
        } else {
            groupBtn.setText("그룹 ON");
            allBtn.setText("전체 ON");
        }

        temp = 0;
        proDialog.dismiss();
    }

    private String[] hexCating(int[] data) {
        String[] hexData = new String[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] < 10) {
                hexData[i] = "0" + String.valueOf(data[i]);
            } else if (data[i] == 10) {
                hexData[i] = "0a";
            } else if (data[i] == 11) {
                hexData[i] = "0b";
            } else if (data[i] == 12) {
                hexData[i] = "0c";
            } else if (data[i] == 13) {
                hexData[i] = "0d";
            } else if (data[i] == 14) {
                hexData[i] = "0e";
            } else if (data[i] == 15) {
                hexData[i] = "0f";
            } else {
                hexData[i] = Integer.toHexString(data[i]);
            }
        }
        return hexData;
    }

    private void viewSetting() {
        scanLayout = (LinearLayout) findViewById(R.id.scanLayout);
        stateLayout = (LinearLayout) findViewById(R.id.stateLayout);
        btnLayout = (LinearLayout) findViewById(R.id.btnLayout);
        controlLayout = (LinearLayout) findViewById(R.id.controlLayout);

        macAddr = (TextView) findViewById(R.id.macAddr);
        readState = (TextView) findViewById(R.id.readState);
        sGroupid = (TextView) findViewById(R.id.sGroupid);
        sOntime = (TextView) findViewById(R.id.sOntime);
        sSendGroup = (TextView) findViewById(R.id.sSendGroup);
        sUniqid = (TextView) findViewById(R.id.sUniqid);
        sSensorOn = (TextView) findViewById(R.id.sSensorOn);
        sSensorOff = (TextView) findViewById(R.id.sSensorOff);
        sFixed = (TextView) findViewById(R.id.sFixed);
        sSensorState = (TextView) findViewById(R.id.sSensorState);

        scanBtn = (Button) findViewById(R.id.scanBtn);
        setMainBtn = (Button) findViewById(R.id.setMainBtn);
        setOperBtn = (Button) findViewById(R.id.setOperBtn);
        groupBtn = (Button) findViewById(R.id.groupBtn);
        allBtn = (Button) findViewById(R.id.allBtn);

        setCri = (Spinner) findViewById(R.id.setCri);
        setGroup = (Spinner) findViewById(R.id.setGroup);
        sendGroup = (Spinner) findViewById(R.id.sendGroup);
        setDimOn = (Spinner) findViewById(R.id.setDimOn);
        setDimOff = (Spinner) findViewById(R.id.setDimOff);
        setOper = (Spinner) findViewById(R.id.setOper);
        fixedDim = (Spinner) findViewById(R.id.fixedDim);

        uniqid = (EditText) findViewById(R.id.uniqid);
        keepSec = (EditText) findViewById(R.id.keepSec);

        mainScroll = (ScrollView) findViewById(R.id.mainScroll);
        scroll = (ScrollView) findViewById(R.id.scroll);
        bleList = (ListView) findViewById(R.id.bleList);
    }
}
