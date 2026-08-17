# Building lib7-Zip-JBinding.so from source

This directory contains a standalone, minimal Android project that builds
`lib7-Zip-JBinding.so` from the official **LZMA SDK 26.02** + the **7-Zip-JBinding**
C++ bridge. It contains only the 7z engine + JBinding bridge (no UnRAR).

## Prerequisites

- Android SDK (set `local.properties` with `sdk.dir=...`)
- Android NDK `26.3.11579264` (or change `ndkVersion` in `sevenzip/build.gradle`)
- JDK 17

## Build

```bash
cd native
./gradlew assembleRelease
```

Output (stripped, release):

```
sevenzip/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a/lib7-Zip-JBinding.so
sevenzip/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/armeabi-v7a/lib7-Zip-JBinding.so
```

Or just copy the prebuilt ones from `../prebuilt/`.

## What is inside

- `cpp/lzmasdk/` — official LZMA SDK 26.02 (public domain, Igor Pavlov)
- `cpp/jbinding-cpp/` — 7-Zip-JBinding C++ bridge (LGPL-2.1)
- `cpp/CMakeLists.txt` — engine + bridge build (7z compress/decompress, AES256)
- `java/net/sf/sevenzipjbinding/` — JBinding Java classes (LGPL-2.1)
- `sevenzip/build.gradle` — Android library module wiring

## Notes

- The output library name is fixed to `lib7-Zip-JBinding.so` because
  `net.sf.sevenzipjbinding.SevenZip` calls `System.loadLibrary("7-Zip-JBinding")`.
- AES/SHA2 use arm64 crypto extensions (`-march=armv8-a+crypto`) for speed.
- 16 KB page alignment is set for Android 15+ compatibility.
