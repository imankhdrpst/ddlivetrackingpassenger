package com.example.ddlivetrackingpassenger;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.flatdialoglibrary.dialog.FlatDialog;
import com.github.pwittchen.reactivebeacons.library.rx2.Beacon;
import com.github.pwittchen.reactivebeacons.library.rx2.Proximity;
import com.github.pwittchen.reactivebeacons.library.rx2.ReactiveBeacons;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.cachapa.expandablelayout.ExpandableLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final boolean IS_AT_LEAST_ANDROID_M =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1000;
    private static final String ITEM_FORMAT = "MAC: %s\nUUID: %s\nRSSI: %d\nDistance(raw): %.2fm\nDistance(kalman): %.2f\nProximity: %s\nName: %s";
    private ReactiveBeacons reactiveBeacons;
    private Disposable subscription;
    private Map<String, Beacon> beacons;

    //added for detecting uuid
    static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    private HashMap<String, Marker> mMarkers = new HashMap<>();
    private GoogleMap mMap;
    private List<DriverPassenger> allDriverPassengers = new ArrayList();
    private String email = "", password = "";
    private TextView txtToggle;
    private ExpandableLayout expndLayout;
    private ListView lvBeacons;
    private View beaconDetectedIndicator;
    private TextView txtTimeStamp;
    private TextView txtBusy;
    private View layBusy;
    private KalmanFilter kalmanFilter = new KalmanFilter();
    private Toolbar mTopToolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        txtToggle = findViewById(R.id.txt_toggle);
        expndLayout = findViewById(R.id.expandable_layout);
        lvBeacons = findViewById(R.id.lv_beacons);
        beaconDetectedIndicator = findViewById(R.id.beacon_detected_view);
        txtTimeStamp = findViewById(R.id.txt_time_stamp);
        txtBusy = findViewById(R.id.txt_busy);
        layBusy = findViewById(R.id.lay_busy);
        layBusy.setVisibility(View.GONE);


        txtToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expndLayout.isExpanded()) {
                    expndLayout.collapse(true);
                } else {
                    expndLayout.expand(true);
                }
            }
        });

        reactiveBeacons = new ReactiveBeacons(this);
        beacons = new HashMap<>();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        email = getSharedPreferences("ddlivetrackingpassenger", MODE_PRIVATE).getString("email", "");
        password = getSharedPreferences("ddlivetrackingpassenger", MODE_PRIVATE).getString("password", "");

        if (canObserveBeacons()) {
            startSubscription();
        }

    }

    private void login() {
        if (TextUtils.isEmpty(email)) {
            final FlatDialog flatDialog = new FlatDialog(MapsActivity.this);
            flatDialog.setCancelable(false);
            flatDialog.setCanceledOnTouchOutside(false);
            flatDialog.setTitle("Login")
                    .setSubtitle("write your email and password info here")
                    .setFirstTextFieldHint("email")
                    .setSecondTextFieldHint("password")
                    .setFirstButtonText("Connect")
                    .withFirstButtonListner(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            getSharedPreferences("ddlivetrackingpassenger", MODE_PRIVATE)
                                    .edit()
                                    .putString("email", flatDialog.getFirstTextField())
                                    .putString("password", flatDialog.getSecondTextField())
                                    .commit();

                            flatDialog.dismiss();

                            loginToFirebase(flatDialog.getFirstTextField(), flatDialog.getSecondTextField());
                        }
                    })
//                    .withSecondButtonListner(new View.OnClickListener() {
//                        @Override
//                        public void onClick(View view) {
//                            flatDialog.dismiss();
//                        }
//                    })
                    .show();
        } else {
            loginToFirebase(email, password);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_user) {
            login();
            return true;
        }
