cmake_minimum_required(VERSION 3.22)
project(lzma2602_jbinding)

# This CMakeLists builds the 7-Zip-JBinding native library (lib7-Zip-JBinding.so)
# from the official LZMA SDK 26.02 + the JBinding C++ bridge.
# It contains ONLY the 7z engine + JBinding bridge (no UnRAR).

# ============================================================
# LZMA SDK 26.02 official engine (7z compress/decompress) + 7-Zip-JBinding bridge
# Based on Format7zR makefile (93 obj + Crypto 4)
# ============================================================
set(LZMA ${CMAKE_CURRENT_SOURCE_DIR}/lzmasdk)

# ---- engine sources (Format7zR, verified present in 26.02) ----
set(SEVENZIP_COMMON_SOURCES
        ${LZMA}/CPP/Common/CRC.cpp
        ${LZMA}/CPP/Common/CrcReg.cpp
        ${LZMA}/CPP/Common/IntToString.cpp
        ${LZMA}/CPP/Common/LzFindPrepare.cpp
        ${LZMA}/CPP/Common/NewHandler.cpp
        ${LZMA}/CPP/Common/MyString.cpp
        ${LZMA}/CPP/Common/StringConvert.cpp
        ${LZMA}/CPP/Common/StringToInt.cpp
        ${LZMA}/CPP/Common/UTFConvert.cpp
        ${LZMA}/CPP/Common/MyVector.cpp
        ${LZMA}/CPP/Common/Wildcard.cpp
        ${LZMA}/CPP/Common/MyWindows.cpp)

set(SEVENZIP_WIN_SOURCES
        ${LZMA}/CPP/Windows/FileDir.cpp
        ${LZMA}/CPP/Windows/FileFind.cpp
        ${LZMA}/CPP/Windows/FileIO.cpp
        ${LZMA}/CPP/Windows/FileLink.cpp
        ${LZMA}/CPP/Windows/FileName.cpp
        ${LZMA}/CPP/Windows/PropVariant.cpp
        ${LZMA}/CPP/Windows/PropVariantConv.cpp
        ${LZMA}/CPP/Windows/Synchronization.cpp
        ${LZMA}/CPP/Windows/System.cpp
        ${LZMA}/CPP/Windows/TimeUtils.cpp)

set(SEVENZIP_7ZIP_COMMON_SOURCES
        ${LZMA}/CPP/7zip/Common/CreateCoder.cpp
        ${LZMA}/CPP/7zip/Common/CWrappers.cpp
        ${LZMA}/CPP/7zip/Common/InBuffer.cpp
        ${LZMA}/CPP/7zip/Common/InOutTempBuffer.cpp
        ${LZMA}/CPP/7zip/Common/FilterCoder.cpp
        ${LZMA}/CPP/7zip/Common/LimitedStreams.cpp
        ${LZMA}/CPP/7zip/Common/MethodId.cpp
        ${LZMA}/CPP/7zip/Common/MethodProps.cpp
        ${LZMA}/CPP/7zip/Common/OutBuffer.cpp
        ${LZMA}/CPP/7zip/Common/ProgressUtils.cpp
        ${LZMA}/CPP/7zip/Common/PropId.cpp
        ${LZMA}/CPP/7zip/Common/StreamBinder.cpp
        ${LZMA}/CPP/7zip/Common/StreamObjects.cpp
        ${LZMA}/CPP/7zip/Common/StreamUtils.cpp
        ${LZMA}/CPP/7zip/Common/UniqBlocks.cpp
        ${LZMA}/CPP/7zip/Common/VirtThread.cpp)

set(SEVENZIP_ARCHIVE_COMMON_SOURCES
        ${LZMA}/CPP/7zip/Archive/DllExports2.cpp
        ${LZMA}/CPP/7zip/Archive/Common/CoderMixer2.cpp
        ${LZMA}/CPP/7zip/Archive/Common/HandlerOut.cpp
        ${LZMA}/CPP/7zip/Archive/Common/InStreamWithCRC.cpp
        ${LZMA}/CPP/7zip/Archive/Common/ItemNameUtils.cpp
        ${LZMA}/CPP/7zip/Archive/Common/OutStreamWithCRC.cpp
        ${LZMA}/CPP/7zip/Archive/Common/ParseProperties.cpp
        ${LZMA}/CPP/7zip/UI/Common/LoadCodecs.cpp
        ${LZMA}/CPP/7zip/UI/Common/PropIDUtils.cpp)

