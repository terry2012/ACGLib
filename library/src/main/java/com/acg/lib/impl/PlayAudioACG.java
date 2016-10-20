package com.acg.lib.impl;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.acg.ACGLib.R;
import com.acg.lib.ACGResourceAccessException;
import com.acg.lib.ComposableACG;
import com.acg.lib.validation.bitmap.BitmapValidator;
import com.acg.lib.validation.bitmap.SingleBitmapValidator;
import com.acg.lib.view.ValidatedViewWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INTERVAL;
import static com.acg.lib.validation.ValidationParameters.DEFAULT_RANDOM_CHECK_INVALIDATION;

/**
 * OneTimeACG which grants one-time access to play recorded audio from a file
 * TODO, passing arguments & SPARTA
 * TODO, filepicker vs. naive, for now just naive
 * TODO, enable/disable (not urgent, but nice eventually)
 */
public final class PlayAudioACG extends ComposableACG<File, Void> {

    private MediaPlayer mediaPlayer;
    private File inputFile;
    private Button playButton;
    private boolean resourceIsAvailable = false;

    @Override
    protected BitmapValidator initBitmapValidator(@NonNull List<View> views) {
        return new SingleBitmapValidator(getBitmapForView(views.get(0)));
    }

    /**
     * Play audio from a file to the speaker
     */
    private void startPlaying(File file) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(file.getPath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare media player", e);
        }

        mediaPlayer.start();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
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
     * Render the view in isolation to populate bitmap
     */
    @Override
    protected @NonNull List<View> renderViewsInIsolation(@NonNull Context context) {
        List<View> views = new ArrayList<>();

        Button playButton = buildPlayButton(context);
        View wrapper = new ValidatedViewWrapper(context, playButton, noopValidationArgs, noopValidator);
        views.add(wrapper);

        return views;
    }

    /**
     * Build the play button from a context with a listener, both for actual rendering and for isolated rendering
     */
    private Button buildPlayButton(Context context) {
        Button playButton = new Button(context);
        playButton.setText(getString(R.string.play_audio_acg_button));
        playButton.setLayoutParams(new ViewGroup.LayoutParams(700, 200));
        playButton.setBackgroundColor(Color.BLACK);
        playButton.setTextColor(Color.WHITE);
        playButton.setEnabled(true);
        return playButton;
    }

    /**
     * Inflate and build the fragment
     */
    @Override
    protected @NonNull View buildView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        final ViewGroup inflatedContainer  = (ViewGroup) inflater.inflate(R.layout.play_audio_acg_fragment, container, false);

        // Create button listener
        Button.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                passResource();
            }
        };

        // Build button
        Context context = inflatedContainer.getContext();
        playButton = buildPlayButton(context);
        playButton.setOnClickListener(listener);
        playButton.setEnabled(false);

        // Build the wrapper
        View wrapper = new ValidatedViewWrapper(context, playButton, validationArguments, validator);
        wrapper.setId(R.id.play_audio_acg_button_id);

        // Add wrapper to view
        inflatedContainer.addView(wrapper);
        return inflatedContainer;
    }

    protected void passResource() {
        resourceIsAvailable = true;
        resourceReadyListener.onResourceReady();
        resourceIsAvailable = false;
    }

    /**
     * Access the resource, given an input resource
     */
    @Override
    public Void getResource() throws ACGResourceAccessException {
        if (resourceIsAvailable && inputFile != null) {
            startPlaying(inputFile);
            return null;
        }
        throw new ACGResourceAccessException("Resource is not available");
    }

    @Override
    public void passInput(File file) {
        inputFile = file;
        playButton.setEnabled(true);
    }


}
