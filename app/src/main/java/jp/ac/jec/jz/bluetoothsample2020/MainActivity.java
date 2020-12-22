package jp.ac.jec.jz.bluetoothsample2020;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * Bluetooth 通信用サービス
     * このクラス自体は接続・切断と状態管理だけ。細かい処理は 内部クラスの ConnectionThread が実装。
     */
    static public class BluetoothService
    {
        // 定数（Bluetooth UUID）⇒これはSPP（Serial Port Profile）用のUUIDなんだって。
        // - や小文字表記は大事。
        // SIGによると、1101 部分が可変。
        // 参考：https://jellyware.jp/kurage/bluejelly/uuid.html
        // private static final UUID UUID_SPP = UUID.fromString( "00001101-0000-1000-8000-00805f9b34fb" );

        // CF Shutter はHID（0x1812）を使うらしい。が、これでもCONNECT_FAILED。
        public static final UUID UUID_HID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb");

        // 定数
        public static final int MESSAGE_STATECHANGE    = 1;
        public static final int STATE_NONE             = 0;
        public static final int STATE_CONNECT_START    = 1;
        public static final int STATE_CONNECT_FAILED   = 2;
        public static final int STATE_CONNECTED        = 3;
        public static final int STATE_CONNECTION_LOST  = 4;
        public static final int STATE_DISCONNECT_START = 5;
        public static final int STATE_DISCONNECTED     = 6;

        // メンバー変数
        private int              mState;
        private ConnectionThread mConnectionThread;
        private Handler mHandler;

        /**
         * 接続時処理用のスレッドを表すクラス
         */
        private class ConnectionThread extends Thread {
            private BluetoothSocket mBluetoothSocket;

            /**
             * スレッドのコンストラクタ
             * @param bluetoothdevice
             */
            public ConnectionThread( BluetoothDevice bluetoothdevice )
            {
                try {
                    mBluetoothSocket = bluetoothdevice.createRfcommSocketToServiceRecord( UUID_HID );
                } catch( IOException e ) {
                    Log.e( "BluetoothService", "failed : bluetoothdevice.createRfcommSocketToServiceRecord( UUID_SPP )", e );
                }
            }

            /**
             * スレッド内の処理。切断されるまで繰り返す
             */
            public void run()
            {
                while( STATE_DISCONNECTED != mState ) {
                    switch( mState ) {
                        case STATE_NONE:
                            break;
                        case STATE_CONNECT_START:
                            // 接続開始
                            try {
                                // BluetoothSocketオブジェクトを用いて、Bluetoothデバイスに接続を試みる。
                                mBluetoothSocket.connect();
                            } catch( IOException e ) {
                                // 接続失敗
                                Log.d( "BluetoothService", "Failed : mBluetoothSocket.connect()" );
                                setState( STATE_CONNECT_FAILED );
                                cancel();    // スレッド終了。
                                return;
                            }
                            // 接続成功
                            setState( STATE_CONNECTED );
                            break;
                        case STATE_CONNECT_FAILED:        // 接続失敗
                            // 接続失敗時の処理の実体は、cancel()。
                            break;
                        case STATE_CONNECTED:        // 接続済み（Bluetoothデバイスから送信されるデータ受信）
                            break;
                        case STATE_CONNECTION_LOST:    // 接続ロスト
                            // 接続ロスト時の処理の実体は、cancel()。
                            break;
                        case STATE_DISCONNECT_START:    // 切断開始
                            // 切断開始時の処理の実体は、cancel()。
                            break;
                        case STATE_DISCONNECTED:    // 切断完了
                            // whileの条件式により、STATE_DISCONNECTEDの場合は、whileを抜けるので、このケース分岐は無意味。
                            break;
                    }
                }
                synchronized( BluetoothService.this ) {    // 親クラスが保持する自スレッドオブジェクトの解放（自分自身の解放）
                    mConnectionThread = null;
                }
            }

            /**
             * キャンセル（接続を終了する。ステータスをSTATE_DISCONNECTEDにすることによってスレッドも終了する）
             */
            public void cancel()
            {
                try {
                    mBluetoothSocket.close();
                } catch( IOException e ) {
                    Log.e( "BluetoothService", "Failed : mBluetoothSocket.close()", e );
                }
                setState( STATE_DISCONNECTED );
            }

        }

        /**
         * BluetoothService コンストラクタ
         * @param context
         * @param handler
         * @param device
         */
        public BluetoothService( Context context, Handler handler, BluetoothDevice device )
        {
            mHandler = handler;
            mState = STATE_NONE;

            // 接続時処理用スレッドの作成と開始
            mConnectionThread = new ConnectionThread( device );
            mConnectionThread.start();

            // つかえるUUIDとってみる
            ParcelUuid[] uuidList = device.getUuids();
            for (ParcelUuid id: uuidList) {
                Log.d(TAG, "BluetoothService: UUID: " + id.toString());
            }
        }

        /**
         * ステータスの設定
         * @param state
         */
        private synchronized void setState( int state )
        {
            mState = state;
            mHandler.obtainMessage( MESSAGE_STATECHANGE, state, -1 ).sendToTarget();
        }

        /**
         * 接続開始処理
         */
        public synchronized void connect()
        {
            if( STATE_NONE != mState ) {
                // １つのBluetoothServiceオブジェクトに対して、connect()は１回だけ呼べる。
                // ２回目以降の呼び出しは、処理しない。
                return;
            }

            setState( STATE_CONNECT_START );
        }

        /**
         * 切断処理
         */
        public synchronized void disconnect()
        {
            if( STATE_CONNECTED != mState ) {
                // 接続中以外は、処理しない。
                return;
            }
            setState( STATE_DISCONNECT_START );
            mConnectionThread.cancel();
        }
    }

    /// 定数 ////////////////////////////////////////////////////////////////////
    private static final int REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    private static final int REQUEST_CONNECTDEVICE   = 2; // デバイス接続要求時の識別コード

    public static final String TAG = "DEBUG_MAIN_ACTIVITY";

    /// メンバー変数 ////////////////////////////////////////////////////////////
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private String mDeviceAddress = "";    // デバイスアドレス
    private BluetoothService mBluetoothService;    // BluetoothService : Bluetoothデバイスとの通信処理を担う

    // GUIアイテム
    private Button mButton_Connect;    // 接続ボタン
    private Button mButton_Disconnect;    // 切断ボタン

    // Bluetoothサービスから情報を取得するハンドラ
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler()
    {
        // ハンドルメッセージ
        // UIスレッドの処理なので、UI処理について、runOnUiThread対応は、不要。
        @Override
        public void handleMessage( Message msg )
        {
            switch( msg.what )
            {
                case BluetoothService.MESSAGE_STATECHANGE:
                    switch( msg.arg1 )
                    {
                        case BluetoothService.STATE_NONE:            // 未接続
                            break;
                        case BluetoothService.STATE_CONNECT_START:        // 接続開始
                            break;
                        case BluetoothService.STATE_CONNECT_FAILED:            // 接続失敗
                            Toast.makeText( MainActivity.this, "Failed to connect to the device.", Toast.LENGTH_SHORT ).show();
                            Log.d(TAG, "handleMessage: Failed to connect: " + msg);
                            break;
                        case BluetoothService.STATE_CONNECTED:    // 接続完了
                            // GUIアイテムの有効無効の設定
                            // 切断ボタン、文字列送信ボタンを有効にする
                            mButton_Disconnect.setEnabled( true );
                            break;
                        case BluetoothService.STATE_CONNECTION_LOST:            // 接続ロスト
                            //Toast.makeText( MainActivity.this, "Lost connection to the device.", Toast.LENGTH_SHORT ).show();
                            break;
                        case BluetoothService.STATE_DISCONNECT_START:
                            // GUIアイテムの有効無効の設定
                            // 切断ボタン、文字列送信ボタンを無効にする
                            mButton_Disconnect.setEnabled( false );
                            break;
                        case BluetoothService.STATE_DISCONNECTED:            // 切断完了
                            // GUIアイテムの有効無効の設定
                            // 接続ボタンを有効にする
                            mButton_Connect.setEnabled( true );
                            mBluetoothService = null;    // BluetoothServiceオブジェクトの解放
                            break;
                    }
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // GUIアイテム
        mButton_Connect = (Button)findViewById( R.id.button_connect );
        mButton_Connect.setOnClickListener( this );
        mButton_Disconnect = (Button)findViewById( R.id.button_disconnect );
        mButton_Disconnect.setOnClickListener( this );

        // Android端末がBLEをサポートしてるかの確認
        if( !getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE )) {
            Toast.makeText( this, R.string.ble_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter ) {
            // Android端末がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
        mButton_Connect.setEnabled( false );
        mButton_Disconnect.setEnabled( false );

        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if( !mDeviceAddress.equals( "" ) )
        {
            mButton_Connect.setEnabled( true );
        }

        // 接続ボタンを押す
        mButton_Connect.callOnClick();
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature() {
        if( mBluetoothAdapter.isEnabled() ) {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }
    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();

        // 切断
        disconnect();
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if( null != mBluetoothService )
        {
            mBluetoothService.disconnect();
            mBluetoothService = null;
        }
    }

    // Bluetooth有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        switch( requestCode )
        {
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode )
                {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case REQUEST_CONNECTDEVICE: // デバイス接続要求
                String strDeviceName;
                if( Activity.RESULT_OK == resultCode )
                {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_NAME );
                    mDeviceAddress = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_ADDRESS );
                }
                else
                {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ( (TextView)findViewById( R.id.textview_devicename ) ).setText( strDeviceName );
                ( (TextView)findViewById( R.id.textview_deviceaddress ) ).setText( mDeviceAddress );
                break;
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_main, menu );
        return true;
    }

    /**
     * 接続ボタンの動作
     * @param v
     */
    @Override
    public void onClick( View v )
    {
        if( mButton_Connect.getId() == v.getId() )
        {
            mButton_Connect.setEnabled( false );    // 接続ボタンの無効化（連打対策）
            connect();            // 接続
            return;
        }
        if( mButton_Disconnect.getId() == v.getId() )
        {
            mButton_Disconnect.setEnabled( false );    // 切断ボタンの無効化（連打対策）
            disconnect();            // 切断
            return;
        }
    }

    /**
     * 接続処理
     */
    private void connect()
    {
        if( mDeviceAddress.equals( "" ) )
        {    // DeviceAddressが空の場合は処理しない
            return;
        }

        if( null != mBluetoothService )
        {    // mBluetoothServiceがnullでないなら接続済みか、接続中。
            return;
        }

        // 接続
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( mDeviceAddress );
        mBluetoothService = new BluetoothService( this, mHandler, device );
        mBluetoothService.connect();
    }

    /**
     * 切断処理
     */
    private void disconnect()
    {
        if( null == mBluetoothService )
        {    // mBluetoothServiceがnullなら切断済みか、切断中。
            return;
        }

        // 切断
        mBluetoothService.disconnect();
        mBluetoothService = null;
    }

    /// メニュー関連 //////////////////////////////////////////////////////////////
    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )
        {
            case R.id.menuitem_search:
                Intent devicelistactivityIntent = new Intent( this, DeviceListActivity.class );
                startActivityForResult( devicelistactivityIntent, REQUEST_CONNECTDEVICE );
                return true;
        }
        return false;
    }

}