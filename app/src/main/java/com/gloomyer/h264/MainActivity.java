package com.gloomyer.h264;

import android.Manifest;
import android.graphics.PixelFormat;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

import com.gloomyer.h264.jni.JNIBridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private SurfaceView surface;
    private int mTrans = 0x0F0F0F0F;
    private String file = Environment.getExternalStorageDirectory() + "/tmp_352_268.264"; //352x288.264"; //240x320.264";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                123);
        surface = findViewById(R.id.surface);
        surface.getHolder().setFormat(PixelFormat.RGBA_8888);
    }

    @Override
    protected void onDestroy() {
        JNIBridge.destroy();
        super.onDestroy();
    }

    private Thread test;

    public void test(View view) {
        if (test == null) {
            test = new Thread(readH264File);
            // test = new Thread(readAssetsFile);
            test.start();
        }
    }


    public void test2(View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JNIBridge.init(surface.getHolder().getSurface());
                JNIBridge.test(file, surface.getHolder().getSurface());
            }
        }).start();
    }

    public void test3(View view) {
        if (test == null) {
            test = new Thread(readAssetsFile);
            test.start();
        }
    }


    private Runnable readAssetsFile = new Runnable() {
        @Override
        public void run() {
            int initResult = JNIBridge.init(surface.getHolder().getSurface());
            Log.d(TAG, "JNIBridge.init   initResult: " + initResult);
            boolean readFlag = true;
            int h264Count = 0;
            while (!Thread.interrupted() && readFlag) {
                if (h264Count > 30) {
                    try {
                        Thread.sleep(4);
                        h264Count = 0;
                        readFlag = false;
                        continue;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                String assetsFileName = String.format("%03d", h264Count) + ".h264";
                try {
                    InputStream is = MainActivity.this.getResources().getAssets().open(assetsFileName);
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    int BUFFER_SIZE = 1024;
                    byte[] data = new byte[BUFFER_SIZE];
                    int count;
                    while ((count = is.read(data, 0, BUFFER_SIZE)) != -1) {
                        outStream.write(data, 0, count);
                    }

                    byte[] result = outStream.toByteArray();

                    if (result[0] == 0x17 && result[1] == 0) {
                        byte[] spsppsBuffer = H264Utils.makeConfig(result);
                        byte[] mSpsPpsBuffer = new byte[spsppsBuffer.length];
                        System.arraycopy(spsppsBuffer, 0, mSpsPpsBuffer, 0, spsppsBuffer.length);
                        H264Utils.H264Info h264Info = H264Utils.parseSpspps(mSpsPpsBuffer);
                        onFrame(spsppsBuffer);
                        // Log.d(TAG, "readAssetsFile h264Info: "+h264Info);
                    }

                    byte[] byOutput = H264Utils.makePackage(result);
                    if (byOutput == null) {
                        byOutput = new byte[4];
                        byOutput[0] = 0;
                        byOutput[1] = 0;
                        byOutput[2] = 0;
                        byOutput[3] = 1;//图像参数集
                    }

                    onFrame(byOutput);
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    is.close();
                    outStream.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                h264Count++;
            }
        }
    };

    private Runnable readH264File = new Runnable() {

        @Override
        public void run() {
            int initResult = JNIBridge.init(surface.getHolder().getSurface());
            Log.d(TAG, "onFrame   initResult: " + initResult);


            int nalLen;

            boolean isFirst = true;
            boolean isFindPPS = true;

            int bytesReadCount;
            int nalBufUsedCount = 0;
            int tempBufUsedCount;

            byte[] nalBuf = new byte[40980]; // 40k
            byte[] tempBuf = new byte[4096];

            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                while (!Thread.currentThread().isInterrupted()) {
                    bytesReadCount = fileInputStream.read(tempBuf, 0, tempBuf.length);
                    if (bytesReadCount <= 0)
                        break;
                    tempBufUsedCount = 0;
                    while (bytesReadCount - tempBufUsedCount > 0) {
                        nalLen = mergeBuffer(nalBuf, nalBufUsedCount, tempBuf, tempBufUsedCount, bytesReadCount - tempBufUsedCount);
                        nalBufUsedCount += nalLen;
                        tempBufUsedCount += nalLen;
                        while (mTrans == 1) {
                            mTrans = 0xFFFFFFFF;
                            if (isFirst) {
                                isFirst = false;
                            } else {
                                // a complete NAL data, include 0x00000001 trail
                                //0x67 A&0x1f==7:sps
                                //0x68 A&0x1f==8:pps
                                //0x65 A&0x1f==5:关键帧
                                // 【h264编码出的NALU规律】
                                //第一帧 SPS【0 0 0 1 0x67】 PPS【0 0 0 1 0x68】 SEI【0 0 0 1 0x6】 IDR【0 0 0 1 0x65】
                                // p帧   P【0 0 0 1 0x61】
                                // I帧   SPS【0 0 0 1 0x67】 PPS【0 0 0 1 0x68】 IDR【0 0 0 1 0x65】

                                if (isFindPPS) {
                                    if ((nalBuf[4] & 0x1F) == 7) {
                                        isFindPPS = false;
                                    } else {
                                        nalBuf[0] = 0;
                                        nalBuf[1] = 0;
                                        nalBuf[2] = 0;
                                        nalBuf[3] = 1;
                                        nalBufUsedCount = 4;
                                        break;
                                    }
                                }
                                try {
                                    Thread.sleep(60);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                onFrame(nalBuf);
                            }
                            nalBuf[0] = 0;
                            nalBuf[1] = 0;
                            nalBuf[2] = 0;
                            nalBuf[3] = 1;
                            nalBufUsedCount = 4;
                        }
                    }
                }

                fileInputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    };


    int mergeBuffer(byte[] nalBuf, int nalBufUsedCount, byte[] tempBuf, int tempBufUsedCount, int remian) {
        int i;
        byte temp;
        for (i = 0; i < remian; i++) {
            temp = tempBuf[i + tempBufUsedCount];
            nalBuf[i + nalBufUsedCount] = temp;
            mTrans <<= 8;
            mTrans |= temp;
            if (mTrans == 1) {
                i++;
                break;
            }
        }
        return i;
    }


    private boolean onFrame(byte[] buf) {
        Log.d(TAG, "onFrame() called with: buf = [" + Arrays.toString(buf) + "]");
        int ret = JNIBridge.decodeVideo(buf);
        Log.e(TAG, "JNIBridge.decode ret:" + ret);

        return true;
    }


}
