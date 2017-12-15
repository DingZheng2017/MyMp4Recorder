package encoder;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaVideoEncoder.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;


import java.io.IOException;
import java.nio.ByteBuffer;

import self.dz.mymp4recorder.MainActivity;

public class MediaVideoEncoder extends MediaEncoder {
    private static final boolean DEBUG = false;    // TODO set false on release
    private static final String TAG = "MediaVideoEncoder";

    private static final String MIME_TYPE = "video/avc";
    // parameters for recording
    //private static final int FRAME_RATE = 25;
    private static final int FRAME_RATE = 10;
    //private static final float BPP = 0.25f;
    private static final float BPP = 0.1f;

    private final int mWidth;
    private final int mHeight;
    private Surface mSurface;
    private VideoThread mVideoThread = null;

    public MediaVideoEncoder(final MediaMuxerWrapper muxer, final MediaEncoderListener listener, final int width, final int height) {
        super(muxer, listener);
        if (DEBUG) Log.i(TAG, "MediaVideoEncoder: ");
        mWidth = width;
        mHeight = height;
    }


    @Override
    protected void prepare() throws IOException {
        if (DEBUG) Log.i(TAG, "prepare: ");
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;

        final MediaCodecInfo videoCodecInfo = selectVideoCodec(MIME_TYPE);
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + videoCodecInfo.getName());

