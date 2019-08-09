#include <jni.h>
#include <string>

#include <android/log.h>
#include <android/native_window_jni.h>
#include <ffmpeg_log.h>
#include <unistd.h>

extern "C" {
#include "libavformat/avformat.h" //封装格式上下文
#include "libavcodec/avcodec.h" //解码库
#include "libswscale/swscale.h"
#include "libswresample/swresample.h"
#include <libavutil/imgutils.h>
#include "libyuv.h"
}

#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR,"H264",FORMAT,##__VA_ARGS__);

int _IS_REGISTER_ALL = 0;

bool is_inited = false;

AVCodec *h264_codec = NULL;
AVCodecContext *pAVCodecContext = NULL;
ANativeWindow *native_window = NULL;
//AVFormatContext 视频格式上下文，视频流可以通过这个struct来获取。
AVFormatContext *pAVFormatContext = NULL;

//AVFrame用于存放解码后的数据
AVFrame *yuv_frame = av_frame_alloc();
AVFrame *rgb_frame = av_frame_alloc();

extern "C"
JNIEXPORT jint JNICALL
Java_com_qy_h264_jni_AVCJniBridge_init__Landroid_view_Surface_2I(JNIEnv *env, jclass type, jobject surface,
                                          jint threadCount) {
    if (is_inited) return 1;
    //设置输出FFmpeg到androidLog
    av_log_set_callback(callback_report);

    if (!_IS_REGISTER_ALL) {
        av_register_all();
        _IS_REGISTER_ALL = 1;
    }

    //查找解码器
    h264_codec = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!h264_codec) {
        return -1;
    }
    //分配内存
    pAVFormatContext = avformat_alloc_context();
    pAVCodecContext = avcodec_alloc_context3(h264_codec);
    if (!pAVCodecContext) {
        return -2;
    }
    pAVCodecContext->thread_count = threadCount;//解码线程数
    pAVCodecContext->codec_type = AVMEDIA_TYPE_VIDEO;
    pAVCodecContext->pix_fmt = AV_PIX_FMT_YUV420P;
    //  pAVFormatContext->flags =pAVFormatContext->flags & AVFMT_FLAG_NOBUFFER;

    if (avcodec_open2(pAVCodecContext, h264_codec, 0) == 0) {
        native_window = ANativeWindow_fromSurface(env, surface);
        is_inited = true;
        return 0;
    }

    yuv_frame = av_frame_alloc();
    rgb_frame = av_frame_alloc();


    return -3;
}


