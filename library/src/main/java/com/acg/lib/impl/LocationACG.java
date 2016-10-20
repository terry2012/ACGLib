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
import com.google.android.gms.location.LocationServices;

import java.util.*;

import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INVALIDATION;

/**
 * OneTimeACG which accesses a location allows a user to toggle location access.
 */
public final class LocationACG extends PermanentAccessACG<Location> implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient googleApiClient;

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
        return googleApiClient.isConnected();
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
        return 4000;
    }

    /**
     * Connection callbacks for the underlying location API
     */
    @Override
    public void onConnected(Bundle bundle) {
        resourceAvailabilityListener.onResourceReady();
    }

    @Override
    public void onConnectionSuspended(int i) {
        resourceAvailabilityListener.onResourceUnavailable();
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
        toggleButton.setText(getString(R.string.location_acg_text_off));
        toggleButton.setTextOn(getString(R.string.location_acg_text_on));
        toggleButton.setTextOff(getString(R.string.location_acg_text_off));
        toggleButton.setLayoutParams(new ViewGroup.LayoutParams(700, 200));
        toggleButton.setBackgroundColor(Color.BLACK);
        toggleButton.setTextColor(Color.WHITE);
        return toggleButton;
    }

    /**
     * Inflate and build the fragment
     */
    @Override
    protected
    @NonNull
    View buildView(@NonNull LayoutInflater inflater, @NonNull final ViewGroup container) {
        final ViewGroup inflatedContainer = (ViewGroup) inflater.inflate(R.layout.location_acg_fragment, container, false);

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
        wrapper.setId(R.id.location_acg_button_id);

        // Add wrapper to view
        inflatedContainer.addView(wrapper);
        return inflatedContainer;
    }

    /**
     * Access the resource, given an input resource
     */
    @Override
    public Location getResource() throws ACGResourceAccessException {
        if (!resourceIsAvailable()) {
            throw new ACGResourceAccessException("Resource is not available");
        }

        // Android 23 compatibility
        Activity activity = getActivity();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        android.location.Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if (location == null) {
            throw new RuntimeException("Unexpected error getting location from LocationServices");
        }

        return new Location(location);
    }
}
