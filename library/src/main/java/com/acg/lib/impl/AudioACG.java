package com.acg.lib.impl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import com.acg.ACGLib.R;
import com.acg.lib.ACG;
import com.acg.lib.ACGResourceAccessException;
import com.acg.lib.listeners.ResourceReadyListener;
import com.acg.lib.validation.bitmap.BitmapValidator;
import com.acg.lib.validation.bitmap.StatefulBitmapValidator;
import com.acg.lib.view.ValidatedViewWrapper;
import com.google.common.base.Optional;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INTERVAL;
import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INVALIDATION;

/**
 * OneTimeACG which grants one-time access to record audio for as long as the user intends, then saves it to the file system.
 */
public final class AudioACG extends ACG<File> {

    private MediaRecorder mediaRecorder;
    private String outputFilePath;
    private boolean resourceIsAvailable = false;
    private Optional<RecorderListener> recorderListener = Optional.absent();

    private static final String OUTPUT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();

    public interface RecorderListener extends ResourceReadyListener {

        /**
         * Called the first time a user clicks "record," but before "stop"
         */
        void onRecordStarted();
    }

    protected void bindListeners(Context context) {
        super.bindListeners(context);
        if (resourceReadyListener instanceof RecorderListener) {
           recorderListener = Optional.of((RecorderListener) resourceReadyListener);
        }
    }

    /**
     * Record audio from the microphone and put it into a local cache
     */
    private void startRecording() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(outputFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare the media recorder", e);
        }

        mediaRecorder.start();
    }

    /**
     * Stop recording audio
     * It's good Android practice to release the recorder after using it, so you have to create a new one each time
     */
    private void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
        mediaRecorder = null;
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
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

    @Override
    protected BitmapValidator initBitmapValidator(@NonNull List<View> views) {
        Map<ValidatedViewWrapper, List<Bitmap>> bitmapsForViews = new HashMap<>();

        for (View view : views) {
            bitmapsForViews.put((ValidatedViewWrapper) view, Collections.singletonList(getBitmapForView(view)));
        }

        return new StatefulBitmapValidator(bitmapsForViews);
    }

    /**
     * Render the view in isolation in any possible states to populate bitmaps
     */
    @Override
    protected @NonNull List<View> renderViewsInIsolation(@NonNull Context context) {
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
        toggleButton.setText(getString(R.string.audio_acg_text_on));
        toggleButton.setTextOn(getString(R.string.audio_acg_text_off));
        toggleButton.setTextOff(getString(R.string.audio_acg_text_on));
        toggleButton.setLayoutParams(new ViewGroup.LayoutParams(700, 200));
        toggleButton.setBackgroundColor(Color.BLACK);
        toggleButton.setTextColor(Color.WHITE);
        return toggleButton;
    }

    /**
     * Inflate and build the fragment
     */
    @Override
    protected @NonNull View buildView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        final ViewGroup inflatedContainer  = (ViewGroup) inflater.inflate(R.layout.audio_acg_fragment, container, false);

        // Create button listener
        CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    outputFilePath = String.format("%s/audio_acg_output%s.3gp", OUTPUT_DIR, UUID.randomUUID());
                    startRecording();
                    sendRecordEvent();
                } else {
                    stopRecording();
                    sendResource();
                }
            }
        };

        // Build button
        Context context = inflatedContainer.getContext();
        ToggleButton toggleButton = buildToggleButton(context);
        toggleButton.setOnCheckedChangeListener(listener);

        // Build the wrapper
        View wrapper = new ValidatedViewWrapper(context, toggleButton, validationArguments, validator);
        wrapper.setId(R.id.audio_acg_button_id);

        // Add wrapper to view
        inflatedContainer.addView(wrapper);
        return inflatedContainer;
    }

    protected void sendRecordEvent() {
        if (recorderListener.isPresent()) {
            recorderListener.get().onRecordStarted();
        }
    }

    protected void sendResource() {
        resourceIsAvailable = true;
        resourceReadyListener.onResourceReady();
        resourceIsAvailable = false;
    }

    /**
     * Access the resource, given an input resource
     */
    @Override
    public File getResource() throws ACGResourceAccessException {
        if (resourceIsAvailable) {
            return new File(outputFilePath);
        }

        throw new ACGResourceAccessException("Resource is not available");
    }
}
