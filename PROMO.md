# Promotion copy (English)

Copy-paste to Reddit (r/androiddev / r/Android), XDA, or Twitter/X.

---

## Post (Reddit / XDA)

**Title: Porting official LZMA SDK 26.02 to 7-Zip-JBinding for Android**

I ported the official 7-Zip C++ SDK 26.02 into 7-Zip-JBinding's JNI bridge, so Android can now
*create* 7z archives (plain / AES256-encrypted / split volumes / encrypted+split) through the
official engine instead of commons-compress.

Background: commons-compress `SevenZOutputFile` blocks `write()` for 3.5–9s during LZMA2 batch
encoding — compressing a 30 MB file took ~32s with a frozen progress bar. The 26.02 engine does
it in ~12s, smooth.

Key changes:
- DirectByteBuffer local-reference leak that caused random SEGV crashes during compression
- 16.02→26.02 COM macro re-map (`MY_UNKNOWN_IMP*` → `Z7_COM_UNKNOWN_IMP_*`)
- AES256 encryption via `ICryptoGetTextPassword`
- `SplitSevenZOutStream` (IOutStream) for `.7z.001` split volumes
- LZMA2 thread-count cap by available RAM (no OOM)

Repo: https://github.com/1090442016/lzma-sdk-26.02-android-jbinding  (LGPL-2.1, minimal reusable patch set)

---

## One-line tweet

Porting official LZMA SDK 26.02 to 7-Zip-JBinding for Android: now you can create encrypted/split
7z archives via the official engine, ~4-5x faster than commons-compress. Repo:
https://github.com/1090442016/lzma-sdk-26.02-android-jbinding
