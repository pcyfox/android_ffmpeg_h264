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
                        H264Utils.H264Info h264Info =H264Utils.parseSpspps(mSpsPpsBuffer);
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
                    Thread.sleep(600);
                    is.close();
                    outStream.close();
                    try {
                        Thread.sleep(40);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
            InputStream is = null;
            FileInputStream fileIS;
            int iTemp = 0;
            int nalLen;

            boolean bFirst = true;
            boolean bFindPPS = true;

            int bytesRead = 0;
            int NalBufUsed = 0;
            int SockBufUsed;

            byte[] NalBuf = new byte[40980]; // 40k
            byte[] SockBuf = new byte[2048];

            try {
                fileIS = new FileInputStream(file);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    bytesRead = fileIS.read(SockBuf, 0, 2048);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (bytesRead <= 0)
                    break;
                SockBufUsed = 0;
                while (bytesRead - SockBufUsed > 0) {
                    nalLen = mergeBuffer(NalBuf, NalBufUsed, SockBuf, SockBufUsed, bytesRead - SockBufUsed);
                    NalBufUsed += nalLen;
                    SockBufUsed += nalLen;
                    while (mTrans == 1) {
                        mTrans = 0xFFFFFFFF;
                        if (bFirst) {
                            bFirst = false;
                        }
                        // a complete NAL data, include 0x00000001 trail.
                        else {
                            if (bFindPPS) {
                                if ((NalBuf[4] & 0x1F) == 7) {
                                    bFindPPS = false;
                                } else {
                                    NalBuf[0] = 0;
                                    NalBuf[1] = 0;
                                    NalBuf[2] = 0;
                                    NalBuf[3] = 1;
                                    NalBufUsed = 4;
                                    break;
                                }
                            }
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            onFrame(NalBuf);
                        }
                        NalBuf[0] = 0;
                        NalBuf[1] = 0;
                        NalBuf[2] = 0;
                        NalBuf[3] = 1;
                        NalBufUsed = 4;
                    }
                }
            }
            try {
                fileIS.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    int mergeBuffer(byte[] NalBuf, int NalBufUsed, byte[] SockBuf, int SockBufUsed, int SockRemain) {
        int i;
        byte Temp;

        for (i = 0; i < SockRemain; i++) {
            Temp = SockBuf[i + SockBufUsed];
            NalBuf[i + NalBufUsed] = Temp;
            mTrans <<= 8;
            mTrans |= Temp;
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
