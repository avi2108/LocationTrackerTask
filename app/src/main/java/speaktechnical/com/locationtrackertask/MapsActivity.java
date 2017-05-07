package speaktechnical.com.locationtrackertask;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, View.OnClickListener {

    private final int TAG_PERSON_WALK_ICON = 1;//these tags used to identify bw two clicks for fab button
    private final int TAG_CLEAR_ICON = 2;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private int MY_PERMISSIONS_REQUEST_LOCATION = 10;

    private FloatingActionButton floatingActionButton;
    private TextView tvForLocationTrackInfo;
    LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    SharedPreferences mPrefs;
    Handler handler;
    private RequestQueue mRequestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mPrefs = getSharedPreferences("sharedPred", MODE_PRIVATE);

        tvForLocationTrackInfo = (TextView) findViewById(R.id.tvForLocationTrackInfo);
        tvForLocationTrackInfo.setVisibility(View.GONE);
        floatingActionButton = (FloatingActionButton) findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(this);
        floatingActionButton.setTag(TAG_PERSON_WALK_ICON);

        handler = new Handler();
        buildGoogleApiClient();

        //setting up the LocationRequest object to request frequent location updates for tracking the shift Off
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
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

        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    void buildGoogleApiClient() {
        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        checkForLocationServiceInDevice();
        super.onStart();
    }

    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mPrefs.edit().putBoolean("mRequestingLocationUpdates", mRequestingLocationUpdates).commit();
//            mGoogleApiClient.disconnect();
//            if (mRequestingLocationUpdates)
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mGoogleApiClient.connect();
        try {//checking the location service status for the device to dismiss location setting dialog
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                if (dialogForSettings != null) dialogForSettings.create().dismiss();
            }
        } catch (Exception ex) {
        }

        mRequestingLocationUpdates = mPrefs.getBoolean("mRequestingLocationUpdates", false);
        if (mRequestingLocationUpdates && mGoogleApiClient.isConnected())
            startLocationUpdates();

    }

    private Location mCurrentLocation, mLastKnownLocation;

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);

            return;
        }
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mCurrentLocation != null) {
            // Add a marker in current location and move the camera
            mMap.addMarker(new MarkerOptions().position(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))).setTitle("My Location");
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()), 16));

        }
    }

    @Override
    public void onConnectionSuspended(int i) {
//        stopLocationUpdates();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    ObjectAnimator animationRotateX;

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

        //---displaying notification text about location tracking with small animation
        tvForLocationTrackInfo.setVisibility(View.VISIBLE);
        tvForLocationTrackInfo.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        tvForLocationTrackInfo.setText("Location Track is being recorded");
        animationRotateX = ObjectAnimator.ofFloat(tvForLocationTrackInfo, "rotationX", 0.0f, 360f);
        animationRotateX.setDuration(2000);
        animationRotateX.setRepeatCount(ObjectAnimator.INFINITE);
        animationRotateX.setInterpolator(new AccelerateDecelerateInterpolator());
        animationRotateX.start();
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            //if location permission was granted by user then try connecting to GoogleClient now
            mGoogleApiClient.connect();
        }

    }


    @Override
    public void onLocationChanged(Location location) {//fetching location update and saving to global location variable
        mLastKnownLocation = location;
//        Toast.makeText(this, location.getLatitude() + "", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        if (((int) view.getTag()) == TAG_PERSON_WALK_ICON) {//reset map ,place the current location marker and start location update
            floatingActionButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_close_white_24dp));
            floatingActionButton.setTag(TAG_CLEAR_ICON);
            mRequestingLocationUpdates = true;
            startLocationUpdates();
            mMap.clear();
            if (mLastKnownLocation != null)
                mCurrentLocation = mLastKnownLocation;
            mMap.addMarker(new MarkerOptions().position(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()))).setTitle("My Location");
        } else {//stop the location update and display the directions
            floatingActionButton.setTag(TAG_PERSON_WALK_ICON);
            mRequestingLocationUpdates = false;
            floatingActionButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_directions_walk_white_24dp));
            stopLocationUpdates();
            animationRotateX.end();
            tvForLocationTrackInfo.setText("Location Tracked and place was marked");
            tvForLocationTrackInfo.setTextColor(getResources().getColor(R.color.colorPrimary));
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Animation animation = AnimationUtils.loadAnimation(MapsActivity.this, android.R.anim.fade_out);

                    animation.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            tvForLocationTrackInfo.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });
                    tvForLocationTrackInfo.startAnimation(animation);
                }
            }, 2000);
            mMap.addMarker(new MarkerOptions().position(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()))).setTitle("My Last Location");

            doDirectionsQuery(makeURL(mCurrentLocation, mLastKnownLocation));

        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("mRequestingLocationUpdates", mRequestingLocationUpdates);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mRequestingLocationUpdates = savedInstanceState.getBoolean("mRequestingLocationUpdates");
        super.onRestoreInstanceState(savedInstanceState);
    }

    LocationManager lm;
    AlertDialog.Builder dialogForSettings;

    /**
     * This function will check for location service status in device and throws a dialog to enable the location service if not
     */
    private void checkForLocationServiceInDevice() {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        if (!gps_enabled && !network_enabled) {
            // notify user
            dialogForSettings = new AlertDialog.Builder(this);
            dialogForSettings.setMessage(getResources().getString(R.string.gps_network_not_enabled));
            dialogForSettings.setPositiveButton(getResources().getString(R.string.open_location_settings), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // TODO Auto-generated method stub
                    Intent myIntent = new Intent(Settings
                            .ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(myIntent);
                    //get gps
                }
            });
            dialogForSettings.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // TODO Auto-generated method stub

                }
            });
            dialogForSettings.show();
        }
    }

    /**
     * This function will construct and return google api directions url taking start and end locations
     *
     * @param srcLocation
     * @param destLocation
     * @return
     */
    public String makeURL(Location srcLocation, Location destLocation) {
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://maps.googleapis.com/maps/api/directions/json");
        urlString.append("?origin=");// from
        urlString.append(Double.toString(srcLocation.getLatitude()));
        urlString.append(",");
        urlString
                .append(Double.toString(srcLocation.getLongitude()));
        urlString.append("&destination=");// to
        urlString
                .append(Double.toString(destLocation.getLatitude()));
        urlString.append(",");
        urlString.append(Double.toString(destLocation.getLongitude()));
        urlString.append("&sensor=false&mode=driving&alternatives=true");
        urlString.append("&key=AIzaSyBzjeJXVy3OvW63-eHFYDW7h0oc6LwDo30");
        return urlString.toString();
    }

    /**
     * This function is responsible for fetching directions response from google direction api returned by makeUrl()
     *
     * @param url google directions url
     */
    private void doDirectionsQuery(String url) {

        final ProgressDialog progressDialog = new ProgressDialog(MapsActivity.this);
        progressDialog.setMessage("Fetching route, Please wait...");
        progressDialog.setIndeterminate(true);
        progressDialog.show();
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                progressDialog.hide();
                drawPath(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                progressDialog.hide();
                Log.e("error", error.getMessage());
            }
        });
        //---adding request to volley RequestQue
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        mRequestQueue.add(stringRequest);
    }

    /**
     * This function will draw the direction path (poyline) from directions api response
     *
     * @param result google directions api response
     */
    public void drawPath(String result) {

        try {
            //Tranform the string into a json object
            final JSONObject json = new JSONObject(result);
            JSONArray routeArray = json.getJSONArray("routes");
            JSONObject routes = routeArray.getJSONObject(0);
            JSONObject overviewPolylines = routes.getJSONObject("overview_polyline");
            String encodedString = overviewPolylines.getString("points");
            List<LatLng> list = decodePoly(encodedString);
            Polyline line = mMap.addPolyline(new PolylineOptions()
                    .addAll(list)
                    .width(12)
                    .color(Color.parseColor("#05b1fb"))//Google maps blue color
                    .geodesic(true)
            );
           /*
           for(int z = 0; z<list.size()-1;z++){
                LatLng src= list.get(z);
                LatLng dest= list.get(z+1);
                Polyline line = mMap.addPolyline(new PolylineOptions()
                .add(new LatLng(src.latitude, src.longitude), new LatLng(dest.latitude,   dest.longitude))
                .width(2)
                .color(Color.BLUE).geodesic(true));
            }
           */
        } catch (JSONException e) {

        }
    }

    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
}
