package com.example.cycleasy;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Fragment class for activities in exercise page
 */
public class ExerciseFragment extends Fragment implements OnMapReadyCallback{
    private static final String TAG = "ExerciseActivity";
    private static final int ERROR_DIALOG_REQUEST = 9001;
    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";
    //true if there is message sent from other fragment, false if otherwise
    private boolean messagepending=false, bound=false, running=false;
    private long pauseoffset;
    private double  cyctime, cycdist=0,cycspeed=0;
    private MapView mMapView;
    private GoogleMap mMap;
    private ArrayList<LatLng> listPoints;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private ServiceConnection mServiceConnection;
    private DistanceTraveledService mDistanceTraveledService;
    private Boolean mLocationPermissionGranted = true;
    private static final float DEFAULT_ZOOM = 15f;
    private Context thiscontext;
    private TextView distancetxt,speedtxt;
    private Chronometer mychrono;
    private String startpt,endpt;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view= inflater.inflate(R.layout.fragment_exercise, null);
        thiscontext = container.getContext();
        FloatingActionButton startBut=(FloatingActionButton) view.findViewById(R.id.exe_startBut);
        FloatingActionButton stopBut=(FloatingActionButton)view.findViewById(R.id.exe_stopBut);
        FloatingActionButton mylocatBut=(FloatingActionButton)view.findViewById(R.id.exe_loactionBut);
        distancetxt=(TextView)view.findViewById(R.id.distnum);
        speedtxt=(TextView)view.findViewById(R.id.speednumber);
        mychrono=(Chronometer)view.findViewById(R.id.exe_chronometer);
        mMapView = (MapView) view.findViewById(R.id.exe_mapview);