int decodec(AVPacket *avPacket) {
    int ret = -110;
    if (!is_inited) { return ret; }
    //向解码器发送数据
    ret = avcodec_send_packet(pAVCodecContext, avPacket);
    av_log(NULL, AV_LOG_DEBUG, "------------>avcodec_send_packet resultCode::%d", ret);

    if (ret == 0) {
        av_frame_unref(yuv_frame);
        av_frame_unref(rgb_frame);
        // dstFrame分配内存
        u_int8_t *out_buffer = (u_int8_t *) av_malloc(
                static_cast<size_t>(av_image_get_buffer_size(AV_PIX_FMT_ARGB,
                                                             pAVCodecContext->width,
                                                             pAVCodecContext->height, 4)));
        av_image_fill_arrays(rgb_frame->data, rgb_frame->linesize, out_buffer, AV_PIX_FMT_ARGB,
                             pAVCodecContext->width, pAVCodecContext->height, 1);

        ANativeWindow_Buffer window_buffer;
        //获取解码后数据
        ret = avcodec_receive_frame(pAVCodecContext, yuv_frame);
        av_log(NULL, AV_LOG_DEBUG, "<------------avcodec_receive_frame resultCode::%d", ret);
        if (ret == 0) {
            if (yuv_frame->format !=  pAVCodecContext->pix_fmt) {
                return -33;
            }

            av_log(NULL, AV_LOG_DEBUG, "<------------yuv_frame->pts----------------%lli", yuv_frame->pts);
            av_log(NULL, AV_LOG_DEBUG, "<------------yuv_frame->pkt_dt--------------%lli", yuv_frame->pkt_dts);
            // 格式转换关键类
            // 构造函数传入的参数为 原视频的宽高、像素格式、目标的宽高这里也取原视频的宽高（可以修改参数）
            // SWS_BICUBIC算法
            SwsContext *swsContext = sws_getContext(pAVCodecContext->width, pAVCodecContext->height,
                                                    pAVCodecContext->pix_fmt,
                                                    pAVCodecContext->width, pAVCodecContext->height,
                                                    AV_PIX_FMT_RGBA, SWS_BICUBIC, NULL, NULL, NULL);

            ANativeWindow_setBuffersGeometry(native_window, pAVCodecContext->width,
                                             pAVCodecContext->height, WINDOW_FORMAT_RGBA_8888);
            ANativeWindow_lock(native_window, &window_buffer, NULL);
            // 将h264的格式转化成rgb
            // 从srcFrame中的数据（h264）解析成rgb存放到dstFrame中去
            sws_scale(swsContext, yuv_frame->data, yuv_frame->linesize, 0,
                      yuv_frame->height, rgb_frame->data,
                      rgb_frame->linesize
            );
            // 一帧的具体字节大小
            uint8_t *dst = static_cast<uint8_t *>(window_buffer.bits);
            // 每一个像素的字节  ARGB 一共是四个字节
            int dstStride = window_buffer.stride * 4;
            // 像素数据的首地址
            uint8_t *src = rgb_frame->data[0];
            int srcStride = rgb_frame->linesize[0];
            // 将 dstFrame的数据 一行行复制到屏幕上去
            for (int i = 0; i < pAVCodecContext->height; ++i) {
                memcpy(dst + i * dstStride, src + i * srcStride, static_cast<size_t>(srcStride));
            }
            ANativeWindow_unlockAndPost(native_window);
            // 绘制完成之后，回收一帧资源Packet
            av_packet_unref(avPacket);
        }
    } else {
        av_log(NULL, AV_LOG_DEBUG, "yuv_frame receiveCode:%d", ret);
    }


//    av_frame_free(&yuv_frame);
//    av_frame_free(&rgb_frame);
//    av_packet_unref(avPacket);
//    // 回收窗体
//    ANativeWindow_release(native_window);

    return ret;
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_qy_h264_jni_AVCJniBridge_decodeVideo(JNIEnv *env, jclass type, jbyteArray h264buff_) {
    int ret = -110;
    jbyte *h264buff = env->GetByteArrayElements(h264buff_, 0);
    AVPacket *packet = av_packet_alloc();
    av_init_packet(packet);
    packet->data = reinterpret_cast<uint8_t *>(h264buff);
    packet->size = env->GetArrayLength(h264buff_);
    decodec(packet);
    env->ReleaseByteArrayElements(h264buff_, h264buff, 0);
    return ret;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_qy_h264_jni_AVCJniBridge_destroy(JNIEnv *env, jclass type) {
    if (!is_inited) return;
    ANativeWindow_release(native_window);
    // 清理并AVCodecContext空间
    // avcodec_free_context(reinterpret_cast<AVCodecContext **>(pAVCodecContext));
    avcodec_close(pAVCodecContext);
    av_frame_free(&yuv_frame);
    av_frame_free(&rgb_frame);
    is_inited = false;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_qy_h264_jni_AVCJniBridge_test(JNIEnv *env, jclass type, jstring path_,
                                          jobject surface) {
    const char *input = env->GetStringUTFChars(path_, 0);
    if (input == NULL) {
        av_log(NULL, AV_LOG_DEBUG, "input is null,please set input");
        return;
    }
    int ret = avformat_open_input(&pAVFormatContext, input, NULL, NULL);
    if (ret != 0) {
        av_log(NULL, AV_LOG_DEBUG, "avformat_open_input error:%d", ret);
        return;
    }

    //读取一个媒体文件的数据包以获取流信息失败
    ret = avformat_find_stream_info(pAVFormatContext, NULL);
    if (ret < 0) {
        av_log(NULL, AV_LOG_DEBUG, "avformat_find_stream_info error:%d", ret);
        return;
    }

    int video_stream_id = -1;
    // 遍历文件流，找到里面的视频流位置(因为文件可能有多个流（视频流，音频流）等等 )
    // nb_streams 代表流的个数
    for (int i = 0; i < pAVFormatContext->nb_streams; ++i) {
        // AVMEDIA_TYPE_VIDEO 来自 AVMediaType ，里面定义了多个类型
        if (pAVFormatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            video_stream_id = i;
            break;
        }
    }
    // 与此流相关的编解码上下文。由libavformat分配和释放。
    // 获取到编解码上下文。根据视频流id获取
    avcodec_parameters_to_context(pAVCodecContext,
                                  pAVFormatContext->streams[video_stream_id]->codecpar);
    // Find a registered decoder with a matching codec ID.
    // 找到一个带有匹配的编解码器ID的注册解码器。
    // 获取解码器
    h264_codec = avcodec_find_decoder(pAVCodecContext->codec_id);
    //这里会根据视频流信息重置h264_codec_ctx内容
    int codecOpenCode = avcodec_open2(pAVCodecContext, h264_codec, NULL);
    if (codecOpenCode < 0) {
        LOGE("---------------------初始化AVCodec失败-------------");
        return;
    }

    AVPacket *avPacket = av_packet_alloc();
    av_init_packet(avPacket);
    //读取视频流的信息到AVPacket中
    while (av_read_frame(pAVFormatContext, avPacket) >= 0) {//读取每一帧
        decodec(avPacket);
    }
    env->ReleaseStringUTFChars(path_, input);
}







