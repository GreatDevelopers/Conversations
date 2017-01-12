package in.gndec.sunehag.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.util.ArrayList;

import in.gndec.sunehag.R;

import static in.gndec.sunehag.Config.DEFAULT_MAP_LOCATION_ZOOM;

public class ShowLocationActivity extends Activity {

    private MapView map;
    private IMapController mapController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.activity_show_location);
        map = (MapView) findViewById(R.id.mapview);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(DEFAULT_MAP_LOCATION_ZOOM);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();

        String mLocationName = intent != null ? intent.getStringExtra("name") : null;

        if (intent != null && intent.hasExtra("longitude") && intent.hasExtra("latitude")) {
            double longitude = intent.getDoubleExtra("longitude",0);
            double latitude = intent.getDoubleExtra("latitude",0);
            GeoPoint geoPoint = new GeoPoint(latitude, longitude);
            mapController.setCenter(geoPoint);
            ArrayList<OverlayItem> overlayItemArrayList = new ArrayList<>();
            OverlayItem item = new OverlayItem(mLocationName,"",geoPoint);
            overlayItemArrayList.add(item);
            ItemizedIconOverlay<OverlayItem> overlayItemItemizedIconOverlay =
                    new ItemizedIconOverlay<>(this, overlayItemArrayList, null);
            map.getOverlays().add(overlayItemItemizedIconOverlay);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}