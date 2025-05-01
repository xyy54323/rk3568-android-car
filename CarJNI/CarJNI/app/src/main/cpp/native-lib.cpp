#include <jni.h>
#include <string>
#include <android/log.h>
#include <asm-generic/fcntl.h>
#include <fcntl.h>
#include <unistd.h>

int fd = 0;

extern "C" JNIEXPORT jint JNICALL
Java_com_example_carjni_MainActivity_MyDeviceOpen(
        JNIEnv* env,
        jobject /* this */) {
    fd = open("/dev/mydevice", O_RDWR | O_NDELAY | O_NOCTTY);

    if (fd < 0) {
        __android_log_print(ANDROID_LOG_INFO, "serial", "open error");
    }else {
        __android_log_print(ANDROID_LOG_INFO, "serial", "open success fd=%d",fd);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_carjni_MainActivity_MyDeviceClose(
        JNIEnv* env,
        jobject /* this */) {
    if (fd > 0) {
        close(fd);
    }
    return 0;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_carjni_MainActivity_controlCar(JNIEnv *env, jobject, jint angle, jint speed) {


    ioctl(fd, speed, angle);
}