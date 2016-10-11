package com.example.yinlijun.networkswitch;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.AsyncTask;
import android.content.Context;
import android.content.IntentFilter;

import android.os.Build;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.concurrent.TimeoutException;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NNN";
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    private IntentFilter mIntentFilter;
    private TrafficStats mStat;
    private TextView mRx;
    private TextView mTx;
    private long mInitRx;
    private long mInitTx;

    private static final int EVENT_GET_STAT = 1;
    private static final int EVENT_GET_DATA = 2;
    private static final int EVENT_ON_DATA = 3;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_GET_STAT:
                    getStat();
                    break;
                case EVENT_GET_DATA:
                    boolean switchNetwork = msg.getData().getBoolean("switch", false);
                    new LongOperation().execute(Boolean.toString(switchNetwork));
                    break;
                case EVENT_ON_DATA:
                    Bundle bundle = msg.getData();
                    TextView code = (TextView)findViewById(R.id.code);
                    code.setText(String.valueOf(bundle.getInt("CODE")));
                    TextView mesage = (TextView)findViewById(R.id.message);
                    mesage.setText(bundle.getString("MESSAGE"));
                    break;
            }
        }
    };

    private void getStat() {
        mTx.setText("tx:" + String.valueOf(mStat.getTotalTxPackets() - mInitTx));
        mRx.setText("rx:" + String.valueOf(mStat.getTotalRxPackets() - mInitRx));
        mHandler.sendEmptyMessageDelayed(EVENT_GET_STAT, 1000);
    }

    private void getData(boolean switchNetwork) throws IOException {
        final Socket socket = new Socket();
        Log.w(TAG, "try to connect server");
        try {
            SocketAddress remoteAddr = new InetSocketAddress("www.baidu.com", 80);
            socket.connect(remoteAddr, 3000);
            socket.close();
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            switchToType(ConnectivityManager.TYPE_MOBILE);
        } finally {
            if(socket != null) {
                socket.close();
            }
        }
    }

    /*
    private void getData(boolean switchNetwork) {
//        if(switchNetwork) {
//            //switch to wifi first
//            switchToType(ConnectivityManager.TYPE_WIFI);
//        }
        URL url = null;
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "try to getData");
            HttpURLConnection.setFollowRedirects(false);
            url = new URL("http://www.baidu.com");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            //connection.setUseCaches(false);
            //connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int code = connection.getResponseCode();
            String str = connection.getResponseMessage();
            notifyData(code, str);
            Log.d(TAG, "getData returns:" + code + ", " + str);

            if(switchNetwork) {
                if (code != 200) {
                    //wifi is not ok, switch to mobile
                    switchToType(ConnectivityManager.TYPE_MOBILE);
                }
            }
        } catch(Exception e) {
            if(switchNetwork) {
                //exception catched, switch to mobile
                switchToType(ConnectivityManager.TYPE_MOBILE);
            }
            Log.d(TAG, "getData caught excption");
            notifyData(-1, "get Data failed");
        } finally {
            if(connection == null) {
                connection.disconnect();
            }
        }
    }
    */

    public void notifyData(int code, String str) {
        Bundle bundle = new Bundle();
        bundle.putInt("CODE", code);
        bundle.putString("MESSAGE", str);

        Message msg = new Message();
        msg.what = EVENT_ON_DATA;
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    public void switchToType(final int type) {
        if(type == ConnectivityManager.TYPE_MOBILE) {
            Log.d(TAG, "disable wifi");
            int netId = mWifiManager.getConnectionInfo().getNetworkId();
            mWifiManager.disableNetwork(netId);

            return;
        }

        final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();

        Log.d(TAG, "switch to " + (type == ConnectivityManager.TYPE_WIFI ? "wifi" : "mobile"));
        // 设置指定的网络传输类型(蜂窝传输) 等于手机网络
        builder.addTransportType(type);

        // 设置感兴趣的网络功能
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // 设置感兴趣的网络：计费网络
        // builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

        NetworkRequest request = builder.build();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                Log.d(TAG, "switch to " + (type == ConnectivityManager.TYPE_WIFI ? "wifi" : "mobile" + " done"));

                // 只要一找到符合条件的网络就注销本callback
                // 你也可以自己进行定义注销的条件
                //connectivityManager.unregisterNetworkCallback(this);

                connectivityManager.bindProcessToNetwork(network);
                // 可以通过下面代码将app接下来的请求都绑定到这个网络下请求
                //if (Build.VERSION.SDK_INT >= 23) {
                //    connectivityManager.bindProcessToNetwork(network);
                //} else {
                    // 23后这个方法舍弃了
                //    ConnectivityManager.setProcessDefaultNetwork(network);
                //}

                // 也可以在将来某个时间取消这个绑定网络的设置
                // if (Build.VERSION.SDK_INT >= 23) {
                //      connectivityManager.bindProcessToNetwork(null);
                //} else {
                //     ConnectivityManager.setProcessDefaultNetwork(null);
                //}
            }
        };
        connectivityManager.requestNetwork(request, callback);

    }

    private boolean isWifiConnected() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        return info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
    }

    private boolean isMobileNetworkConnected() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        return info.getType() == ConnectivityManager.TYPE_MOBILE && info.isConnected();
    }

    private class LongOperation extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            boolean switchNetwork = Boolean.parseBoolean(params[0]);
            try {
                getData(switchNetwork);
            } catch (Exception e) {
              e.printStackTrace();
            }
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_WIFI);
                NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                boolean mobileConnected = false;
                boolean wifiConnected = false;

                Network[] networks = mConnectivityManager.getAllNetworks();
                for(Network network : networks) {
                    NetworkInfo info = mConnectivityManager.getNetworkInfo(network);
                    if(info.getType() == ConnectivityManager.TYPE_MOBILE && info.isConnected()) {
                        mobileConnected = true;
                    }
                    if(info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected()) {
                        wifiConnected = true;

                    }
                }
                boolean currentWifiActive = wifiConnected && networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;

                Log.w(TAG, "mobileConnected:" + mobileConnected + " wifiConnected:" + wifiConnected + " currentWifiActive:" + currentWifiActive);

                if(currentWifiActive) {
                    //wifi is connected
                    checkWifiConnection();
                }
            }
        }
    };

    private void checkWifiConnection() {
        Message msg = new Message();
        msg.what = EVENT_GET_DATA;
        msg.arg1 = 1;
        Bundle bundle = new Bundle();
        bundle.putBoolean("switch", true);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mRx = (TextView) findViewById(R.id.rx);
        mTx = (TextView) findViewById(R.id.tx);
        mInitRx = mStat.getTotalRxPackets();
        mInitTx = mStat.getTotalTxPackets();
        mHandler.sendEmptyMessage(EVENT_GET_STAT);
        //mHandler.sendEmptyMessage(EVENT_GET_DATA);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, mIntentFilter);


        Button rst = (Button)findViewById(R.id.rst);
        rst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mInitRx = mStat.getTotalRxPackets();
                mInitTx = mStat.getTotalTxPackets();
            }
        });
        Button fetch = (Button)findViewById(R.id.fetch);
        fetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.sendEmptyMessage(EVENT_GET_DATA);
            }
        });

        Button mobile = (Button)findViewById(R.id.mobile);
        mobile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //switchToType(NetworkCapabilities.TRANSPORT_CELLULAR);
            }
        });
        Button wifi = (Button)findViewById(R.id.wifi);
        wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //switchToType(NetworkCapabilities.TRANSPORT_WIFI);
            }
        });
    }
}
