package jp.ac.jec.jz.bluetoothsample2020;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceListActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    /**
     * Bluetooth デバイス管理クラス
     */
    static class DeviceListAdapter extends BaseAdapter
    {
        private ArrayList<BluetoothDevice> mDeviceList;
        private LayoutInflater mInflator;

        /**
         * constructor
         * @param activity
         */
        public DeviceListAdapter( Activity activity )
        {
            super();
            mDeviceList = new ArrayList<BluetoothDevice>();
            mInflator = activity.getLayoutInflater();
        }

        /**
         * リストにデバイスを追加する
         */
        public void addDevice( BluetoothDevice device )
        {
            if( !mDeviceList.contains( device ) )
            {    // 加えられていなければ加える
                mDeviceList.add( device );
                notifyDataSetChanged();    // ListViewの更新
            }
        }

        /**
         * リストをクリアする
         */
        public void clear()
        {
            mDeviceList.clear();
            notifyDataSetChanged();    // ListViewの更新
        }

        @Override
        public int getCount()
        {
            return mDeviceList.size();
        }

        @Override
        public Object getItem( int position )
        {
            return mDeviceList.get( position );
        }

        @Override
        public long getItemId( int position )
        {
            return position;
        }

        /**
         * ListView に表示するためのホルダクラス
         */
        static class ViewHolder
        {
            TextView deviceName;
            TextView deviceAddress;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent )
        {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if( null == convertView ) {
                convertView = mInflator.inflate( R.layout.listitem_device, parent, false );
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView)convertView.findViewById( R.id.textview_deviceaddress );
                viewHolder.deviceName = (TextView)convertView.findViewById( R.id.textview_devicename );
                convertView.setTag( viewHolder );
            } else {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            BluetoothDevice device     = mDeviceList.get( position );
            String          deviceName = device.getName();
            if( null != deviceName && 0 < deviceName.length() ) {
                viewHolder.deviceName.setText( deviceName );
            } else {
                viewHolder.deviceName.setText( R.string.unknown_device );
            }
            viewHolder.deviceAddress.setText( device.getAddress() );

            return convertView;
        }
    }

    // 定数 ////////////////////////////////////////////////////////////////////////////
    private static final int    REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    public static final  String EXTRAS_DEVICE_NAME      = "DEVICE_NAME";
    public static final  String EXTRAS_DEVICE_ADDRESS   = "DEVICE_ADDRESS";

    /// メンバー変数 ///////////////////////////////////////////////////////////////////
    private BluetoothAdapter  mBluetoothAdapter;        // BluetoothAdapter : Bluetooth処理で必要
    private DeviceListAdapter mDeviceListAdapter;    // リストビューの内容
    private boolean mScanning = false;                // スキャン中かどうかのフラグ

    /**
     * ブロードキャストレシーバー
     * Bluetooth 端末検出を検知したらUIスレッドに書き込み
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent )
        {
            String action = intent.getAction();

            // Bluetooth端末発見
            if( BluetoothDevice.ACTION_FOUND.equals( action ) ) {
                final BluetoothDevice device = intent.getParcelableExtra( BluetoothDevice.EXTRA_DEVICE );
                runOnUiThread( () -> {
                        mDeviceListAdapter.addDevice( device );
                    });
                return;
            }
            // Bluetooth端末検索終了
            if( BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals( action ) ) {
                runOnUiThread( () -> {
                    mScanning = false;
                    invalidateOptionsMenu();    // メニューの更新
                });
                return;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        // 戻り値の初期化
        setResult( Activity.RESULT_CANCELED );
        // リストビューの設定
        mDeviceListAdapter = new DeviceListAdapter( this ); // ビューアダプターの初期化
        ListView listView = findViewById( R.id.devicelist );    // リストビューの取得
        listView.setAdapter( mDeviceListAdapter );    // リストビューにビューアダプターをセット
        listView.setOnItemClickListener( this ); // クリックリスナーオブジェクトのセット

        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if( null == mBluetoothAdapter )
        {    // デバイス（＝スマホ）がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }
    }

    /// events /////////////////////////////////////////////////////////////////////////////
    /**
     * リストビューのアイテムクリック時の処理
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id )
    {
        // クリックされたアイテムの取得
        BluetoothDevice device = (BluetoothDevice)mDeviceListAdapter.getItem( position );
        if( null == device ) {
            return;
        }
        // 戻り値の設定
        Intent intent = new Intent();
        intent.putExtra( EXTRAS_DEVICE_NAME, device.getName() );
        intent.putExtra( EXTRAS_DEVICE_ADDRESS, device.getAddress() );
        setResult( Activity.RESULT_OK, intent );
        finish();
    }

    /**
     * 初回表示時、および、ポーズからの復帰時
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();

        // ブロードキャストレシーバーの登録
        registerReceiver( mBroadcastReceiver, new IntentFilter( BluetoothDevice.ACTION_FOUND ) );
        registerReceiver( mBroadcastReceiver, new IntentFilter( BluetoothAdapter.ACTION_DISCOVERY_FINISHED ) );

        // スキャン開始
        startScan();
    }

    /**
     * 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
     */
    @Override
    protected void onPause()
    {
        super.onPause();

        // スキャンの停止
        stopScan();

        // ブロードキャストレシーバーの登録解除
        unregisterReceiver( mBroadcastReceiver );
    }

    /**
     * デバイスのBluetooth機能の有効化要求
     */
    private void requestBluetoothFeature()
    {
        if( mBluetoothAdapter.isEnabled() )
        {
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }

    /**
     * 機能の有効化ダイアログの操作結果
     * @param requestCode
     * @param resultCode
     * @param data
     */
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
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    /**
     * スキャンの開始
     */
    private void startScan()
    {
        // リストビューの内容を空にする。
        mDeviceListAdapter.clear();

        // スキャンの開始
        mScanning = true;
        mBluetoothAdapter.startDiscovery();	// 約 12 秒間の問い合わせのスキャンが行われる

        // メニューの更新
        invalidateOptionsMenu();
    }

    /**
     * スキャンの停止
     */
    private void stopScan()
    {
        // スキャンの停止
        mBluetoothAdapter.cancelDiscovery();
    }

    /// メニュー関連 ///////////////////////////////////////////////////////
    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_device_list, menu );
        if( !mScanning )
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( false );
            menu.findItem( R.id.menuitem_scan ).setVisible( true );
            menu.findItem( R.id.menuitem_progress ).setActionView( null );
        }
        else
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( true );
            menu.findItem( R.id.menuitem_scan ).setVisible( false );
            menu.findItem( R.id.menuitem_progress ).setActionView( R.layout.actionbar_indeterminate_progress );
        }
        return true;
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() ) {
            case R.id.menuitem_scan:
                startScan();    // スキャンの開始
                break;
            case R.id.menuitem_stop:
                stopScan();    // スキャンの停止
                break;
        }
        return true;
    }
}