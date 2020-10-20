package com.codedevtech.emplitrack;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.codedevtech.emplitrack.adapters.MapMeetingAdapter;
import com.codedevtech.emplitrack.adapters.MyMapInfoWindowAdapter;
import com.codedevtech.emplitrack.model.Visit;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.google.android.material.textfield.TextInputEditText;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
	SQLiteHelper helper;

	ArrayList<Polyline> paths;
	ArrayList<Visit> visits = new ArrayList<>();
	Visit pendingVisit;
	private GoogleMap gmap;
	private Polyline path;
	public static double totalDistance;
	public static long totalTime;
	public static boolean following = false;
	MapMeetingAdapter meetingAdapter;

	private TextView distanceText;
	private TextView timeText;
	private Button mainButton;
	private FloatingActionButton breakContinueBtn;
	private SlidingUpPanelLayout slidingUpPanelLayout;

	private LocationService.LocationServiceBinder serviceBinder;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_maps);

		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | getWindow().getDecorView().getSystemUiVisibility());
		}

		SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
		assert mapFragment != null;
		mapFragment.getMapAsync(this);
		slidingUpPanelLayout = findViewById(R.id.map_layout);

		distanceText = findViewById(R.id.map_panel_distance);
		timeText = findViewById(R.id.map_panel_time);
		helper = new SQLiteHelper(this);
		setupButtons();
		onGPS();
	}

	@Override
	public void onMapReady(GoogleMap googleMap) {
		gmap=googleMap;
		gmap.setMaxZoomPreference(16.3f);

		RecyclerView visitList = findViewById(R.id.swipe_up_meeting_recycler_view);
		visitList.setLayoutManager(new LinearLayoutManager(this));
		meetingAdapter = new MapMeetingAdapter(this, visits, slidingUpPanelLayout,gmap);
		visitList.setAdapter(meetingAdapter);

		gmap.setPadding(0, (int) Utils.pixel(24, this), 0, (int) Utils.pixel(10, this));
		gmap.getUiSettings().setCompassEnabled(false);
		gmap.getUiSettings().setMyLocationButtonEnabled(false);
		gmap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
			@Override
			public void onCameraMoveStarted(int i) {
				if (i == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)
					following = false;
			}
		});
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Snackbar.make(slidingUpPanelLayout, "Location permission require", Snackbar.LENGTH_SHORT).show();
			return;
		}
		gmap.setMyLocationEnabled(true);
		LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
		Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (location == null)
			location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (location != null) {
			gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
		}
		gmap.setInfoWindowAdapter(new MyMapInfoWindowAdapter(getApplicationContext()));
		gmap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
			@Override
			public boolean onMarkerClick(Marker marker) {
				marker.showInfoWindow();
				return false;
			}
		});
		pendingVisit = null;
		ArrayList<Visit> visits1 = helper.getVisits();
		if (visits1 != null && visits1.size() > 0) {
			visits.clear();
			for (Visit v : visits1) {
				if (v.end_time > 0) {
					v.marker = addMark(v.latlng);
					v.marker.setTitle(v.client_name);
					v.marker.setSnippet("" + v.start_time);
					visits.add(v);
				} else {
					pendingVisit = v;
					pendingVisit.marker = addMark(pendingVisit.latlng);
				}
			}
			if (meetingAdapter != null) {
				meetingAdapter.notifyDataSetChanged();
			}
		}
		loadPaths();
		if (pendingVisit != null && pendingVisit.latlng != null) {
			String s = "End Visit";
			mainButton.setText(s);
			mainButton.setBackgroundResource(R.drawable.rounded_corner_red_dark);
			addMark(pendingVisit.latlng);
		}else if (paths!=null && paths.size()>0) {
			mainButton.setText(R.string.mark);
			mainButton.setBackgroundResource(R.drawable.rounded_corner_green_dark);
		}else {
			mainButton.setText(R.string.start);
			mainButton.setBackgroundResource(R.drawable.btn_background);
		}

		if (serviceBinder!=null)
			serviceBinder.setup(gmap,path,distanceText,timeText);
	}

	private ServiceConnection serviceConnection=new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder){
			serviceBinder= (LocationService.LocationServiceBinder) iBinder;
			serviceBinder.setup(gmap,path,distanceText,timeText);
		}
		@Override
		public void onServiceDisconnected(ComponentName componentName) { }
	};
	@Override
	protected void onStart() {
		super.onStart();
		if (gmap!=null)
			loadPaths();
		Intent intent = new Intent(getApplicationContext(), LocationService.class);
		MapsActivity.this.bindService(intent,serviceConnection,BIND_AUTO_CREATE);
	}
	@Override
	protected void onStop() {
		super.onStop();
		if (paths!=null)
		for (Polyline line:paths)
			line.remove();
		paths=null;
		if (path!=null)
			path.remove();
		path=null;
		MapsActivity.this.unbindService(serviceConnection);
	}

	private void setupButtons() {
		mainButton = findViewById(R.id.map_panel_main_btn);
		breakContinueBtn=findViewById(R.id.break_continue_button);
		if (LocationService.running)
			breakContinueBtn.setImageResource(R.drawable.ic_location);
		else
			breakContinueBtn.setImageResource(R.drawable.ic_add);
		breakContinueBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view){ breakContinueClick(view); }
		});
		mainButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				String text=mainButton.getText().toString();
				if (text.equals("Start")) {
					breakContinueClick(view);
					mainButton.setText(R.string.mark);
					mainButton.setBackgroundResource(R.drawable.rounded_corner_green_dark);
				}else if (!mainButton.getText().toString().equals("End Visit")) {
					AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this)
							.setTitle("Confirm")
							.setMessage("Are you sure you want to mark visit?")
							.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									if (path != null) {
										List<LatLng> ll = path.getPoints();
										if (ll.size() > 0) {
											LatLng last = ll.get(ll.size() - 1);
											pendingVisit = helper.startVisit(last);
											String s = "End Visit";
											mainButton.setText(s);
											mainButton.setBackgroundResource(R.drawable.rounded_corner_red_dark);
											pendingVisit.marker = addMark(last);
										} else {
											Toast.makeText(getApplicationContext(), "Route Not Found", Toast.LENGTH_SHORT).show();
										}
									} else {
										Toast.makeText(getApplicationContext(), "Route Not Found!", Toast.LENGTH_SHORT).show();
									}
								}
							})
							.setNegativeButton("No", null);
					final AlertDialog dialog = builder.create();
					dialog.setOnShowListener(new DialogInterface.OnShowListener() {
						@Override
						public void onShow(DialogInterface dialogInterface) {
							Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
							btn.setBackgroundColor(Color.WHITE);
							btn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
							btn.setBackgroundColor(Color.WHITE);
						}
					});
					dialog.show();
				} else {
					LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
					final LinearLayout dialogLayout = (LinearLayout) inflater.inflate(R.layout.visit_form, null, false);
					AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this)
							.setCancelable(true)
							.setTitle("Visit Details")
							.setView(dialogLayout)
							.setPositiveButton("Save", null)
							.setNegativeButton("Cancel", null);
					final AlertDialog dialog = builder.create();
					dialog.setOnShowListener(new DialogInterface.OnShowListener() {
						@Override
						public void onShow(DialogInterface dialogInterface) {
							Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
							btn.setBackgroundColor(Color.WHITE);
							btn.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									TextInputEditText clientName = dialogLayout.findViewById(R.id.visit_form_client_name);
									String cl_name = clientName.getText().toString();

									TextInputEditText companyName = dialogLayout.findViewById(R.id.visit_form_company_name);
									String cm_name = companyName.getText().toString();

									TextInputEditText remarkEdtx = dialogLayout.findViewById(R.id.visit_form_remark);
									String remark = remarkEdtx.getText().toString();

									RadioGroup group = dialogLayout.findViewById(R.id.visit_form_status);
									RadioButton btn = dialogLayout.findViewById(group.getCheckedRadioButtonId());
									String status = btn.getText().toString();

									String s = "Mark Visit";
									mainButton.setText(s);
									mainButton.setBackgroundResource(R.drawable.rounded_corner_green_dark);
									pendingVisit.client_name = cl_name;
									pendingVisit.company_name = cm_name;
									pendingVisit.remark = remark;
									int st;
									if (status.equals("Positive"))
										st = 1;
									else if (status.equals("Negative"))
										st = -1;
									else st = 0;
									pendingVisit.status = st;
									pendingVisit.end_time = new Date().getTime();
									pendingVisit.marker.setTitle(pendingVisit.client_name);
									pendingVisit.marker.setSnippet("" + pendingVisit.start_time);
									helper.endVisit(pendingVisit);
									visits.add(pendingVisit);
									if (meetingAdapter != null) {
										meetingAdapter.notifyDataSetChanged();
										meetingAdapter.notifyItemChanged(visits.size() - 2);
									}
									if (!cl_name.isEmpty() && !cm_name.isEmpty())
										dialog.dismiss();
								}
							});
							btn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
							btn.setBackgroundColor(Color.WHITE);
						}
					});
					dialog.show();
				}
			}
		});
	}

	public void breakContinueClick(View view){
		int icon;
		Intent intent = new Intent(getApplicationContext(), LocationService.class);
		if(LocationService.running){
			LocationService.running=false;
			intent.setAction(LocationService.STOP_COMMAND);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
				startService(intent);
			}else{
				stopService(intent);
				NotificationManager manager= (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				manager.cancel(4444);
			}
			icon=R.drawable.ic_add;
		}else{
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			preferences.edit().putLong("start", new Date().getTime()).apply();

			path=gmap.addPolyline(new PolylineOptions());
			path.setColor(Utils.getColor(MapsActivity.this,R.color.colorPrimary));
			if (serviceBinder!=null)
				serviceBinder.setup(gmap,path,distanceText,timeText);

			LocationService.running=true;
			intent.setAction(LocationService.START_COMMAND);
			if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
				startForegroundService(intent);
			}else
				startService(intent);
			icon=R.drawable.ic_location;
		}
		breakContinueBtn.setImageResource(icon);
	}

	private void loadPaths() {
		ArrayList<ArrayList<Pair<Long, LatLng>>> paths = helper.getLocationArray();
		totalDistance = 0;
		totalTime = 0;
		if (this.path!=null)
			this.path.remove();
		this.path=null;
		this.paths = new ArrayList<>();
		if (paths!=null)
		for (ArrayList<Pair<Long, LatLng>> linedata : paths) {
			PolylineOptions options = new PolylineOptions();
			LatLng lastPoint = null;
			float[] tf = new float[1];
			long start = 0, end = 0;
			for (Pair<Long, LatLng> point : linedata) {
				options.add(point.second);
				if (start == 0)
					start = point.first;
				end = point.first;
				if (lastPoint == null)
					lastPoint = point.second;
				else {
					Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, point.second.latitude, point.second.longitude, tf);
					totalDistance += tf[0];
					lastPoint = point.second;
				}
			}
			totalTime += end - start;
			if (path!=null)
				this.paths.add(path);
			path = gmap.addPolyline(options);
			path.setColor(Utils.getColor(this,R.color.colorPrimary));
		}
		if (distanceText != null)
			distanceText.setText(String.format(Locale.US, "%.2f " + (totalDistance > 1000 ? "Km" : "Meters") + " Driven", (totalDistance > 1000 ? totalDistance / 1000 : totalDistance)));
		if (timeText != null) {
			timeText.setText(Utils.timeString(0));
		}
		slidingUpPanelLayout.setTouchEnabled(true);
		slidingUpPanelLayout.setDragView(findViewById(R.id.map_panel_head));
	}

	public void myLocation(View view) {
		if (gmap != null) {
			LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				return;
			}
			Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 17));
				following = true;
			}
		}
	}

	public void back(View view) {
		onBackPressed();
	}

	@Override
	public void onBackPressed() {
		if (slidingUpPanelLayout.getPanelState()== SlidingUpPanelLayout.PanelState.EXPANDED)
			slidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
		else
			super.onBackPressed();
	}

	public Marker addMark(LatLng position){
		Drawable icon= ContextCompat.getDrawable(this,R.drawable.visit_marker);
		assert icon != null;
		icon.setBounds(0,0,icon.getIntrinsicWidth(),icon.getIntrinsicHeight());
		Bitmap bmp=Bitmap.createBitmap(icon.getIntrinsicWidth(),icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas=new Canvas(bmp);
		icon.draw(canvas);
		MarkerOptions options=new MarkerOptions()
				.position(position)
				.icon(BitmapDescriptorFactory.fromBitmap(bmp));
		Marker m=gmap.addMarker(options);
		m.setInfoWindowAnchor(0.5f,0.7f);
		return m;
	}

	public void focusAllPaths(View view){
		if (paths!=null){
			LatLngBounds.Builder builder=LatLngBounds.builder();
			boolean included=false;
			for (Polyline polyline:paths){
				List<LatLng> points = polyline.getPoints();
				for (LatLng point : points){
					included=true;
					builder.include(point);
				}
			}
			if (path!=null) {
				List<LatLng> points = path.getPoints();
				for (LatLng point : points){
					included = true;
					builder.include(point);
				}
			}
			if (included)
				gmap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
		}
	}

	private void onGPS(){
		LocationRequest locationRequest = LocationRequest.create();
		locationRequest.setInterval(Utils.INTERVAL);
		locationRequest.setFastestInterval(Utils.FASTEST_INETRVAL);
		locationRequest.setPriority(Utils.PRIORITY);
		locationRequest.setMaxWaitTime(Utils.MAX_WAIT_TIME);

		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
		builder.setAlwaysShow(true);

		Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());
		result.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
			@Override
			public void onSuccess(LocationSettingsResponse locationSettingsResponse){}
		})
				.addOnFailureListener(MapsActivity.this, new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						if (e instanceof ResolvableApiException){
							try {
								ResolvableApiException exc= (ResolvableApiException) e;
								exc.startResolutionForResult(MapsActivity.this,5);
							}catch (IntentSender.SendIntentException exception){
								exception.printStackTrace();
							}
						}
					}
				});
	}
}