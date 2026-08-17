#include <ScopedLocalRef.h>
#include "SevenZipJBinding.h"

#include "JBindingTools.h"
#include "CPPToJavaSequentialInStream.h"


STDMETHODIMP CPPToJavaSequentialInStream::Read(void *data, UInt32 size, UInt32 *processedSize)
{
    TRACE_OBJECT_CALL("Read");

    JNIEnvInstance jniEnvInstance(_jbindingSession);

    if (processedSize) {
    	*processedSize = 0;
    }

    // ⚠️ DirectByteBuffer 修复（2026-08-17 移植自 android-p7zip）：
    // 原版用 NewByteArray + GetByteArrayElements + DeleteLocalRef 每块分配 byte[]，
    // 压缩密集读取时 JNI local reference table 溢出 → SEGV_ACCERR 崩溃。
    // NewDirectByteBuffer 直接包装 native 内存（data 指针）→ 零数组创建、零复制、
    // 零局部引用。Java 侧 read(ByteBuffer, len) 读进 buffer 即写入 native 内存。
    ScopedLocalRef<jobject> buffer(jniEnvInstance, jniEnvInstance->NewDirectByteBuffer(data, size));

	if (!buffer.get()) {
	    jniEnvInstance.reportError("Out of local resources or out of memory");
	}

	jint wasRead = _iSequentialInStream->read(jniEnvInstance, _javaImplementation, buffer.get(), size);
	if (jniEnvInstance.exceptionCheck())
	{
		return S_FALSE;
	}

	if (processedSize)
	{
		*processedSize = (UInt32)wasRead;
	}

	return S_OK;
}

