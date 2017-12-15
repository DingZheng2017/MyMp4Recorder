package self.dz.mymp4recorder;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import encoder.MediaAudioEncoder;
import encoder.MediaEncoder;
import encoder.MediaMuxerWrapper;
import encoder.MediaVideoEncoder;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    int width = 640, height = 480;
    int framerate, bitrate;
    int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    MediaCodec mMediaCodec;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Camera mCamera;
    NV21Convertor mConvertor;
    Button btnSwitch;
    boolean started = false;
    String path = Environment.getExternalStorageDirectory() + "/myMp4.h264";
    private MediaMuxerWrapper mMuxer;
    //存储YUV数据的队列，用于从videomideacodec线程里获取
    private static int yuvqueuesize = 10;
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSwitch = (Button) findViewById(R.id.btn_switch);
        initMediaCodec();
        surfaceView = (SurfaceView) findViewById(R.id.sv_surfaceview);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setFixedSize(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        //初始化编码器


    }

    //放YUV数据
    public void putYUVData(byte[] buffer) {
        if (YUVQueue.size() >= yuvqueuesize) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }

    private void initMediaCodec() {
        int dgree = getDgree();
        framerate = 15;
        bitrate = 2 * width * height * framerate / 20;
        //   EncoderDebugger debugger = EncoderDebugger.debug(getApplicationContext(), width, height);
        //   mConvertor = debugger.getNV21Convertor();
        mConvertor = new NV21Convertor();
        try {
            mMediaCodec = MediaCodec.createEncoderByType("Video/AVC");
            MediaFormat mediaFormat;
            if (dgree == 0) {
                mediaFormat = MediaFormat.createVideoFormat("video/avc", height, width);
            } else {
                mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
            }
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);//15fps
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);// 10s一次关键帧
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void onClick(View view) {
        if (!started) {
            startPreview();
        } else {
            stopPreview();
        }
    }

    private void startPreview() {
        if (mCamera != null && !started) {
            mCamera.startPreview();
            int previewFormat = mCamera.getParameters().getPreviewFormat();
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            int size = previewSize.width * previewSize.height
                    * ImageFormat.getBitsPerPixel(previewFormat)
                    / 8;
            mCamera.addCallbackBuffer(new byte[size]);
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
            started = true;
            btnSwitch.setText("停止");
            startRecording();
        }


    }

    private void startRecording() {
        try {
        mMuxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
        if (true) {
            // for video capturing
            new MediaVideoEncoder(mMuxer, mMediaEncoderListener, 480, 640);
        }
        if (true) {
            // for audio capturing
            new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
        }
            mMuxer.prepare();
        } catch (IOException e) {
            Log.e("ysh", "00000startCapture:", e);
            e.printStackTrace();
        }
        mMuxer.startRecording();
    }
    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {

        }

        @Override
        public void onStopped(final MediaEncoder encoder) {

        }
    };

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
            started = false;
            btnSwitch.setText("开始");
        }
        if (mMuxer != null) {
            mMuxer.stopRecording();
            mMuxer = null;
            // you should not wait here
        }

    }


    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        byte[] mPpsSps = new byte[0];

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            android.util.Log.d("ysh", "xxxxxxxxx mPpsSps:   " + mPpsSps.length);

            if (data == null) {
                android.util.Log.d("ysh", "data == null");
                return;
            }

            putYUVData(data);
            mCamera.addCallbackBuffer(data);
//            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
//            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
//            byte[] dst = new byte[data.length];
//            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
//            if (getDgree() == 0) {
//                dst = Util.rotateNV21Degree90(data, previewSize.width, previewSize.height);
//            } else {
//                dst = data;
//            }
//            try {
//                int bufferIndex = mMediaCodec.dequeueInputBuffer(5000000);
//                Log.d("ysh", "bufferIndex : "+bufferIndex );
//                if (bufferIndex >= 0) {
//                    inputBuffers[bufferIndex].clear();
//                    //将YUV420SP数据转换成YUV420P的格式，并将结果存入inputBuffers[bufferIndex]
//                    mConvertor.convert(dst, inputBuffers[bufferIndex]);
//                    mMediaCodec.queueInputBuffer(bufferIndex, 0,
//                            inputBuffers[bufferIndex].position(),
//                            System.nanoTime() / 1000, 0);
//                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//                    while (outputBufferIndex >= 0) {
//                        Log.d("ysh", "outputBufferIndex>= 0");
//                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                        byte[] outData = new byte[bufferInfo.size];
//                        //从buff中读取数据到outData中
//                        outputBuffer.get(outData);
//                        //记录pps和sps，pps和sps数据开头是0x00 0x00 0x00 0x01 0x67，
//                        if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 103) {
//                            mPpsSps = outData;
//                        } else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 101) {
//                            //关键帧开始规则是0x00 0x00 0x00 0x01 0x65，0x65对应十进制101
//                            //在关键帧前面加上pps和sps数据
//                            byte[] iframeData = new byte[mPpsSps.length + outData.length];
//                            System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
//                            System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
//                            outData = iframeData;
//                        }
//                        Util.save(outData, 0, outData.length, path, true);
//                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//                    }
//                } else {
//                    Log.e("ysh", "No buffer available !");
//                }
//            } catch (Exception e) {
//                StringWriter sw = new StringWriter();
//                PrintWriter pw = new PrintWriter(sw);
//                e.printStackTrace(pw);
//                String stack = sw.toString();
//                Log.e("save_log", stack);
//                e.printStackTrace();
//            } finally {
//                mCamera.addCallbackBuffer(dst);
//            }
//        }
        }
    };


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        createCamera(surfaceHolder);
    }

    private boolean createCamera(SurfaceHolder surfaceHolder) {
        try {
            mCamera = Camera.open(mCameraId);
            Camera.Parameters parameters = mCamera.getParameters();
            int[] max = determineMaximumSupportedFramerate(parameters);
            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, camInfo);
            int cameraRotationOffset = camInfo.orientation;
            int rotate = (360 + cameraRotationOffset - getDgree()) % 360;
            parameters.setRotation(rotate);
            //It is strongly recommended that either NV21 or YV12 is used, since they are supported by all camera devices.
            //强烈建议使用NV21格式和YV12格式，而默认情况下是NV21格式，也就是YUV420SP的。(不经过转换，直接用BitmapFactory解析是不能成功的)
            parameters.setPreviewFormat(ImageFormat.NV21);
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            parameters.setPreviewSize(width, height);
            parameters.setPreviewFpsRange(max[0], max[1]);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(null);
            int displayRotation;
            displayRotation = (cameraRotationOffset - getDgree() + 360) % 360;
            mCamera.setDisplayOrientation(displayRotation);
            mCamera.setPreviewDisplay(surfaceHolder);
            return true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stack = sw.toString();
            //      Toast.makeText(this, stack, Toast.LENGTH_LONG).show();
            destroyCamera();
            e.printStackTrace();
            return false;
        }
    }

    private int getDgree() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break; // Natural orientation
            case Surface.ROTATION_90:
                degrees = 90;
                break; // Landscape left
            case Surface.ROTATION_180:
                degrees = 180;
                break;// Upside down
            case Surface.ROTATION_270:
                degrees = 270;
                break;// Landscape right
        }
        return degrees;
    }

    //这个方法就可以显示出你的手机摄像头支持的预览帧数范围
    private int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};

        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopPreview();
        destroyCamera();
    }

    private void destroyCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {

            }
            mCamera = null;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyCamera();
        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaCodec = null;
    }
}

