/**
 * University of Illinois
 Open Source License

 Copyright © <2015>, <University of Illinois at Urbana-Champaign>. All rights reserved.
 All rights reserved.

 Developed by:

 Smartphone Bioassay Team

 University of Illinois at Urbana-Champaign

 http://sb.illinois.edu

 Permission is hereby granted, free of charge, to any person obtaining a copy of
 this software and associated documentation files (the "Software"), to deal with
 the Software without restriction, including without limitation the rights to
 use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 of the Software, and to permit persons to whom the Software is furnished to do
 so, subject to the following conditions:

 * Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimers.

 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimers in the
 documentation and/or other materials provided with the distribution.

 * Neither the names of the Smartphone Bioassay Team, University of Illinois at
 Urbana-Champaign, nor the names of its contributors may be used to
 endorse or promote products derived from this Software without specific
 prior written permission.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE
 SOFTWARE.
 */
package uiuc.bioassay.camera;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.location.Location;
import android.media.ExifInterface;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("deprecation")
public class CameraActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String TAG = "CAMERA";
    private static final int MAX_PICTURE = 5;

    private Camera mCamera;
    private CameraPreview mPreview;
    private FrameLayout preview;
    private Button buttonCapture;

    private int picCount = 0;
    private StartPictureSeriesSound startSeriesSound = new StartPictureSeriesSound();
    private StopPictureSeriesSound stopSeriesSound = new StopPictureSeriesSound();
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputImageFile(picCount);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.flush();
                fos.close();
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(pictureFile)));

                // Take more pictures if still
                ++picCount;
                camera.startPreview();
                if (picCount < MAX_PICTURE) {
                    mCamera.takePicture(null, null,
                            mPicture);
                } else {
                    picCount = 0;
                    stopSeriesSound.play();
                    buttonCapture.setEnabled(true);
                    startLocationUpdates();
                }
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            } catch (Exception e) {
                Log.d(TAG, "Error starting preview: " + e.getMessage());
            }
        }
    };

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 20000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    // Keys for storing activity state in the Bundle.
    protected final static String LOCATION_KEY = "location-key";
    protected final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    protected LocationRequest mLocationRequest;

    /**
     * Represents a geographical location.
     */
    protected Location mCurrentLocation;


    /**
     * Time when the location was updated represented as a String.
     */
    protected String mLastUpdateTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Open camera
        openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);

        // Set onClickListener for taking picture
        buttonCapture = (Button) findViewById(R.id.button_capture);
        buttonCapture.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stopLocationUpdates();
                        buttonCapture.setEnabled(false);
                        startSeriesSound.play();
                        mCamera.takePicture(null, null, mPicture);
                    }
                }
        );

        mLastUpdateTime = "";

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState);

        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }
        }
    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }


    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }


    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }

        // Start update location
        startLocationUpdates();
    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        Toast.makeText(this, "Location updated. Latitude: " + mCurrentLocation.getLatitude() + ", longitude: " + mCurrentLocation.getLongitude() + ", time: " + mLastUpdateTime,
                Toast.LENGTH_LONG).show();
    }

    private void writeGPSInfo(File imgFile, Location location) {
        try {
            ExifInterface exifInterface = new ExifInterface(imgFile.getAbsolutePath());
            double latitude = location.getLatitude();
            Log.d(TAG, "Latitude: " + latitude);
            String[] latitudeDMS = Location.convert(latitude, Location.FORMAT_SECONDS).split(":");
            String latitudeFormat = Math.abs(Integer.parseInt(latitudeDMS[0])) +
                    "/1," + latitudeDMS[1] + "/1," + (int) (Double.parseDouble(latitudeDMS[2]) * 10000) + "/10000";
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitudeFormat);
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, (latitude > 0) ? "N" : "S");
            Log.d(TAG, "Latitude: " + latitudeFormat + ((latitude > 0) ? "N" : "S"));

            double longitude = location.getLongitude();
            Log.d(TAG, "Longitude: " + longitude);
            String[] longitudeDMS = Location.convert(longitude, Location.FORMAT_SECONDS).split(":");
            String longitudeFormat = Math.abs(Integer.parseInt(longitudeDMS[0])) +
                    "/1," + longitudeDMS[1] + "/1," + (int)(Double.parseDouble(longitudeDMS[2])*10000) + "/10000";
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitudeFormat);
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, (longitude > 0) ? "E" : "W");
            Log.d(TAG, "Longitude: " + longitudeFormat + ((longitude > 0) ? "E":"W"));
            exifInterface.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    /**
     * Stores activity data in the Bundle.
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }

        if (mCamera == null) {
            openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // release the camera immediately on pause event
        releaseCamera();

        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_camera, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.stopPreview();
            //mCamera.setPreviewCallback(null);
            mPreview.getHolder().removeCallback(mPreview);
            mCamera.release();        // release the camera for other applications
            preview.removeView(mPreview);
            mPreview = null;
            mCamera = null;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    private static Camera getCameraInstance(int cameraId){
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    /** Set camera display orientation */
    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, Camera camera) {
        Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /** Open camera */
    private void openCamera(int cameraId) {
        if (mCamera != null) {
            return;
        }
        /* TODO: If Camera Activity is called from another activity, return error if cannot open camera */
        mCamera = getCameraInstance(cameraId);
        if (mCamera == null) {
            Log.d(TAG, "Camera is null");
            return;
        }

        // Set default camera parameters using Read, Modify, Write technique
        // Read
        Camera.Parameters params = mCamera.getParameters();

        // Modify
        // Set camera orientation
        setCameraDisplayOrientation(this, cameraId, mCamera);
        // Search for best preview size and set preview size
        {
            int width = 0;
            int height = 0;
            int maxArea = 0;
            for (Camera.Size size : params.getSupportedPreviewSizes()) {
                int area = size.width * size.height;
                if (area > maxArea) {
                    width = size.width;
                    height = size.height;
                    maxArea = area;
                }
            }
            params.setPreviewSize(width, height);
        }

        // Search for best picture size and set piture size
        {
            int width = 0;
            int height = 0;
            int maxArea = 0;
            for (Camera.Size size : params.getSupportedPictureSizes ()) {
                int area = size.width * size.height;
                if (area > maxArea) {
                    width = size.width;
                    height = size.height;
                    maxArea = area;
                }
            }
            params.setPictureSize(width, height);
        }

        // Set image quality
        params.setJpegQuality(100);

        // Set focus mode
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);

        // Set exposure offset
        params.setExposureCompensation(0);

        // Set white balance
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_FLUORESCENT);

        // Set iso
        params.set("iso", String.valueOf("100"));


        // Write
        mCamera.setParameters(params);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        mPreview.setFocusOnTouch(true);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);
    }

    /** Shutter Start Series Sound */
    private static class StartPictureSeriesSound {
        private final MediaActionSound media;

        private StartPictureSeriesSound() {
            media = new MediaActionSound();
            media.load(MediaActionSound.START_VIDEO_RECORDING);
        }

        public void play() {
            media.play(MediaActionSound.START_VIDEO_RECORDING);
        }
    }

    /** Shutter Stop Series Sound */
    private static class StopPictureSeriesSound {
        private final MediaActionSound media;

        private StopPictureSeriesSound() {
            media = new MediaActionSound();
            media.load(MediaActionSound.STOP_VIDEO_RECORDING);
        }

        public void play() {
            media.play(MediaActionSound.STOP_VIDEO_RECORDING);
        }
    }

    /** Create a File for saving an image */
    private static File getOutputImageFile(int count){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + "_" + count + ".jpg");
    }

}
