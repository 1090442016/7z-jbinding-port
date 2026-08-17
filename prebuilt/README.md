# Prebuilt lib7-Zip-JBinding.so (drop-in, no build required)

These are release builds of `lib7-Zip-JBinding.so` for the two most common Android
ABIs. Use them directly if you do not want to compile from source.

```
arm64-v8a/lib7-Zip-JBinding.so      (939 KB)
armeabi-v7a/lib7-Zip-JBinding.so     (583 KB)
```

## How to use

Copy each `.so` into your app module:

```
app/src/main/jniLibs/arm64-v8a/lib7-Zip-JBinding.so
app/src/main/jniLibs/armeabi-v7a/lib7-Zip-JBinding.so
```

Then add the JBinding Java classes (from `../native/java/net/...`) to your project,
and use `SplitSevenZOutStream.kt` + `JBinding7zArchiveExplorer.kt` from the repo root
to create 7z archives (plain / AES256-encrypted / split volumes).

## Provenance

Built from this repo's `native/` project (`assembleRelease`) against LZMA SDK 26.02
+ 7-Zip-JBinding. Source is in `../native/`; rebuild anytime to verify.

License: LZMA SDK is public domain; JBinding bridge and this port are LGPL-2.1-or-later.
