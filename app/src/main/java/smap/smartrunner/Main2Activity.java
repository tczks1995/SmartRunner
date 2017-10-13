package smap.smartrunner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.nfc.Tag;
import android.os.Build;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;

import android.util.Log;
import android.widget.ImageButton;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.baidu.mapapi.map.MyLocationConfiguration.LocationMode;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.OnStartTraceListener;
import com.baidu.trace.OnStopTraceListener;
import com.baidu.trace.OnTrackListener;
import com.baidu.trace.Trace;
import com.baidu.trace.OnGeoFenceListener;
import com.baidu.trace.OnEntityListener;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;


public class Main2Activity extends AppCompatActivity {
    //地图、位置、轨迹初始化

    private static MapView mMapView = null;
    private static BaiduMap mBaiduMap=null;
    private static OnEntityListener mEntityListenner =null;
    private RefreshThread mRefreshThread = null;  //刷新地图线程以获取实时点
    private static boolean isFirstLocate=true;
    private static boolean isFirstTrack=true;
    private static LatLng start_LatLng;
    //private static LatLng End_LatLng;
    private static double mDistance=0;
    private static MapStatusUpdate mMapstatusUpdate=null;
    private static BitmapDescriptor mRealtimeBitap=null;
    private static List<LatLng> mPointList =new ArrayList<LatLng>();
    private static PolylineOptions mPolyline =null;
    public LocationClient mLocationClient;
    private TextView positionText,TrackText;
    public MyLocationListener locationListener=new MyLocationListener();
    private Trace trace;  // 实例化轨迹服务
    private LBSTraceClient client;  // 实例化轨迹服务客户端
    long serviceId = 132568 ;
    String entityName = "myTrace";
    int traceType = 2;
    int gatherInterval = 1;
    int packInterval = 1;// 打包周期
    int protocolType = 1;// http协议类型
    OnStartTraceListener  mStartTraceListener=null;
    OnStopTraceListener mStopTraceListener=null;
    OnTrackListener mTrackListener=null;
    DistanceUtil mDistanceUtil=new DistanceUtil();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationClient =new LocationClient(getApplicationContext());
        mLocationClient.registerLocationListener(locationListener);
        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main2);
        //初始化数据显示Text
        positionText=(TextView)findViewById(R.id.textView2);
        TrackText=(TextView)findViewById(R.id.textView3);
        // 获取权限
        List<String> permissionList=new ArrayList<>();
        if(ContextCompat.checkSelfPermission(Main2Activity.this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(Main2Activity.this, Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(Main2Activity.this, Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if(ContextCompat.checkSelfPermission(Main2Activity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED)
        {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!permissionList.isEmpty()){
            String[] permissions=permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(Main2Activity.this,permissions,1);
        }
        else{
            requestLocation();
        }
        //获取地图控件引用
         mMapView = (MapView) findViewById(R.id.bmapView);
         mBaiduMap= mMapView.getMap();
        //标出当前位置
        mBaiduMap.setMyLocationEnabled(true);
    }

    public void IMB_onClick(View v) {
        Log.d("TAG","已经按按钮");
        requestLocation();
        BDLocation LastLocation=mLocationClient.getLastKnownLocation();
        LatLng mLatLng = new LatLng(LastLocation.getLatitude(), LastLocation.getLongitude());
        mMapstatusUpdate = MapStatusUpdateFactory.newLatLng(mLatLng);
        mBaiduMap.animateMapStatus(mMapstatusUpdate);
    }

    public void TGB_onClick(View v){
        ToggleButton TGB =(ToggleButton)findViewById(R.id.toggleButton);
        client = new LBSTraceClient(getApplicationContext());
        trace = new Trace(getApplicationContext(), serviceId, entityName, traceType);
        client. setInterval(gatherInterval, packInterval);// 设置采集和打包周期
        client. setProtocolType (protocolType);// 设置http协议类型

        if(TGB.isChecked())
        {
            Log.d("TGB STATUS","On");
            Toast.makeText(Main2Activity.this,"Track On",Toast.LENGTH_LONG).show();
            initOnEntityListener();
            mStartTraceListener = new OnStartTraceListener() {

                @Override
                public void onTraceCallback(int arg0, String arg1) {  // 开启轨迹服务回调接口（arg0 : 消息编码，arg1 : 消息内容，详情查看类参考）
                    Log.i("TAG", "onTraceCallback=" + arg1);
                    if(arg0 == 0 || arg0 == 10006){
                        startRefreshThread(true);
                    }
                }

                @Override
                public void onTracePushCallback(byte arg0, String arg1) {
                    Log.i("TAG", "onTracePushCallback=" + arg1);
                }  // 轨迹服务推送接口（用于接收服务端推送消息，arg0 : 消息类型，arg1 : 消息内容，详情查看类参考）
            };
            client.startTrace(trace, mStartTraceListener);//开启轨迹服务
            mTrackListener =new OnTrackListener() {
                @Override
                public void onRequestFailedCallback(String msg) {
                    Log.i("TAG","onRequestFailedCallback"+msg);
                }
            };
        }
        else
        {
            Log.d("TGB STATUS","Off");
            Toast.makeText(Main2Activity.this,"Track Off",Toast.LENGTH_LONG).show();
            mStopTraceListener = new OnStopTraceListener(){
                // 轨迹服务停止成功
                @Override
                public void onStopTraceSuccess() {
                    Log.i("TAG", "onStopTraceSuccess= 服务开启" );
                    EndRefreshThread(true);
                }
                // 轨迹服务停止失败（arg0 : 错误编码，arg1 : 消息内容，详情查看类参考）
                @Override
                public void onStopTraceFailed(int arg0, String arg1) {
                    Log.i("TAG", "onStopTraceFailed=" + arg1);
                    EndRefreshThread(true);
                }
            };

            //停止轨迹服务
            client.stopTrace(trace,mStopTraceListener);
        }

    }

    private void requestLocation(){
        initialLocation();
        mLocationClient.start();
    }

    private void initialLocation(){
        LocationClientOption options = new LocationClientOption();
        options.setScanSpan(1000);//设置位置刷新时间
        options.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        options.setCoorType("bd09ll");
        mLocationClient.setLocOption(options);
    }

    @Override
    public void onRequestPermissionsResult(int requesrCode,String[] permissions,int[] grantResults){
        switch(requesrCode){
            case 1:
                if(grantResults.length>0){
                    for(int result:grantResults){
                        if(result!=PackageManager.PERMISSION_GRANTED){
                            Toast.makeText(this,"Permission Denied",Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                    requestLocation();
                }else{
                    Toast.makeText(this,"Unknown Error",Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
        mBaiduMap.setMyLocationEnabled(false);
    }
    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }

    private void navigateTo(BDLocation location) {
        if (isFirstLocate) {
            LatLng mLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            MapStatusUpdate mMapstatusUpdate = MapStatusUpdateFactory.newLatLng(mLatLng);
            mBaiduMap.animateMapStatus(mMapstatusUpdate);
            isFirstLocate=false;
        }
        MyLocationData.Builder locationBuilder=new MyLocationData.Builder();
        locationBuilder.latitude(location.getLatitude());
        locationBuilder.longitude(location.getLongitude());
        MyLocationData locationData=locationBuilder.build();
        mBaiduMap.setMyLocationData(locationData);
    }

    private void initOnEntityListener(){

        //实体状态监听器
        mEntityListenner = new OnEntityListener(){

            @Override
            public void onRequestFailedCallback(String arg0) {
                Looper.prepare();
                Toast.makeText(
                        getApplicationContext(),
                        "entity请求失败的回调接口信息："+arg0,
                        Toast.LENGTH_SHORT)
                        .show();
                Looper.loop();
            }

            @Override
            public void onQueryEntityListCallback(String arg0) {
                /**
                 * 查询实体集合回调函数，此时调用实时轨迹方法
                 */
                showRealtimeTrack(arg0);
            }

        };
    }

    protected void showRealtimeTrack(String realtimeTrack){

        if(mRefreshThread == null || !mRefreshThread.refresh){
            return;
        }
        //数据以JSON形式存取
        RealTimeTrackData realtimeTrackData = GsonService.parseJson(realtimeTrack, RealTimeTrackData.class);
        if(realtimeTrackData != null && realtimeTrackData.getStatus() ==0){
            LatLng latLng = realtimeTrackData.getRealtimePoint();
            if(latLng != null){
                mPointList.add(latLng);
                drawRealtimePoint(latLng);
                if(isFirstTrack)
                {
                    isFirstTrack=false;
                    Log.i("TAG","FirstTrack");
                    start_LatLng = latLng;
                }
                else{
                    Log.i("TAG","ContinueTrack");
                    double tempDistace=DistanceUtil.getDistance(start_LatLng,latLng);
                    mDistance=mDistance+tempDistace;
                    start_LatLng=latLng;
                    Log.i("TAG","Distance="+mDistance);
                    TrackText.setText("运动里程："+mDistance);
                }
            }
            else{
                Toast.makeText(getApplicationContext(), "当前无轨迹点", Toast.LENGTH_LONG).show();
            }

        }

    }

    /**
     * 画出实时线路点
     * @param point
     */
    private void drawRealtimePoint(LatLng point){

        mBaiduMap.clear();
        MapStatus mapStatus = new MapStatus.Builder().target(point).zoom(18).build();
        mMapstatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
        mRealtimeBitap = BitmapDescriptorFactory.fromResource(R.drawable.ic_loc);
        if(mPointList.size() >= 2  && mPointList.size() <= 1000){
            mPolyline = new PolylineOptions().width(10).color(Color.RED).points(mPointList);
        }

        addMarker();

    }
    private void addMarker(){

        if(mMapstatusUpdate != null){
            mBaiduMap.setMapStatus(mMapstatusUpdate);
        }

        if(mPolyline != null){
            mBaiduMap.addOverlay(mPolyline);
        }

    }
    private void startRefreshThread(boolean isStart){

        if(mRefreshThread == null){
            mRefreshThread = new RefreshThread();
        }

        mRefreshThread.refresh = isStart;

        if(isStart){
            if(!mRefreshThread.isAlive()){
                mRefreshThread.start();
            }
        }
        else{
            mRefreshThread = null;
        }

    }

    private void EndRefreshThread(boolean isEnd){

        mRefreshThread = new RefreshThread();
        mRefreshThread.refresh = !isEnd;

        if(isEnd){
            if(mRefreshThread.isAlive()){
                mRefreshThread.interrupt();
            }
        }


    }

    private void queryRealtimeTrack(){

        String entityName = this.entityName;
        String columnKey = "";
        int returnType = 0;
        int activeTime = 0;
        int pageSize = 100;
        int pageIndex =1;

        this.client.queryEntityList(
                serviceId,
                entityName,
                columnKey,
                returnType,
                activeTime,
                pageSize,
                pageIndex,
                mEntityListenner
        );

    }

    public class MyLocationListener implements BDLocationListener
    {
        @Override
        public void onReceiveLocation(BDLocation location)
        {
            StringBuilder currentPosition =new StringBuilder();
            currentPosition.append("经度：").append(location.getLongitude()).append("\n");
            currentPosition.append("纬度: ").append(location.getLatitude()).append("\n");
            currentPosition.append("高度：").append(location.getAltitude()).append("\n");
            currentPosition.append("精度：").append(location.getRadius()).append("\n");
           if(location.getLocType()==BDLocation.TypeNetWorkLocation)
            {
                currentPosition.append("Network");
            }else if(location.getLocType()==BDLocation.TypeGpsLocation)
            {
                currentPosition.append("GPS\n").append(location.getGpsAccuracyStatus());
            }
            positionText.setText(currentPosition);
            navigateTo(location);
        }



    }

    private class RefreshThread extends Thread {
        protected boolean refresh = true;

        public void run() {

            while (refresh) {
                queryRealtimeTrack();
                try {
                    Thread.sleep(packInterval * 1000);
                } catch (InterruptedException e) {
                    System.out.println("线程休眠失败");
                }
            }

        }
    }

}


