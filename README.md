# 7-Zip-JBinding × LZMA SDK 26.02 (Android) Port

> The first usable JBinding port that can **create (compress) 7z split / encrypted archives** correctly on Android.

**Release:** https://github.com/1090442016/lzma-sdk-26.02-android-jbinding/releases/tag/v1.0.0

`sevenzipjbinding` (JBinding) is the Java/JNI bridge for the official 7-Zip C++ SDK, which originally only
supported **reading** archives. The official SDK stayed on the p7zip 16.02 branch for years, while the upstream
7-Zip C++ SDK has evolved to **26.02** (higher compression ratio, faster, many CVE fixes). This repo wires
**LZMA SDK 26.02** into JBinding's `jbinding-cpp` bridge and fixes several fatal bugs on the compression path,
so that Android can now **create 7z (plain / encrypted / split / encrypted+split)** smoothly.

## Directory layout

```
7z-jbinding-port/
├── README.md                          # This file: root cause / fixes / perf / usage
├── LICENSE                            # LGPL-2.1+ notice
├── THIRD_PARTY_LICENSES.md            # License summary
├── SplitSevenZOutStream.kt            # Split output stream for the official engine (IOutStream)
├── JBinding7zArchiveExplorer.kt       # Compression engine (compress impl)
├── jbinding_myunknown_compat.h        # 16.02→26.02 COM macro compat layer
├── archive-abstractions.md            # App-layer types that JBinding7zArchiveExplorer depends on
├── cmake/
│   └── jbinding-cpp_CMakeLists.txt    # Key CMake snippets
├── patches/
│   └── CPPToJavaSequentialInStream.cpp.patch  # DirectByteBuffer fix
├── prebuilt/                          # Prebuilt .so (drop-in, no build needed)
│   ├── arm64-v8a/lib7-Zip-JBinding.so
│   └── armeabi-v7a/lib7-Zip-JBinding.so
└── native/                            # Standalone NDK project to build .so from source
    ├── settings.gradle
    ├── build.gradle / gradle.properties
    ├── gradlew / gradlew.bat / gradle/
    ├── sevenzip/build.gradle
    ├── sevenzip/cpp/                  # lzmasdk/ + jbinding-cpp/ + CMakeLists.txt
    └── sevenzip/java/net/sf/sevenzipjbinding/   # JBinding Java classes
```

## Quick start (prebuilt, no build)

1. Copy `prebuilt/arm64-v8a/lib7-Zip-JBinding.so` and `prebuilt/armeabi-v7a/lib7-Zip-JBinding.so`
   into your app's `src/main/jniLibs/<abi>/`.
2. Add the JBinding Java classes from `native/sevenzip/java/net/...` to your project.
3. Use `SplitSevenZOutStream.kt` + `JBinding7zArchiveExplorer.kt` (repo root) to create 7z
   (plain / AES256-encrypted / split volumes). See `prebuilt/README.md`.

## Build from source

```bash
cd native && ./gradlew assembleRelease
```

See `native/README.md` for prerequisites and details.

## Why you need this

| Approach | Problem |
|---|---|
| commons-compress 1.28 `SevenZOutputFile` | LZMA2 batch encoding blocks `write()` for **3.5–9 s**, freezes progress; 30 MB file takes ~32 s |
| Old JBinding (p7zip 16.02 core) | Outdated core, no 26.02 speed/memory optimizations |
| **This port (26.02 + JBinding)** | **4–5× faster**, smooth progress, no stalls; supports encryption + splitting |

Benchmark (same 29.9 MB mp4, level 6, split 15 MB):

| Engine | Time | Behavior |
|---|---|---|
| commons-compress | ~32 s | 3.7 s stall at each volume boundary, 10 s on first volume |
| **This port (26.02)** | **~12 s** | Smooth, no stalls |

## Key fixes

### 1. DirectByteBuffer local-reference leak (SEGV crash)

**Root cause**: JBinding's original `CPPToJavaSequentialInStream::Read` used
`NewByteArray + GetByteArrayElements + DeleteLocalRef` to allocate a `jbyteArray` per read. During compression,
the dense LZMA2 small-block read loop overflowed the JNI local reference table and triggered random
`SEGV_ACCERR` crashes.

**Fix**: wrap the native write buffer with `NewDirectByteBuffer(data, size)` and let the Java side read
straight into native memory — **zero array allocation, zero copy, zero local references**.

```cpp
// CPPToJavaSequentialInStream.cpp
ScopedLocalRef<jobject> buffer(jniEnvInstance,
    jniEnvInstance->NewDirectByteBuffer(data, size));
jint wasRead = _iSequentialInStream->read(jniEnvInstance,
    _javaImplementation, buffer.get(), size);
```

Java side (see `JBinding7zArchiveExplorer.kt`):

```kotlin
override fun read(data: ByteBuffer, len: Int): Int {
    val oldLimit = data.limit()
    if (len < oldLimit) data.limit(len)
    val read = channel.read(data)   // DirectByteBuffer reads directly into native memory
    data.limit(oldLimit)
    return if (read < 0) 0 else read
}
```

### 2. 16.02 → 26.02 COM interface macro changes

26.02 removed the `MY_UNKNOWN_IMP*` macros and switched to `Z7_COM_UNKNOWN_IMP_*`, and the new macros generate
`QueryInterface/AddRef/Release` as `private final`, which subclasses cannot override or cross-call.

**Fix**: `jbinding_myunknown_compat.h` redefines the relevant macros as `public` + overridable after including
`Common/MyCom.h`, and provides `MY_UNKNOWN_IMP*` → `Z7_COM_UNKNOWN_IMP_*` mappings, so the JBinding bridge
classes compile without per-file rewrites.