//        else if (id == R.id.action_ble)
//        {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap = googleMap;
        mMap.setMaxZoomPreference(16);
    }

    private void loginToFirebase(String email, String password) {
        // Authenticate with Firebase and subscribe to updates
        layBusy.setVisibility(View.VISIBLE);
        txtBusy.setText("Signing in ...");
        FirebaseAuth.getInstance().signInWithEmailAndPassword(
                email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    layBusy.setVisibility(View.GONE);
                    subscribeToUpdates();
                    Log.d(TAG, "firebase auth success");
                } else {
                    layBusy.setVisibility(View.GONE);
                    final FlatDialog flatDialog = new FlatDialog(MapsActivity.this);
                    flatDialog.setCancelable(false);
                    flatDialog.setCanceledOnTouchOutside(false);
                    flatDialog.setTitle("Error")
                            .setSubtitle("failed to login")
                            .setFirstButtonText("OK")
                            .withFirstButtonListner(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    flatDialog.dismiss();
                                }
                            })
                            .show();
                    Log.d(TAG, "firebase auth failed");
                }
            }
        });
    }

    private void subscribeToUpdates() {
        layBusy.setVisibility(View.VISIBLE);
        txtBusy.setText("Looking for driver ...");
        DatabaseReference drvPsngRef = FirebaseDatabase.getInstance().getReference("drv_psng");
        drvPsngRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                layBusy.setVisibility(View.GONE);
                String json = (String) dataSnapshot.getValue();
                allDriverPassengers = (new Gson()).fromJson(json, new TypeToken<List<DriverPassenger>>() {
                }.getType());
                boolean found = false;
                for (DriverPassenger item : allDriverPassengers) {
                    if (item.getPassenger().equals(email)) {
                        found = true;
                        lookForDriver(item.getDriver());
                        break;
                    }
                }

                if (!found) {
                    final FlatDialog flatDialog = new FlatDialog(MapsActivity.this);
                    flatDialog.setCancelable(false);
                    flatDialog.setCanceledOnTouchOutside(false);
                    flatDialog.setTitle("Error")
                            .setSubtitle("no driver found for you")
                            .setFirstButtonText("OK")
                            .withFirstButtonListner(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    flatDialog.dismiss();
                                }
                            })
                            .show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                layBusy.setVisibility(View.GONE);

            }
        });

    }

    private void lookForDriver(String driver) {



        DatabaseReference locationRef = FirebaseDatabase.getInstance().getReference("locs/" + driver + "/location");
        locationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                setMarker(dataSnapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
        locationRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                setMarker(dataSnapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                setMarker(dataSnapshot);
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.d(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    private void setMarker(DataSnapshot dataSnapshot) {
        // When a location update is received, put or update
        // its value in mMarkers, which contains all the markers
        // for locations received, so that we can build the
        // boundaries required to show them all on the map at once
        String key = dataSnapshot.getKey();
        String value = (String) dataSnapshot.getValue();
        if (value == null) return;
        LatLng location = (new Gson()).fromJson(value, LatLng.class);
        if (!mMarkers.containsKey(key)) {
            mMarkers.put(key, mMap.addMarker(new MarkerOptions().title(key).position(location)));
        } else {
            mMarkers.get(key).setPosition(location);
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Marker marker : mMarkers.values()) {
            builder.include(marker.getPosition());
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 300));
    }

    private void startSubscription() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PERMISSION_GRANTED) {
            requestCoarseLocationPermission();
            return;
        }

        subscription = reactiveBeacons.observe()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Beacon>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Beacon beacon) throws Exception {
                        beaconDetectedIndicator.setBackgroundResource(R.drawable.green_circle);
                        SimpleDateFormat simpleDateFormat;
                        simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
                        String format = simpleDateFormat.format(new Date());
                        txtTimeStamp.setText("Last Update : " + format);
                        (new Handler()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                beaconDetectedIndicator.setBackgroundResource(R.drawable.red_circle);
                            }
                        }, 500);
                        beacons.put(beacon.device.getAddress(), beacon);
                        refreshBeaconList();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        int a = 0;
                    }
                });
    }

    private boolean canObserveBeacons() {
        if (!reactiveBeacons.isBleSupported()) {
            Toast.makeText(this, "BLE is not supported on this device", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!reactiveBeacons.isBluetoothEnabled()) {
            reactiveBeacons.requestBluetoothAccess(this);
            return false;
        } else if (!reactiveBeacons.isLocationEnabled(this)) {
            reactiveBeacons.requestLocationAccess(this);
            return false;
        } else if (!isFineOrCoarseLocationPermissionGranted() && IS_AT_LEAST_ANDROID_M) {
            requestCoarseLocationPermission();
            return false;
        }

        return true;
    }

    private void refreshBeaconList() {
        List<String> list = new ArrayList<>();

        for (Beacon beacon : beacons.values()) {
            if (getUUID(beacon.scanRecord) != null)
                list.add(getBeaconItemString(beacon));
        }

        int itemLayoutId = android.R.layout.simple_list_item_1;
        lvBeacons.setAdapter(new ArrayAdapter<>(this, itemLayoutId, list));
    }

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private String getUUID(final byte[] scanRecord) {
        int startByte = 2;
        boolean patternFound = false;
        while (startByte <= 5) {
            if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                    ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                patternFound = true;
                break;
            }
            startByte++;
        }

        if (patternFound) {
            //Convert to hex String
            byte[] uuidBytes = new byte[16];
            System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
            String hexString = bytesToHex(uuidBytes);

            //UUID detection
            String uuid = hexString.substring(0, 8) + "-" +
                    hexString.substring(8, 12) + "-" +
                    hexString.substring(12, 16) + "-" +
                    hexString.substring(16, 20) + "-" +
                    hexString.substring(20, 32);
//
//      // major
//      final int major = (scanRecord[startByte + 20] & 0xff) * 0x100 + (scanRecord[startByte + 21] & 0xff);
//
//      // minor
//      final int minor = (scanRecord[startByte + 22] & 0xff) * 0x100 + (scanRecord[startByte + 23] & 0xff);

            return uuid;
        }

        return null;
    }

    private String getBeaconItemString(Beacon beacon) {
        String mac = beacon.device.getAddress();
        int rssi = beacon.rssi;
        double distance = beacon.getDistance();
        double distanceKalman = kalmanFilter.applyFilter(beacon.rssi);
        Proximity proximity = beacon.getProximity();
        String name = beacon.device.getName();
        String uuid = getUUID(beacon.scanRecord);
        return String.format(ITEM_FORMAT, mac, uuid == null ? "null" : uuid, rssi, distance, distanceKalman, proximity, name);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final boolean isCoarseLocation = requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION;
        final boolean permissionGranted = grantResults[0] == PERMISSION_GRANTED;

        if (isCoarseLocation && permissionGranted && subscription == null) {
            startSubscription();
        }
    }

    private void requestCoarseLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
    }

    private boolean isFineOrCoarseLocationPermissionGranted() {
        boolean isAndroidMOrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        boolean isFineLocationPermissionGranted = isGranted(ACCESS_FINE_LOCATION);
        boolean isCoarseLocationPermissionGranted = isGranted(ACCESS_COARSE_LOCATION);

        return isAndroidMOrHigher && (isFineLocationPermissionGranted
                || isCoarseLocationPermissionGranted);
    }

    private boolean isGranted(String permission) {
        return ActivityCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED;
    }

    public class KalmanFilter {
        private double processNoise;//Process noise
        private double measurementNoise;//Measurement noise
        private double estimatedRSSI;//calculated rssi
        private double errorCovarianceRSSI;//calculated covariance
        private boolean isInitialized = false;//initialization flag

        public KalmanFilter() {
            this.processNoise = 0.125;
            this.measurementNoise = 0.8;
        }

        public KalmanFilter(double processNoise, double measurementNoise) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
        }

        public double applyFilter(double rssi) {
            double priorRSSI;
            double kalmanGain;
            double priorErrorCovarianceRSSI;
            if (!isInitialized) {
                priorRSSI = rssi;
                priorErrorCovarianceRSSI = 1;
                isInitialized = true;
            } else {
                priorRSSI = estimatedRSSI;
                priorErrorCovarianceRSSI = errorCovarianceRSSI + processNoise;
            }

            kalmanGain = priorErrorCovarianceRSSI / (priorErrorCovarianceRSSI + measurementNoise);
            estimatedRSSI = priorRSSI + (kalmanGain * (rssi - priorRSSI));
            errorCovarianceRSSI = (1 - kalmanGain) * priorErrorCovarianceRSSI;

            return estimatedRSSI;
        }
    }

}