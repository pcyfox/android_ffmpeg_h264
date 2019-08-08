#ifndef AUDIOVIDEO_FFMPEG_LOG_H
#define AUDIOVIDEO_FFMPEG_LOG_H
#ifdef __cplusplus
extern "C"{
#endif


#include "libavutil/log.h"
#include "../../../../../../../../Library/Android/sdk/ndk-bundle/sysroot/usr/include/android/log.h"
#include "../../../../../../../../Library/Android/sdk/ndk-bundle/toolchains/llvm/prebuilt/darwin-x86_64/lib64/clang/6.0.2/include/stdarg.h"

#define FF_LOG_TAG "FFMPEG_LOG_"

#define VLOG(level, TAG, ...)    ((void)__android_log_vprint(level, TAG, __VA_ARGS__))
#define VLOGV(...)  VLOG(ANDROID_LOG_VERBOSE,   FF_LOG_TAG, __VA_ARGS__)
#define VLOGD(...)  VLOG(ANDROID_LOG_DEBUG,     FF_LOG_TAG, __VA_ARGS__)
#define VLOGI(...)  VLOG(ANDROID_LOG_INFO,      FF_LOG_TAG, __VA_ARGS__)
#define VLOGW(...)  VLOG(ANDROID_LOG_WARN,      FF_LOG_TAG, __VA_ARGS__)
#define VLOGE(...)  VLOG(ANDROID_LOG_ERROR,     FF_LOG_TAG, __VA_ARGS__)


#define ALOG(level, TAG, ...)    ((void)__android_log_print(level, TAG, __VA_ARGS__))
#define ALOGV(...)  ALOG(ANDROID_LOG_VERBOSE,   FF_LOG_TAG, __VA_ARGS__)
#define ALOGD(...)  ALOG(ANDROID_LOG_DEBUG,     FF_LOG_TAG, __VA_ARGS__)
#define ALOGI(...)  ALOG(ANDROID_LOG_INFO,      FF_LOG_TAG, __VA_ARGS__)
#define ALOGW(...)  ALOG(ANDROID_LOG_WARN,      FF_LOG_TAG, __VA_ARGS__)
#define ALOGE(...)  ALOG(ANDROID_LOG_ERROR,     FF_LOG_TAG, __VA_ARGS__)

static void callback_report(void *ptr, int level, const char *fmt, va_list vl) {
    int ffplv;
    switch (level){
        case AV_LOG_ERROR:
            ffplv = ANDROID_LOG_ERROR;
            break;
        case AV_LOG_WARNING:
            ffplv = ANDROID_LOG_WARN;
            break;
        case AV_LOG_INFO:
            ffplv = ANDROID_LOG_INFO;
            break;
        case AV_LOG_VERBOSE:
            ffplv=ANDROID_LOG_VERBOSE;
        default:
            ffplv = ANDROID_LOG_DEBUG;
            break;
    }
    va_list vl2;
    char line[1024];
    static int print_prefix = 1;
    va_copy(vl2, vl);
    av_log_format_line(ptr, level, fmt, vl2, line, sizeof(line), &print_prefix);
    va_end(vl2);
    ALOG(ffplv, FF_LOG_TAG, "%s", line);
}

#ifdef __cplusplus
}
#endif

#endif //AUDIOVIDEO_FFMPEG_LOG_H