```cpp
#include "Common/MyCom.h"
// Redefine Z7_COM_QI_BEGIN / Z7_COM_ADDREF_RELEASE as public
// Provide MY_UNKNOWN_IMP / MY_UNKNOWN_IMP1..5 mappings
```

### 3. Encrypted 7z (AES256)

JBinding's `IOutCreateCallback` does **not** automatically inherit `ICryptoGetTextPassword`. If the callback
does not implement that interface explicitly, the 7z engine will not encrypt.

**Fix**: the callback `implements ICryptoGetTextPassword`, and `cryptoGetTextPassword()` returns the password:

```kotlin
object : IOutCreateCallback<IOutItem7z>, ICryptoGetTextPassword {
    override fun cryptoGetTextPassword(): String? =
        password?.takeIf { it.isNotEmpty() }   // null/empty → no encryption
    // ...
}
```

> ⚠️ **Do not return an empty string**: the engine treats an empty string as "password is empty" and sets
> `passwordIsDefined = true`, encrypting with an empty password. When there is no password, return `null`.

> This port **does not enable header encryption** (`setHeaderEncryption` is not called), so the header stays
> unencrypted and file names are visible — same behavior as commons-compress output. Browse/extract logic needs
> no changes; the extract side reads transparently via `MultiReadOnlySeekableByteChannel`.

### 4. Split 7z (`.7z.001` / `.7z.002` ...)

The official engine writes sequentially through `IOutStream` and `seek`s back to the volume head to rewrite the
signature header. The original JBinding had no split output stream implementation.

**Fix**: `SplitSevenZOutStream` (implements JBinding `IOutStream`) splits by `splitLength`:

- Working file = target path (`name.7z`); when a volume is full, `rename` it to `.7z.0NN` and create a new one
- `seek` supports redirecting to an already-cut historical volume (engine rewrites start header at volume head)
- `write` loops across volume boundaries; `write/seek/setSize` are all `synchronized`
- DirectByteBuffer write goes through `FileChannel` with zero copy
- Pure byte slicing — same shape as commons-compress's `SplitSevenZChannel` output, so **the extract side needs
  no changes**

```kotlin
val outStream: IOutStream = if (splitSize > 0) {
    SplitSevenZOutStream(destinationFile, splitSize)
} else {
    RandomAccessFileOutStream(RandomAccessFile(destinationFile, "rw"))
}
```

### 5. LZMA2 multi-thread memory safety

Each thread has its own dictionary buffer. At level 6 (8 MiB dictionary), each thread can use ~100 MB.
**Never call `setThreadCount(0)`** (auto = CPU core count; an 8-core device may eat 800 MB+ and trigger the
OOM Killer). Limit threads to 2–4 based on available memory:

```kotlin
outArchive.setThreadCount(
    when {
        availMb >= 4096 -> 4
        availMb >= 2048 -> 3
        else -> 2
    }
)
```

### 6. Failure cleanup

When compression fails (insufficient permission, disk full, etc.), the already-generated split volumes
`name.7z.0NN` + the target file must be cleaned up to avoid leaving dirty files. See
`JBinding7zArchiveExplorer.cleanupSplitFiles()`.

## File list (minimal reusable set)

| File | Purpose |
|---|---|
| `SplitSevenZOutStream.kt` | Split output stream for the official engine (IOutStream) |
| `JBinding7zArchiveExplorer.kt` | Compression engine (compress impl; depends on `ArchiveExplorer` interface + `SevenZip` API) |
| `jbinding_myunknown_compat.h` | 16.02→26.02 COM compatibility layer |
| `patches/CPPToJavaSequentialInStream.cpp.patch` | DirectByteBuffer fix patch |
| `cmake/jbinding-cpp_CMakeLists.txt` | Key CMake snippets (source paths / compiler flags / linking) |
| `THIRD_PARTY_LICENSES.md` | License summary |

> `JBinding7zArchiveExplorer.kt` depends on a few app-layer types (`ArchiveExplorer`, `ArchiveException`,
> `ExtractProgress`, `FileHelper.ConflictStrategy`). These are simple interfaces / data classes — replace them
> with your own abstractions when publishing, or declare them yourself (see `archive-abstractions.md`). For
> browse/extract, keep using your existing commons-compress / official `SevenZFile`.

## Build notes

1. **LZMA SDK 26.02 source path changes** (vs 16.02):
   - `Windows/Defs.h` → `Common/Defs.h`
   - `Windows/Error.h` removed
   - `LZMA_Alone/` directory gone entirely
   - `Windows/ErrorMsg.cpp` etc. need adjusted includes
2. **CMake**: point `P7ZIP_SRC` to the 26.02 `CPP` directory (no longer the p7zip branch), and add
   `CPP/myWindows`, `CPP/include_windows` to `include_directories`. Use flags
   `-DUNICODE -D_UNICODE -DENV_UNIX -DUNIX_USE_WIN_FILE` (non-MinGW platforms).
3. **JNI headers**: run `javah` once to generate `net_sf_sevenzipjbinding_*.h` (see CMakeLists
   `add_custom_command`).
4. **Linking**: on non-MinGW platforms `TARGET_LINK_LIBRARIES(7-Zip-JBinding dl c pthread)`.

The full `SEVEN_ZIP_SOURCE_FILES` / `JBINDING_CPP_FILES` lists are in
`cmake/jbinding-cpp_CMakeLists.txt`.

## License

- **LZMA SDK**: public domain (Igor Pavlov)
- **7-Zip-JBinding bridge**: LGPL-2.1-or-later
- **This port's patches** (compat header, DirectByteBuffer fix, SplitSevenZOutStream, compression engine):
  provided under the same LGPL terms

See `THIRD_PARTY_LICENSES.md` for details.