set(SEVENZIP_7Z_SOURCES
        ${LZMA}/CPP/7zip/Archive/7z/7zCompressionMode.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zDecode.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zEncode.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zExtract.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zFolderInStream.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zHandler.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zHandlerOut.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zHeader.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zIn.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zOut.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zProperties.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zSpecStream.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zUpdate.cpp
        ${LZMA}/CPP/7zip/Archive/7z/7zRegister.cpp)

set(SEVENZIP_COMPRESS_SOURCES
        ${LZMA}/CPP/7zip/Compress/CodecExports.cpp
        ${LZMA}/CPP/7zip/Compress/Bcj2Coder.cpp
        ${LZMA}/CPP/7zip/Compress/Bcj2Register.cpp
        ${LZMA}/CPP/7zip/Compress/BcjCoder.cpp
        ${LZMA}/CPP/7zip/Compress/BcjRegister.cpp
        ${LZMA}/CPP/7zip/Compress/BranchMisc.cpp
        ${LZMA}/CPP/7zip/Compress/BranchRegister.cpp
        ${LZMA}/CPP/7zip/Compress/ByteSwap.cpp
        ${LZMA}/CPP/7zip/Compress/CopyCoder.cpp
        ${LZMA}/CPP/7zip/Compress/CopyRegister.cpp
        ${LZMA}/CPP/7zip/Compress/DeltaFilter.cpp
        ${LZMA}/CPP/7zip/Compress/Lzma2Decoder.cpp
        ${LZMA}/CPP/7zip/Compress/Lzma2Encoder.cpp
        ${LZMA}/CPP/7zip/Compress/Lzma2Register.cpp
        ${LZMA}/CPP/7zip/Compress/LzmaDecoder.cpp
        ${LZMA}/CPP/7zip/Compress/LzmaEncoder.cpp
        ${LZMA}/CPP/7zip/Compress/LzmaRegister.cpp)

set(SEVENZIP_C_SOURCES
        ${LZMA}/C/7zStream.c
        ${LZMA}/C/7zCrc.c
        ${LZMA}/C/7zCrcOpt.c
        ${LZMA}/C/Alloc.c
        ${LZMA}/C/Bcj2.c
        ${LZMA}/C/Bcj2Enc.c
        ${LZMA}/C/Bra.c
        ${LZMA}/C/Bra86.c
        ${LZMA}/C/BraIA64.c
        ${LZMA}/C/CpuArch.c
        ${LZMA}/C/Delta.c
        ${LZMA}/C/LzFind.c
        ${LZMA}/C/LzFindOpt.c
        ${LZMA}/C/LzFindMt.c
        ${LZMA}/C/Lzma2Dec.c
        ${LZMA}/C/Lzma2DecMt.c
        ${LZMA}/C/Lzma2Enc.c
        ${LZMA}/C/LzmaDec.c
        ${LZMA}/C/LzmaEnc.c
        ${LZMA}/C/MtCoder.c
        ${LZMA}/C/MtDec.c
        ${LZMA}/C/SwapBytes.c
        ${LZMA}/C/Threads.c
        ${LZMA}/C/Aes.c
        ${LZMA}/C/AesOpt.c
        ${LZMA}/C/Sha256.c
        ${LZMA}/C/Sha256Opt.c)

# ---- Crypto (7z AES encryption, required when Z7_NO_CRYPTO is not defined) ----
set(SEVENZIP_CRYPTO_SOURCES
        ${LZMA}/CPP/7zip/Crypto/7zAes.cpp
        ${LZMA}/CPP/7zip/Crypto/7zAesRegister.cpp
        ${LZMA}/CPP/7zip/Crypto/MyAes.cpp
        ${LZMA}/CPP/7zip/Crypto/MyAesReg.cpp
        ${LZMA}/CPP/7zip/Crypto/RandGen.cpp)

