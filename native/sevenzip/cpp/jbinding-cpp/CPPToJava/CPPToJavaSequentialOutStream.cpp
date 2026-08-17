#include <ScopedLocalRef.h>
#include "SevenZipJBinding.h"

#include "JNITools.h"
#include "CPPToJavaSequentialOutStream.h"

STDMETHODIMP CPPToJavaSequentialOutStream::Write(const void *data, UInt32 size,
                                                 UInt32 *processedSize) {
    TRACE_OBJECT_CALL("Write");

    if (processedSize) {
        *processedSize = 0;
    }

    if (size == 0) {
        return S_OK;
    }

    JNIEnvInstance jniEnvInstance(_jbindingSession);

    // ⚠️ DirectByteBuffer 修复（2026-08-17 移植自 android-p7zip）：
    // 原版用 NewByteArray + SetByteArrayRegion 每块分配 byte[] 并复制数据，
    // 压缩密集写入时频繁分配 + GC 抖动 + 局部引用管理 → 崩溃。
    // NewDirectByteBuffer 直接包装 native 内存（data 指针），Java 侧 write(ByteBuffer)
    // 直接读 native 内存 → 零数组创建、零复制、零局部引用。
    ScopedLocalRef<jobject> dataBuffer(jniEnvInstance, jniEnvInstance->NewDirectByteBuffer((void*) data, size));

    if (!dataBuffer.get()) {
        jniEnvInstance.reportError("Out of local resources or out of memory");
    }

    // public int write(ByteBuffer buffer, int len);
    jint result = _iSequentialOutStream->write(jniEnvInstance, _javaImplementation, dataBuffer.get(), size);
    if (jniEnvInstance.exceptionCheck()) {
        return S_FALSE;
    }
    *processedSize = (UInt32) result;

    if (result <= 0) {
        jniEnvInstance.reportError("Implementation of 'int ISequentialOutStream.write(ByteBuffer)' "
            "should write at least one byte. Returned amount of written bytes: %i", result);
        return S_FALSE;
    }

    return S_OK;
}

