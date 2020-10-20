package com.codedevtech.emplitrack;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationService extends Service {
	private GoogleMap gmap;
	private Polyline path;
	private TextView distance;
	private TextView time;

	public static boolean running=false;

	FusedLocationProviderClient locationProviderClient;
	LocationRequest locationRequest;
	LocationCallback locationCallback = new LocationCallback() {
		@Override
		public void onLocationResult(LocationResult locationResult) {
			super.onLocationResult(locationResult);

			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			final long startTime = preferences.getLong("start", 0);
			if (startTime == 0)
				preferences.edit().putLong("start", new Date().getTime()).apply();

			long endTime = new Date().getTime();
			List<Location> locations = locationResult.getLocations();
			LatLng tmp;
			float[] tf = new float[1];
			List<LatLng> newpoints = new ArrayList<>();
			for (Location l : locations) {
				tmp = new LatLng(l.getLatitude(), l.getLongitude());
				newpoints.add(tmp);
				helper.addLocation(endTime / 1000, tmp.latitude, tmp.longitude);
			}
			if (path != null) {
				List<LatLng> points = path.getPoints();
				points.addAll(newpoints);
				path.setPoints(points);

				double totalDistance = 0;
				for (int i = 1; i < points.size(); i++) {
					Location.distanceBetween(points.get(i - 1).latitude, points.get(i - 1).longitude, points.get(i).latitude, points.get(i).longitude, tf);
					totalDistance += tf[0];
				}
				if (distance != null) {
					double t = totalDistance + MapsActivity.totalDistance;
					String dist = String.format(Locale.US, "%.2f " + (t < 1000 ? "Meters" : "Km"), t < 1000 ? t : t / 1000);
					distance.setText(dist);
				}
				if (time != null)
					time.setText(Utils.timeString(endTime - startTime));
			}
			long lastUploadTime = Utils.getLastUploadTime(getApplicationContext());
			long dif = endTime - lastUploadTime;
			if (dif > 60000 * 15) {
				helper.uploadAfter15Min();
			}
		}
	};

	SQLiteHelper helper;

	public static String START_COMMAND = "ACTION_START_SERVICE";
	public static String STOP_COMMAND = "ACTION_STOP_SERVICE";

	@Override
	public void onCreate() {
		if (helper == null)
			helper = new SQLiteHelper(getApplicationContext());

		try {
			SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a",Locale.US);
			FileOutputStream stream=new FileOutputStream("/sdcard/error.txt",true);
			stream.write(("\nstarted : "+dateFormat.format(new Date())).getBytes());
			stream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		locationProviderClient = LocationServices.getFusedLocationProviderClient(this);

		locationRequest = new LocationRequest();
		locationRequest.setInterval(Utils.INTERVAL);
		locationRequest.setFastestInterval(Utils.FASTEST_INETRVAL);
		locationRequest.setPriority(Utils.PRIORITY);
		locationRequest.setMaxWaitTime(Utils.MAX_WAIT_TIME);
		locationRequest.setSmallestDisplacement(Utils.MIN_DISTANCE);
	}
	@Override
	public void onDestroy(){
		locationProviderClient.removeLocationUpdates(locationCallback);
		if (running){
			Intent intent = new Intent(getApplicationContext(), LocationService.class);
			intent.setAction(LocationService.START_COMMAND);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
				startForegroundService(intent);
			}else
				startService(intent);
		}
		try {
			SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a",Locale.US);
			FileOutputStream stream=new FileOutputStream("/sdcard/error.txt",true);
			stream.write(("\nstopped : "+dateFormat.format(new Date())).getBytes());
			stream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getAction().equals(START_COMMAND)){
			Log.i("start command", "Received Start Intent ");
			helper.addLocation(new Date().getTime() / 1000, 0, 0);
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
				locationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
			}
			createNotification();
		}else if (intent.getAction().equals(STOP_COMMAND)) {
			Log.i("stop command", "Received Stop Intent");
			if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
				stopForeground(true);
				stopSelfResult(startId);
			}
		}
		return START_STICKY;
	}
	private void createNotification(){
		String NOTIFICATION_CHANNEL_ID = "com.codedevtech.emplitrack";
		String channelName = "Background Service";
		NotificationManager manager= (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID,channelName, NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			manager.createNotificationChannel(chan);
		}
		Intent intent=new Intent(this,MapsActivity.class);
		PendingIntent pendingIntent=PendingIntent.getActivity(this,10,intent,PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder notificationBuilder=new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
		notificationBuilder.setOngoing(true)
				.setContentTitle("Emplitrack is Tracking")
				.setContentText("Click to open map")
				.setContentIntent(pendingIntent);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
			notificationBuilder.setPriority(NotificationManager.IMPORTANCE_MIN)
				.setCategory(Notification.CATEGORY_SERVICE);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForeground(4444,notificationBuilder.build());
		}else{
			notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round);
			manager.notify(4444,notificationBuilder.build());
		}
	}
	@Override
	public IBinder onBind(Intent intent){
		return new LocationServiceBinder();
	}
	class LocationServiceBinder extends Binder{
		public Service getService(){
			return LocationService.this;
		}
		public void setup(GoogleMap map, Polyline path,TextView distance,TextView time){
			LocationService.this.gmap=map;
			LocationService.this.path=path;
			LocationService.this.distance=distance;
			LocationService.this.time=time;
		}
	}
}
