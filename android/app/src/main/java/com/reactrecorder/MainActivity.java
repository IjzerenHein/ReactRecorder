package com.reactrecorder;

import com.facebook.react.ReactActivity;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;

public class MainActivity extends ReactActivity {

    private boolean mIsRecording = false;
    private boolean mHasRecord = false;

    public boolean isRecording() {
        return mIsRecording;
    }
    public boolean hasRecord() {
        return mHasRecord;
    }

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RecordingManager.updateActivity(this);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = null;

        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();

            mIsRecording = false;
            mHasRecord = false;
            mMediaRecorder = null;
            mMediaProjection = null;

            RecordingManager.notifyStatusChanged();
            return;
        }

        try {
            mMediaProjectionCallback = new MediaProjectionCallback();
            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            mMediaProjection.registerCallback(mMediaProjectionCallback, null);
            mVirtualDisplay = createVirtualDisplay();
            mMediaRecorder.start();
            mIsRecording = true;
            mHasRecord = true;
        } catch (Exception e) {
            mIsRecording = false;
            mHasRecord = false;
        }
        RecordingManager.notifyStatusChanged();
    }

    public void startRecording() {
        if (mIsRecording) return;

        try {
            initRecorder();
            shareScreen();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Exception happened. Please relaunch the app.", Toast.LENGTH_SHORT).show();

            mIsRecording = false;
            mHasRecord = false;
            mMediaRecorder = null;
            mMediaProjection = null;
            RecordingManager.notifyStatusChanged();
        }
    }

    public void stopRecording() {
        if (!mIsRecording) return;

        if (mMediaRecorder == null) {
            return;
        }
        try {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            stopScreenSharing();
        } catch (Exception e) {

        } finally {
            mIsRecording = false;
        }

        RecordingManager.notifyStatusChanged();
    }

    public void playRecording() {
        if (mIsRecording || !mHasRecord) return;

        Intent intent = new Intent(this, VideoActivity.class);
        startActivity(intent);
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void initRecorder() {
        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setOutputFile(Environment
                    .getExternalStoragePublicDirectory(Environment
                            .DIRECTORY_DOWNLOADS) + "/video.mp4");
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mMediaRecorder.setVideoFrameRate(30);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            stopRecording();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        mMediaRecorder = null;
        destroyMediaProjection();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "ReactRecorder";
    }
}
