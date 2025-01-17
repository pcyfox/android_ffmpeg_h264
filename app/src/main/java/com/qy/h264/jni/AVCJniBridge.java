package com.qy.h264.jni;

import android.view.Surface;

public class AVCJniBridge {
    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("gcodec");
    }

    /**
     * 初始化
     * <p>
     *
     * @param surface surface
     * @return 0成功 -1:解码器没找到，-2解码器上下文分配失败 -3解码器打开失败 1: 已经注册过了
     */
    public native static int init(Surface surface, int threadCount);

    public static int init(Surface surface) {
      return init(surface, 8);
    }

    /**
     * 将h264数据 解码并且显示到初始化提供的Surface中
     *
     * @param h264buff h264一帧数据 必须保证一帧数据的完整，否则会乱码
     * @return 0：表示成功解码 -1表示失败
     */
    public native static int decodeVideo(byte[] h264buff);

    /**
     * 释放资源
     */
    public native static void destroy();


    public native static void test(String path, Surface surface);

}
