package in.gndec.sunehag.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.util.ArrayList;

import in.gndec.sunehag.R;

import static in.gndec.sunehag.Config.DEFAULT_MAP_LOCATION_ZOOM;

public class ShareLocationActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private MapView map;
    private IMapController mapController;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mLastLocation;
    private Button mCancelButton;
    private Button mShareButton;
    private RelativeLayout mSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_location);
        map = (MapView) findViewById(R.id.mapview);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(DEFAULT_MAP_LOCATION_ZOOM);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mCancelButton = (Button) findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        mShareButton = (Button) findViewById(R.id.share_button);
        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLastLocation != null) {
                    Intent result = new Intent();
                    result.putExtra("latitude", mLastLocation.getLatitude());
                    result.putExtra("longitude", mLastLocation.getLongitude());
                    result.putExtra("altitude", mLastLocation.getAltitude());
                    result.putExtra("accuracy", (int) mLastLocation.getAccuracy());
                    setResult(RESULT_OK, result);
                    finish();
                }
            }
        });
        mSnackbar = (RelativeLayout) findViewById(R.id.snackbar);
        TextView snackbarAction = (TextView) findViewById(R.id.snackbar_action);
        snackbarAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.mLastLocation = null;
        if (isLocationEnabled()) {
            this.mSnackbar.setVisibility(View.GONE);
        } else {
            this.mSnackbar.setVisibility(View.VISIBLE);
        }
        mShareButton.setEnabled(false);
        mShareButton.setTextColor(0x8a000000);
        mShareButton.setText(R.string.locating);
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        mGoogleApiClient.disconnect();
        super.onPause();
    }

    private void centerOnLocation(Location location) {
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.setCenter(geoPoint);
        ArrayList<OverlayItem> overlayItemArrayList = new ArrayList<>();
        OverlayItem item = new OverlayItem("","",geoPoint);
        overlayItemArrayList.add(item);
        ItemizedIconOverlay<OverlayItem> overlayItemItemizedIconOverlay =
                new ItemizedIconOverlay<>(this, overlayItemArrayList, null);
        map.getOverlays().add(overlayItemItemizedIconOverlay);
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (this.mLastLocation == null) {
            centerOnLocation(location);
            this.mShareButton.setEnabled(true);
            this.mShareButton.setTextColor(0xde000000);
            this.mShareButton.setText(R.string.share);
        }
        this.mLastLocation = location;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean isLocationEnabledKitkat() {
        try {
            int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isLocationEnabledLegacy() {
        String locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        return !TextUtils.isEmpty(locationProviders);
    }

    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            return isLocationEnabledKitkat();
        }else{
            return isLocationEnabledLegacy();
        }
    }
}