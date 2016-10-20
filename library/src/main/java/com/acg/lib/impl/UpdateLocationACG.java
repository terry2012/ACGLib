package com.acg.lib.impl;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import com.acg.ACGLib.R;
import com.acg.lib.ACGResourceAccessException;
import com.acg.lib.PermanentAccessACG;
import com.acg.lib.model.Location;
import com.acg.lib.validation.bitmap.BitmapValidator;
import com.acg.lib.validation.bitmap.StatefulBitmapValidator;
import com.acg.lib.view.ValidatedViewWrapper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INTERVAL;
import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INVALIDATION;

/**
 * A LocationACG that sends periodic updates instead of lazy access, but still works as long as it's toggled "on"
 * This is a more common use case for the Location ACG
 * Right now uses coarse location/balanced power, can make more customizable later
 * If this is common outside of location, we should expose an "updating" ACG as a different kind
 */
public final class UpdateLocationACG extends PermanentAccessACG<Location> implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private GoogleApiClient googleApiClient;
    private Optional<Long> interval = Optional.absent();
    private Optional<Long> fastestInterval = Optional.absent();
    private Optional<Float> smallestDisplacement = Optional.absent();
    private Location location;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        buildGoogleApiClient(context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        buildGoogleApiClient(activity);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (googleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected BitmapValidator initBitmapValidator(@NonNull List<View> views) {
        Map<ValidatedViewWrapper, List<Bitmap>> bitmapsForViews = new HashMap<>();

        for (View view : views) {
            bitmapsForViews.put((ValidatedViewWrapper) view, Collections.singletonList(getBitmapForView(view)));
        }

        return new StatefulBitmapValidator(bitmapsForViews);
    }

    /**
     * Create an instance of the Google API Client
     */
    private synchronized void buildGoogleApiClient(Context context) {
        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    protected boolean resourceIsAvailable() {
        return googleApiClient.isConnected() && location != null;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    /**
     * The amount of time an ACG should invalidate a view after a failed check in ms
     */
    @Override
    protected int randomCheckInvalidationParameter() {
        return DEFAULT_RANDOM_CHECK_INVALIDATION;
    }

    /**
     * The frequency of random checks for an ACG in ms
     */
    @Override
    protected int randomCheckIntervalParameter() {
        return DEFAULT_RANDOM_CHECK_INTERVAL;
    }

    /**
     * Connection callbacks for the underlying location API
     */
    @Override
    public void onConnected(Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        stopLocationUpdates();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        throw new RuntimeException("Connection failed");
    }

    /**
     * Render the view in isolation in any possible states to populate bitmaps
     */
    @Override
    protected
    @NonNull
    List<View> renderViewsInIsolation(@NonNull Context context) {
        List<View> views = new ArrayList<>();

        // Default state
        ToggleButton toggleButton = buildToggleButton(context);
        View wrapper = new ValidatedViewWrapper(context, toggleButton, noopValidationArgs, noopValidator);
        views.add(wrapper);

        // Checked state
        ToggleButton checkedToggleButton = buildToggleButton(context);
        View checkedWrapper = new ValidatedViewWrapper(context, checkedToggleButton, noopValidationArgs, noopValidator);
        checkedWrapper.performClick();
        views.add(checkedWrapper);

        return views;
    }

    /**
     * Build the inner toggle button from a context with a listener, both for actual rendering and for isolated rendering
     */
    private ToggleButton buildToggleButton(Context context) {
        ToggleButton toggleButton = new ToggleButton(context);
        toggleButton.setText(getString(R.string.update_location_acg_off));
        toggleButton.setTextOn(getString(R.string.update_location_acg_on));
        toggleButton.setTextOff(getString(R.string.update_location_acg_off));
        toggleButton.setLayoutParams(new ViewGroup.LayoutParams(1000, 200));
        toggleButton.setBackgroundColor(Color.BLACK);
        toggleButton.setTextColor(Color.WHITE);
        return toggleButton;
    }

    /**
     * Inflate and build the fragment
     */
    @Override
    protected @NonNull View buildView(@NonNull LayoutInflater inflater, @NonNull final ViewGroup container) {
        final ViewGroup inflatedContainer = (ViewGroup) inflater.inflate(R.layout.update_location_acg_fragment, container, false);

        // Create button listener
        CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    googleApiClient.connect();
                } else {
                    googleApiClient.disconnect();
                }
            }
        };

        // Build button
        Context context = inflatedContainer.getContext();
        ToggleButton toggleButton = buildToggleButton(context);
        toggleButton.setOnCheckedChangeListener(listener);

        // Build the wrapper
        View wrapper = new ValidatedViewWrapper(context, toggleButton, validationArguments, validator);
        wrapper.setId(R.id.update_location_acg_button_id);

        // Add wrapper to view
        inflatedContainer.addView(wrapper);
        return inflatedContainer;
    }

    public void setInterval(long interval) {
        this.interval = Optional.of(interval);
    }

    public void setFastestInterval(long fastestInterval) {
        this.fastestInterval = Optional.of(fastestInterval);
    }

    public void setSmallestDisplacement(float smallestDisplacement) {
        this.smallestDisplacement = Optional.of(smallestDisplacement);
    }

    protected void startLocationUpdates() {
        // Android 23 compatibility
        Activity activity = getActivity();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        LocationRequest request = new LocationRequest();
        if (interval.isPresent()) {
            request.setInterval(interval.get());
        }
        if (fastestInterval.isPresent()) {
            request.setFastestInterval(fastestInterval.get());
        }
        if (smallestDisplacement.isPresent()) {
            request.setSmallestDisplacement(smallestDisplacement.get());
        }
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, request, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        resourceAvailabilityListener.onResourceUnavailable();
    }

    /**
     * Access the resource, given an input resource
     */
    @Override
    public Location getResource() throws ACGResourceAccessException {
        if (!resourceIsAvailable()) {
            throw new ACGResourceAccessException("Resource is not available");
        }

        return location;
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        this.location = new Location(location);
        resourceAvailabilityListener.onResourceReady();
    }
}
