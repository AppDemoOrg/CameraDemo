package com.abt.camera;

import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.abt.camera.util.CameraHelper;
import com.abt.camera.util.Utils;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnTouch;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaRecorder.OnErrorListener {

    public final static String BASE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/video/"; //存放照片的文件夹
    private boolean mIsStarting = false;
    private int mTimeCount;// 时间计数
    private OnRecordFinishListener mOnRecordFinishListener;// 录制完成回调接口
    private TimerTask mTimerTask;
    private MediaRecorder mMediaRecorder;// 录制视频的类
    private Timer mTimer;// 计时器
    private AnimationDrawable mAnimation;
    private File mVecordFile = null;// 文件
    private String mDirname;//视频存储的目录
    private int mRecordMaxTime = 100;// 一次拍摄最长时间 10秒
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private List<int[]> mFpsRange;
    private Camera.Size mOptimalSize;

    private OnRecordFinishListener mRecordFinishListener = new OnRecordFinishListener() {
        @Override
        public void onRecordFinish() {
            Toast.makeText(MainActivity.this, "拍摄完毕", Toast.LENGTH_SHORT).show();
        }
    };

    @BindView(R.id.surfaceView)
    SurfaceView mSurfaceView;
    @BindView(R.id.tag_start)
    ImageView mTagStart;
    @OnTouch(R.id.startBtn) boolean startBtn(View view, MotionEvent event) {
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN :
                if (mIsStarting) {
                    stopRecord();
                } else {
                    startRecord(mRecordFinishListener);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTimeCount < 30) {
                    Toast.makeText(MainActivity.this, "不能少于3秒！", Toast.LENGTH_SHORT).show();
                    stopRecord();
                } else {
                    stopRecord();
                    if (mOnRecordFinishListener != null){
                        mOnRecordFinishListener.onRecordFinish();
                    }
                }
                break;
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mSurfaceHolder = mSurfaceView.getHolder();// 取得holder
        mSurfaceHolder.addCallback(this); // holder加入回调接口
        mSurfaceHolder.setKeepScreenOn(true);

        mAnimation = (AnimationDrawable) mTagStart.getDrawable();
        mAnimation.setOneShot(false); // 设置是否重复播放
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Logger.d("surfaceCreated");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Logger.d("surfaceDestroyed");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            freeCameraResource();
        }

        try {
            mCamera = Camera.open();
            if (mCamera == null) return;
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mParameters = mCamera.getParameters();// 获得相机参数

            List<Camera.Size> supportedPreviewSizes = mParameters.getSupportedPreviewSizes();
            List<Camera.Size> supportedVideoSizes = mParameters.getSupportedVideoSizes();
            mOptimalSize = CameraHelper.getOptimalVideoSize(supportedPreviewSizes,
                    supportedVideoSizes, width, height);
            mParameters.setPreviewSize(mOptimalSize.width, mOptimalSize.height); // 设置预览图像大小
            mParameters.set("orientation", "portrait");
            List<String> focusModes = mParameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mFpsRange = mParameters.getSupportedPreviewFpsRange();
            mCamera.setParameters(mParameters);// 设置相机参数
            mCamera.startPreview();// 开始预览
        } catch (Exception io) {
            io.printStackTrace();
        }
    }

    /**
     * 释放摄像头资源
     */
    private void freeCameraResource() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        try {
            if (mr != null) mr.reset();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 录制完成回调接口
     */
    public interface OnRecordFinishListener {
        void onRecordFinish();
    }

    /**
     * 录制前，初始化
     */
    private void initRecord() {
        try {
            if(mMediaRecorder == null){
                mMediaRecorder = new MediaRecorder();

            }
            if(mCamera != null){
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
            }

            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);// 视频源

            // Use the same size for recording profile.
            CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            mProfile.videoFrameWidth = mOptimalSize.width;
            mProfile.videoFrameHeight = mOptimalSize.height;

            mMediaRecorder.setProfile(mProfile);
            //该设置是为了抽取视频的某些帧，真正录视频的时候，不要设置该参数
//            mMediaRecorder.setCaptureRate(mFpsRange.get(0)[0]);//获取最小的每一秒录制的帧数

            mMediaRecorder.setOutputFile(mVecordFile.getAbsolutePath());
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            releaseRecord();
        }
    }

    /**
     * 开始录制视频
     */
    public void startRecord(final OnRecordFinishListener onRecordFinishListener) {
        this.mOnRecordFinishListener = onRecordFinishListener;
        mIsStarting = true;
        //lay_tool.setVisibility(View.INVISIBLE);
        //tag_start.setVisibility(View.VISIBLE);
        mAnimation.start();
        createRecordDir();
        try {
            initRecord();
            mTimeCount = 0;// 时间计数器重新赋值
            mTimer = new Timer();
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    mTimeCount++;
                    //mProgressBar.setProgress(mTimeCount);
                    if (mTimeCount == mRecordMaxTime) {// 达到指定时间，停止拍摄
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stop();
                            }
                        });
                        if (mOnRecordFinishListener != null){
                            mOnRecordFinishListener.onRecordFinish();
                        }
                    }
                }
            };
            mTimer.schedule(mTimerTask, 0, 100);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止拍摄
     */
    public void stop() {
        stopRecord();
        releaseRecord();
        freeCameraResource();
    }

    /**
     * 创建目录与文件
     */
    private void createRecordDir() {
        mDirname = String.valueOf(System.currentTimeMillis()) +  String.valueOf( new Random().nextInt(1000));
        File FileDir = new File(BASE_PATH + mDirname);
        if (!FileDir.exists()) {
            FileDir.mkdirs();
        }
        // 创建文件
        try {
            mVecordFile = new File(FileDir.getAbsolutePath() + "/" + Utils.getDateNumber() +".mp4");
            Logger.d("Path:", mVecordFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        //mProgressBar.setProgress(0);
        mIsStarting = false;
        //tag_start.setVisibility(View.GONE);
        mAnimation.stop();
        //lay_tool.setVisibility(View.VISIBLE);
        if(mTimerTask != null)
            mTimerTask.cancel();
        if (mTimer != null)
            mTimer.cancel();
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 释放资源
     */
    private void releaseRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.setOnErrorListener(null);
            try {
                mMediaRecorder.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mMediaRecorder = null;
    }

}