        //Service connection for distance tracker
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                DistanceTraveledService.DistanceTravelBinder distanceTravelBinder = (DistanceTraveledService.DistanceTravelBinder)service;
                mDistanceTraveledService = distanceTravelBinder.getBinder();
                bound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                bound = false;
            }
        };

        //Start exercise button activity
        startBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO for tracker when start button is clicked\
                if (!running){
                    mychrono.setBase(SystemClock.elapsedRealtime()-pauseoffset);
                    mychrono.start();
                    displayMetrics();
                    running=true;}
                else{
                    mychrono.stop();
                    pauseoffset=SystemClock.elapsedRealtime()-mychrono.getBase();
                    running=false;
            }

        }}
        );

        //stop exercise button activity
        stopBut.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                //TODO for tracker when stop button is long clicked
                //send cycling metrics to exercise report fragment for display
                Bundle bundle=new Bundle();
                bundle.putString("cycling distance", String.format("%.2fKM",cycdist));
                bundle.putString("cycling time", mychrono.getText().toString());
                bundle.putString("cycling speed",String.format("%.2fKM/H",cycspeed));
                FragmentTransaction transaction=getFragmentManager().beginTransaction();
                ExeReportFragment reportFragment=new ExeReportFragment();
                reportFragment.setArguments(bundle);
                transaction.setCustomAnimations(R.anim.slide_in_bott, R.anim.slide_out_bott);
                transaction.addToBackStack(null);
                transaction.replace(R.id.fragment_container, reportFragment, "EXERCISE REPORT").commit();

                //reset the metrics displayed
                distancetxt.setText(getResources().getString(R.string.defaultdistance));
                speedtxt.setText(getResources().getString(R.string.defaultspeed));
                mychrono.setBase(SystemClock.elapsedRealtime());
                pauseoffset=0;

                return false;
            }
        });


        mylocatBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDeviceLocation();
            }
        });
        // SearchableActivity myActivity=(SearchableActivity)getActivity();
        // searchbar.setText(myActivity.getHintText());

        //initiate google map
        initGoogleMap(savedInstanceState);
        mMapView.getMapAsync(this);


        return view;
    }

    //display exercise metrics
    private void displayMetrics() {
            final Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(mDistanceTraveledService != null){
                        cycdist = mDistanceTraveledService.getDistanceTraveled()/100000;
                    }
                    cyctime=(SystemClock.elapsedRealtime()-mychrono.getBase())/1000;
                    cycspeed=cycdist/(cyctime/3600);
                    speedtxt.setText(String.format("%.2fKM/H",cycspeed));
                    distancetxt.setText(String.format("%.2fKM",cycdist));
                    handler.postDelayed(this, 1000);
                    Log.d("time", String.valueOf(cyctime)+"S");
                    Log.d("displaydistance", String.valueOf(cycdist)+"KM");
                    Log.d("speed",String.valueOf(cycspeed)+"KM/H");


                }
            });

        }

    public boolean isServicesOK() {
        Log.d(TAG, "isServiceOK: checking google services version");

        int available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext());

        if (available == ConnectionResult.SUCCESS) {
            //fine
            Log.d(TAG, "isServicesOK:");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            //
            Log.d(TAG, "isServicesOK: ");
            //Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(getContext(), available, ERROR_DIALOG_REQUEST);
            //dialog.show();
        } else {
            Toast.makeText(getContext(), "You can't make map requests", Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
        Intent intent = new Intent (thiscontext, DistanceTraveledService.class);
        thiscontext.bindService(intent,mServiceConnection,Context.BIND_AUTO_CREATE);
        Log.d("onStart", "binded");
    }


    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
        if(bound){
            thiscontext.unbindService(mServiceConnection);
            Log.d("onStop", "unbind");
           bound = false;
        }
    }


    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }
    private void initGoogleMap(Bundle savedInstanceState){
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        mMapView.onCreate(mapViewBundle);

        mMapView.getMapAsync(this);
    }


    /**
     * called when map is initialized and ready for geolocating and route searching
     * @param googleMap non null googleMap instance passed in for performing map functions
     */
    // When map is ready
    // When map is ready
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText(getContext(), "Map is Ready", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onMapReady: map is ready");
        mMap = googleMap;

        if (mLocationPermissionGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(thiscontext, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(thiscontext,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);

            //Fx: find a location.

            //Fx: find bicycling directions between 2 points
            mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(LatLng latLng) {
                    //Reset marker when already 2
                    if (listPoints.size() == 2) {
                        listPoints.clear();
                        mMap.clear();
                    }
                    //Save first point selected
                    listPoints.add(latLng);
                    //Create marker
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng);

                    if (listPoints.size() == 1) {
                        //Add first marker to the map
                        Log.d(TAG, "onMapLongClick: adding first point");
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    } else {
                        //Add second market to the map
                        Log.d(TAG, "onMapLongClick: adding second point");
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    }
                    mMap.addMarker(markerOptions);
                    //request get direction code bellow
                    if (listPoints.size() == 2) {
                        Log.d(TAG, "onMapLongClick: Searching now");
                        String url = getRequestUrl(listPoints.get(0), listPoints.get(1));
                        ExerciseFragment.TaskRequestDirections taskRequestDirections = new ExerciseFragment.TaskRequestDirections();
                        taskRequestDirections.execute(url);
                    }
                }
            });
        }
    }


    // get request about starting and ending points and convert to url, and use url to request from google map api
    private String getRequestUrl(LatLng origin,LatLng dest){
        // value of origin
        String str_org = "origin="+origin.latitude+","+origin.longitude;
        // value of destination
        String str_dest = "destination="+dest.latitude+","+dest.longitude;
        // set value anble the sensor
        //String sensor = "sensor=false";
        // Mode for finding direction
        String mode = "mode=bicycling";
        // Build the full param
        String api_key = "&key=AIzaSyDW_vO8Zofe8at0AwHE-91_Pa1ZQFTijr8";

        String param = str_org +"&"+str_dest+"&"+mode+api_key;
        // Output format
        String output = "json";
        // Create url to request
        String url = "https://maps.googleapis.com/maps/api/directions/"+output+"?"+param;
        Log.d(TAG, "getRequestUrl: "+url);
        return url;
    }