        // final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, 480, 640);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);    // API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        if (DEBUG) Log.i(TAG, "format: " + format);

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // get Surface for encoder input
        // this method only can call between #configure and #start
        //mSurface = mMediaCodec.createInputSurface();    // API >= 18
        mMediaCodec.start();
        if (DEBUG) Log.i(TAG, "prepare finishing");
        if (mListener != null) {
            try {
                mListener.onPrepared(this);
            } catch (final Exception e) {
                Log.e(TAG, "prepare:", e);
            }
        }
    }

    @Override
    protected void startRecording() {
        super.startRecording();
        // create and execute audio capturing thread using internal mic
        if (mVideoThread == null) {
            mVideoThread = new VideoThread();
            mVideoThread.start();
        }
    }

    @Override
    protected void release() {
        if (DEBUG) Log.i(TAG, "release:");
        mVideoThread = null;
        super.release();
    }

    public boolean isRuning = false;
    byte[] mPpsSps = new byte[0];
    private class VideoThread extends Thread {
        @Override
        public void run() {
            //拿video数据并塞入codec
            isRuning = true;
            byte[] input = null;
            long pts = 0;
            long generateIndex = 0;

            while (isRuning) {
                if (MainActivity.YUVQueue.size() > 0) {
                    input = MainActivity.YUVQueue.poll();
                    byte[] yuv420sp = new byte[480 * 640 * 3 / 2];
                    //颜色转换，摄像头NV21转NV12,此处可代码优化
                     //在用MediaCodec编码的时候，如果设置颜色空间为YUV420SP，那么则需要转换一下
                    NV21ToNV12(input, yuv420sp, 480, 640);
                    input = yuv420sp;
                }
                if (input != null) {
                    try {
                       // long startMs = System.currentTimeMillis();
                        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                        if (inputBufferIndex >= 0) {
                        //    pts = computePresentationTime(generateIndex);
                            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(input);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime() / 1000, 0);
                            generateIndex += 1;
                        }

                        //MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                       // int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);


//                        while (outputBufferIndex >= 0) {
//                            //Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
//                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                            byte[] outData = new byte[bufferInfo.size];
//                            outputBuffer.get(outData);
//                            if (bufferInfo.flags == 2) {
//                                configbyte = new byte[bufferInfo.size];
//                                configbyte = outData;
//                            } else if (bufferInfo.flags == 1) {
//                                byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
//                                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
//                                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
//
//                                outputStream.write(keyframe, 0, keyframe.length);
//                            } else {
//                                outputStream.write(outData, 0, outData.length);
//                            }
//
//                            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
//                        }
//                        while (outputBufferIndex >= 0) {
//                            Log.d("ysh", "outputBufferIndex>= 0");
//                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                            byte[] outData = new byte[bufferInfo.size];
//                            outputBuffer.get(outData);
//                            //记录pps和sps
//                            if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 103) {
//                                mPpsSps = outData;
//                            } else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 101) {
//                                //在关键帧前面加上pps和sps数据
//                                byte[] iframeData = new byte[mPpsSps.length + outData.length];
//                                System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
//                                System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
//                                outData = iframeData;
//                            }
//                            //Util.save(outData, 0, outData.length, path, true);
//                            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//                        }

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
        if(nv21 == null || nv12 == null)return;
        int framesize = width*height;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for(i = 0; i < framesize; i++){
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j-1] = nv21[j+framesize];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j] = nv21[j+framesize-1];
        }
    }



    private int calcBitRate() {
        //final int bitrate = (int)(BPP * FRAME_RATE * mWidth * mHeight);
        final int bitrate = (int) (BPP * FRAME_RATE * 480 * 640);
        Log.i("ysh", String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
        return bitrate;
    }

    /**
     * select the first codec that match a specific MIME type
     *
     * @param mimeType
     * @return null if no codec matched
     */
    protected static final MediaCodecInfo selectVideoCodec(final String mimeType) {
        if (DEBUG) Log.v(TAG, "selectVideoCodec:");

        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            // select first codec that match a specific MIME type and color format
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    if (DEBUG) Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
                    final int format = selectColorFormat(codecInfo, mimeType);
                    if (format > 0) {
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }

    /**
     * select color format available on specific codec and we can use.
     *
     * @return 0 if no colorFormat is matched
     */
    protected static final int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        if (DEBUG) Log.i(TAG, "selectColorFormat: ");
        int result = 0;
        final MediaCodecInfo.CodecCapabilities caps;
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            caps = codecInfo.getCapabilitiesForType(mimeType);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
        int colorFormat;
        for (int i = 0; i < caps.colorFormats.length; i++) {
            colorFormat = caps.colorFormats[i];
            if (isRecognizedViewoFormat(colorFormat)) {
                if (result == 0)
                    result = colorFormat;
                break;
            }
        }
        if (result == 0)
            Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return result;
    }

    /**
     * color formats that we can use in this class
     */
    protected static int[] recognizedFormats;

    static {
        recognizedFormats = new int[]{
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
//        	MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
        };
    }

    private static final boolean isRecognizedViewoFormat(final int colorFormat) {
        if (DEBUG) Log.i(TAG, "isRecognizedViewoFormat:colorFormat=" + colorFormat);
        final int n = recognizedFormats != null ? recognizedFormats.length : 0;
        for (int i = 0; i < n; i++) {
            if (recognizedFormats[i] == colorFormat) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder");
        mMediaCodec.signalEndOfInputStream();    // API >= 18
        mIsEOS = true;
    }

//    @Override
//    protected void drain() {
//        if (mMediaCodec == null) return;
//        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
//        int encoderStatus, count = 0;
//        final MediaMuxerWrapper muxer = mWeakMuxer.get();
//        if (muxer == null) {
////        	throw new NullPointerException("muxer is unexpectedly null");
//            Log.w(TAG, "muxer is unexpectedly null");
//            return;
//        }
//        LOOP:	while (mIsCapturing) {
//            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
//            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
//            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
//                if (!mIsEOS) {
//                    if (++count > 5)
//                        break LOOP;		// out of while
//                }
//            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
//                // this shoud not come when encoding
//                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
//            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                if (DEBUG) Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
//                // this status indicate the output format of codec is changed
//                // this should come only once before actual encoded data
//                // but this status never come on Android4.3 or less
//                // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
//                if (mMuxerStarted) {	// second time request is error
//                    throw new RuntimeException("format changed twice");
//                }
//                // get output format from codec and pass them to muxer
//                // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
//                final MediaFormat format = mMediaCodec.getOutputFormat(); // API >= 16
//                mTrackIndex = muxer.addTrack(format);
//                mMuxerStarted = true;
//                if (!muxer.start()) {
//                    // we should wait until muxer is ready
//                    synchronized (muxer) {
//                        while (!muxer.isStarted())
//                            try {
//                                muxer.wait(100);
//                            } catch (final InterruptedException e) {
//                                break LOOP;
//                            }
//                    }
//                }
//            } else if (encoderStatus < 0) {
//                // unexpected status
//                if (DEBUG) Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
//            } else {
//                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
//                if (encodedData == null) {
//                    // this never should come...may be a MediaCodec internal error
//                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
//                }
//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    // You shoud set output format to muxer here when you target Android4.3 or less
//                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
//                    // therefor we should expand and prepare output format from buffer data.
//                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
//                    if (DEBUG) Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
//                    mBufferInfo.size = 0;
//                }
//
//                if (mBufferInfo.size != 0) {
//                    // encoded data is ready, clear waiting counter
//                    count = 0;
//                    if (!mMuxerStarted) {
//                        // muxer is not ready...this will prrograming failure.
//                        throw new RuntimeException("drain:muxer hasn't started");
//                    }
//                    // write encoded data to muxer(need to adjust presentationTimeUs.
//                    mBufferInfo.presentationTimeUs = getPTSUs();
//                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
//                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
//                }
//                // return buffer to encoder
//                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    // when EOS come.
//                    mIsCapturing = false;
//                    break;      // out of while
//                }
//            }
//        }
//    }
}