set(SEVENZIP_ENGINE_SOURCES
        ${SEVENZIP_COMMON_SOURCES}
        ${SEVENZIP_WIN_SOURCES}
        ${SEVENZIP_7ZIP_COMMON_SOURCES}
        ${SEVENZIP_ARCHIVE_COMMON_SOURCES}
        ${SEVENZIP_7Z_SOURCES}
        ${SEVENZIP_COMPRESS_SOURCES}
        ${SEVENZIP_C_SOURCES}
        ${SEVENZIP_CRYPTO_SOURCES})

# ---- 7-Zip-JBinding bridge (Java<->C++, 23 sources) ----
set(JBINDING_SOURCES
        jbinding-cpp/Debug.cpp
        jbinding-cpp/JNITools.cpp
        jbinding-cpp/JavaStaticInfo.cpp
        jbinding-cpp/SevenZipJBinding.cpp
        jbinding-cpp/UniversalArchiveOpenCallback.cpp
        jbinding-cpp/UserTrace.cpp
        jbinding-cpp/CodecTools.cpp
        jbinding-cpp/JBindingTools.cpp
        jbinding-cpp/CHeadCacheInStream.cpp
        jbinding-cpp/JavaToCPP/JavaToCPPSevenZip.cpp
        jbinding-cpp/JavaToCPP/JavaToCPPInArchiveImpl.cpp
        jbinding-cpp/JavaToCPP/JavaToCPPOutArchiveImpl.cpp
        jbinding-cpp/CPPToJava/CPPToJavaArchiveExtractCallback.cpp
        jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenCallback.cpp
        jbinding-cpp/CPPToJava/CPPToJavaArchiveOpenVolumeCallback.cpp
        jbinding-cpp/CPPToJava/CPPToJavaCryptoGetTextPassword.cpp
        jbinding-cpp/CPPToJava/CPPToJavaInStream.cpp
        jbinding-cpp/CPPToJava/CPPToJavaOutStream.cpp
        jbinding-cpp/CPPToJava/CPPToJavaProgress.cpp
        jbinding-cpp/CPPToJava/CPPToJavaSequentialInStream.cpp
        jbinding-cpp/CPPToJava/CPPToJavaSequentialOutStream.cpp
        jbinding-cpp/CPPToJava/CPPToJavaArchiveUpdateCallback.cpp)

# ---- JNI shared lib: 7z (engine + bridge unified, avoids duplicate IID_XXX symbols) ----
add_library(sevenzip-jni SHARED)
target_sources(sevenzip-jni PRIVATE ${SEVENZIP_ENGINE_SOURCES} ${JBINDING_SOURCES})
target_include_directories(sevenzip-jni PRIVATE
        ${LZMA}/CPP
        ${LZMA}/CPP/Common
        ${LZMA}/CPP/Windows
        ${LZMA}/C
        jbinding-cpp
        jbinding-cpp/PlatformUnix
        jbinding-cpp/javah)
target_compile_definitions(sevenzip-jni PRIVATE
        ENV_UNIX
        _UNIX
        UNIX_USE_WIN_FILE
        _FILE_OFFSET_BITS=64
        _LARGEFILE_SOURCE
        _REENTRANT
        BREAK_HANDLER
        UNICODE
        _UNICODE)
target_compile_options(sevenzip-jni PRIVATE
        -fexceptions
        -fno-strict-aliasing
        -fvisibility=hidden)

# AES/SHA2 HW-accelerated files need arm64 crypto extensions (vaeseq_u8/vsha256hq_u32 etc.)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set_source_files_properties(
            ${LZMA}/C/AesOpt.c
            ${LZMA}/C/Sha256Opt.c
            PROPERTIES COMPILE_OPTIONS "-march=armv8-a+crypto")
endif()
set_target_properties(sevenzip-jni PROPERTIES
        CXX_STANDARD 11
        CXX_STANDARD_REQUIRED ON
        # Output name MUST be lib7-Zip-JBinding.so: net.sf.sevenzipjbinding.SevenZip's
        # Android init branch calls System.loadLibrary("7-Zip-JBinding") (after stripping lib/.so).
        OUTPUT_NAME "7-Zip-JBinding")
target_link_libraries(sevenzip-jni PRIVATE android log)
# Android 15+ 16KB page alignment
target_link_options(sevenzip-jni PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
