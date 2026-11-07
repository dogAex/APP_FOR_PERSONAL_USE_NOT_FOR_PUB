#include <jni.h>
#include <string>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>

#define LOG_TAG "QG_ReShade_Core"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_qg_reshade_MainActivity_probeKernel(JNIEnv* env, jobject) {
    int fd = open("/dev/kgsl-3d0", O_RDWR);
    if (fd >= 0) {
        LOGI("内核节点 kgsl-3d0 打开成功 (fd: %d)", fd);
        close(fd);
        return env->NewStringUTF("内核通道连通 (fd: OK)");
    } else {
        LOGI("内核受限，等待 Shizuku 介入");
        return env->NewStringUTF("底层受限，仅渲染表层");
    }
}