//    private void init(String query){
//        Log.d(TAG, "init: initializing");
//
//        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//            @Override
//            public boolean onEditorAction(TextView v, int actionId, KeyEvent keyEvent) {
//                if(actionId == EditorInfo.IME_ACTION_SEARCH
//                        ||actionId == EditorInfo.IME_ACTION_DONE
//                        ||keyEvent.getAction() == KeyEvent.ACTION_DOWN
//                        ||keyEvent.getAction() == KeyEvent.KEYCODE_ENTER){
//
//                    //execute our method for searching
//                    geoLocate(query);
//                    hideSoftKeyboard();
//                }
//                return false;
//            }
//        });
//    }


    /**
     * locating a geographical location from a location name of type String, and move the camera to that location
     * @param query query text containing the name of the location
     * @return Address of the location
     */
    private Address geoLocate(String query){
        Log.d(TAG, "geoLocate: geolocating");

        Geocoder geocoder = new Geocoder(getContext());
        List<Address> list = new ArrayList<>();
        try{
            list = geocoder.getFromLocationName(query,1);
        }catch(IOException e){
            Log.e(TAG,"geolocate: IOException"+e.getMessage());
        }

        if(list.size()>0){

            Log.d(TAG, "geoLocate: found something");
            Address address = list.get(0);

            Log.d(TAG, "geoLocate: found a location: "+address.toString());
            //Toast.makeText(this,address.toString(),Toast.LENGTH_SHORT).show();

            moveCamera(new LatLng(address.getLatitude(),address.getLongitude()),DEFAULT_ZOOM,address.getAddressLine(0));

            return address;
        }
        return null;
    }



    /**
     * move camera towards a point with latitude and longtitude parsed in LatLng, and camera zoom of zoom and location name title
     * @param latLng LatLng object containing latitude and longtitude of an address
     * @param zoom camera zoom value
     * @param title location name
     */
    private void moveCamera(LatLng latLng, float zoom,String title){
        Log.d(TAG, "moveCamera: moving the camera to: "+latLng.latitude +", lng: "+latLng.longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng,zoom));

        if(!title.equals("My Location")) {
            MarkerOptions options = new MarkerOptions()
                    .position(latLng)
                    .title(title);
            mMap.addMarker(options);
        }

        hideSoftKeyboard();
    }

    private void hideSoftKeyboard(){
        Log.d(TAG, "hideSoftKeyboard: hiding");
        // this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    /**
     * Get the location of this device
     */
    private void getDeviceLocation(){
        Log.d(TAG,"getDeviceLocation: getting the devices current location");

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(thiscontext);

        try{
            if(mLocationPermissionGranted){
                final Task location = mFusedLocationProviderClient.getLastLocation();
                location.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if(task.isSuccessful()){
                            Log.d(TAG, "onComplete: foundLocation!");
                            Location currentLocation = (Location) task.getResult();
                            moveCamera(new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude()),
                                    DEFAULT_ZOOM,
                                    "My Location");
                        }else{
                            Log.d(TAG, "onComplete: current location is null");
                            Toast.makeText(getContext(),"unable to get current location",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }catch(SecurityException e){
            Log.e(TAG, "getDeviceLocation: SecurityException"+e.getMessage());
        }
    }

    // get direction, using httpurlconnection
    private String requestDirection(String reqUrl) throws IOException {
        String responseString = "";
        InputStream inputStream = null;
        HttpURLConnection httpURLConnection = null;
        try{
            URL url = new URL(reqUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();

            //Get the response result
            inputStream = httpURLConnection.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }

            responseString = stringBuffer.toString();
            bufferedReader.close();
            inputStreamReader.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            httpURLConnection.disconnect();
        }
        return responseString;
    }
    // create a AsyncTask to call request direction
    public class TaskRequestDirections extends AsyncTask<String,Void,String> {
        @Override
        protected String doInBackground(String... strings){
            String responseString = "";
            try {
                responseString = requestDirection(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s){
            super.onPostExecute(s);
            //Parse json here
            Log.d(TAG, "onPostExecute: calling taskparser");
            ExerciseFragment.TaskParser taskParser = new ExerciseFragment.TaskParser();
            taskParser.execute(s);

        }
    }

    public class TaskParser extends AsyncTask<String,Void,List<List<HashMap<String,String>>>> {

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... strings) {
            Log.d(TAG, "doInBackground: calling json");
            JSONObject jsonObject = null;
            List<List<HashMap<String, String>>> routes = null;
            try {
                jsonObject = new JSONObject(strings[0]);
                DirectionParser directionParser = new DirectionParser();
                routes = directionParser.parse(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> lists) {
            //super.onPostExecute(lists);
            // Get list route and display it into the map
            ArrayList points = null;

            PolylineOptions polylineOptions = null;

            for (List<HashMap<String, String>> path : lists) {
                points = new ArrayList();
                polylineOptions = new PolylineOptions();

                for (HashMap<String, String> point : path) {
                    double lat = Double.parseDouble(point.get("lat"));
                    double lon = Double.parseDouble(point.get("lon"));
                    Log.d(TAG, "onPostExecute: " + lat + " " + lon);
                    points.add(new LatLng(lat, lon));
                }

                Log.d(TAG, "onPostExecute: drawing line now");
                polylineOptions.addAll(points);
                polylineOptions.width(15);
                polylineOptions.color(Color.BLUE);
                polylineOptions.geodesic(true);
            }
            if (polylineOptions != null) {
                mMap.addPolyline(polylineOptions);
            } else {
                Log.d(TAG, "onPostExecute: Direction not found!");
                Toast.makeText(getContext(), "Direction not found", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
