package org.newtonproject.newpay.fastbletest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;

import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("Registered")
@EActivity
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSION_LOCATION = 1;
    public static final String DEVICE_NAME = "NewKey";
    private static final String UUID_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String UUID_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private BleDevice bleDevice;

    @ViewById
    TextView textView;

    @ViewById
    ScrollView receiveScrollView;

    @ViewById
    Button connectButton;

    @ViewById
    Button writeButton;

    @ViewById
    Button disconnectButton;

    @ViewById
    ProgressBar progressBar;

    @ViewById
    EditText inputText;

    public void setBleDevice(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
    }

    public BleDevice getBleDevice() {
        return bleDevice;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        writeButton.setEnabled(false);
        disconnectButton.setEnabled(false);

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
    }

    @Click
    void connectButton(){
        checkPermissions();
    }

    @Click
    void writeButton(){
        byte[] data = inputText.getText().toString().getBytes();
        showLog("data length " + data.length);
        writeData(data);
    }

    @Click
    void clearButton(){
        textView.setText("");
    }

    @Click
    void disconnectButton(){
        BleManager.getInstance().disconnect(getBleDevice());
    }

    private void writeData(byte[] data) {
        BleManager.getInstance().write(getBleDevice(), UUID_SERVICE, UUID_CHARACTERISTIC, data, false, bleWriteCallback);
        //false表示不分包发送
    }

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please open bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Notify")
                            .setMessage("You should open gps")
                            .setNegativeButton("cancel",
                                    (dialog, which) -> finish())
                            .setPositiveButton("set",
                                    (dialog, which) -> {
                                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                        startActivityForResult(intent, 2);
                                    })

                            .setCancelable(false)
                            .show();
                } else {
                    setScanRule();
                    startScan();
                }
                break;
        }
    }

    private void setScanRule() {
        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                .setDeviceName(true, DEVICE_NAME)   // 只扫描指定广播名的设备，可选
                .setScanTimeOut(10000)              // 扫描超时时间，可选，默认10秒
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                progressBar.setVisibility(View.VISIBLE);
                BleManager.getInstance().disconnectAllDevice();
                connectButton.setEnabled(false);
                showLog("scan start");
            }

            @Override
            public void onLeScan(BleDevice bleDevice) {
                //未经过滤的所有设备
                super.onLeScan(bleDevice);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                //符合BleScanRuleConfig的设备会出现在这里
                showLog("onScanning : " + bleDevice.getName());
                setBleDevice(bleDevice);
                progressBar.setVisibility(View.INVISIBLE);
                connect(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                showLog("scan finished");
                for (BleDevice bd : scanResultList) {
                    showLog(bd.getName());
                }
                progressBar.setVisibility(View.INVISIBLE);
                connectButton.setEnabled(true);
            }
        });
    }

    private void connect(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                BleManager.getInstance().cancelScan();
                Runnable runnable = () -> {
                    connectButton.setEnabled(false);
                    progressBar.setVisibility(View.VISIBLE);
                };
                Handler handler = new Handler();
                handler.postDelayed(runnable, 5);
                showLog("start connect");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                showLog("connect fail");
                connectButton.setEnabled(true);
                writeButton.setEnabled(false);
                disconnectButton.setEnabled(false);
                progressBar.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                writeButton.setEnabled(true);
                disconnectButton.setEnabled(true);
                connectButton.setEnabled(false);
                showLog("connect succeed , status : " + status);
                BleManager.getInstance().setMtu(bleDevice, 99, new BleMtuChangedCallback() {
                    @Override
                    public void onSetMTUFailure(BleException exception) {
                        showLog("set MTU failed");
                    }

                    @Override
                    public void onMtuChanged(int mtu) {
                        showLog("set MTU succeed , MTU : " + mtu);
                    }
                });
                Runnable runnable = () -> BleManager.getInstance().notify(
                        bleDevice,
                        UUID_SERVICE,
                        UUID_CHARACTERISTIC,
                        new BleNotifyCallback() {
                            @Override
                            public void onNotifySuccess() {
                                showLog("notify succeed");
                                progressBar.setVisibility(View.INVISIBLE);
                            }

                            @Override
                            public void onNotifyFailure(BleException exception) {
                                showLog("notify fail");
                                progressBar.setVisibility(View.INVISIBLE);
                            }

                            @Override
                            public void onCharacteristicChanged(byte[] data) {
                                showLog(new String(data));
                            }
                        });
                Handler handler = new Handler();
                handler.postDelayed(runnable, 100);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                showLog("disconnect ");
                connectButton.setEnabled(true);
                writeButton.setEnabled(false);
                disconnectButton.setEnabled(false);
            }
        });
    }

    private BleWriteCallback bleWriteCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(int current, int total, byte[] justWrite) {
            showLog("Write success");
        }

        @Override
        public void onWriteFailure(BleException exception) {
            showLog("Write fail");
        }
    };

    private void scrollTextToEnd() {
        receiveScrollView.post(() -> receiveScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void showLog(String message) {
        textView.append(message + "\n");
        scrollTextToEnd();
    }
}
