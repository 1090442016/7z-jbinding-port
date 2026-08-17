# Promotion copy (English + Chinese)

Copy-paste to Reddit (r/androiddev / r/Android), XDA, Coolapk, or Twitter/X.

---

## English (Reddit / XDA / Twitter)

**Title: First working 7-Zip-JBinding port on LZMA SDK 26.02 for Android**

I ported the official 7-Zip C++ SDK 26.02 into 7-Zip-JBinding's JNI bridge so Android can finally
*create* 7z archives (plain / AES256-encrypted / split volumes / encrypted+split) through the official
engine instead of commons-compress.

Why it matters: commons-compress `SevenZOutputFile` blocks `write()` for 3.5–9s during LZMA2 batch
encoding — compressing a 30 MB file took ~32s with a frozen progress bar. The 26.02 engine does it in
~12s, smooth.

Key fixes:
- DirectByteBuffer local-reference leak that caused random SEGV crashes during compression
- 16.02→26.02 COM macro re-map (`MY_UNKNOWN_IMP*` → `Z7_COM_UNKNOWN_IMP_*`)
- AES256 encryption via `ICryptoGetTextPassword`
- `SplitSevenZOutStream` (IOutStream) for `.7z.001` split volumes
- LZMA2 thread-count cap by available RAM (no OOM)

Repo: https://github.com/1090442016/7z-jbinding-port  (LGPL-2.1, minimal reusable patch set)

---

## 中文（酷安 / V2EX / 少数派）

**标题：7-Zip-JBinding 接入官方 LZMA SDK 26.02 的 Android 移植，终于能在 Android 上正常压缩 7z 了**

把官方 7-Zip C++ SDK 26.02 移植进了 7-Zip-JBinding 的 JNI 桥接层，让 Android 可以通过官方引擎
*创建* 7z 归档（普通 / AES256 加密 / 分卷 / 加密+分卷），不再依赖 commons-compress。

痛点：commons-compress 的 `SevenZOutputFile` 在 LZMA2 批量编码时 `write()` 阻塞 3.5–9 秒，压缩一个
30MB 文件要 ~32 秒且进度条冻结；换 26.02 官方引擎只需 ~12 秒，进度平滑。

主要修复：
- 压缩时 DirectByteBuffer 局部引用泄漏导致的随机 SEGV 崩溃
- 16.02→26.02 的 COM 宏重映射（`MY_UNKNOWN_IMP*` → `Z7_COM_UNKNOWN_IMP_*`）
- 通过 `ICryptoGetTextPassword` 实现 AES256 加密
- `SplitSevenZOutStream`（IOutStream）实现 `.7z.001` 分卷
- 按可用内存限制 LZMA2 线程数，避免 OOM

仓库：https://github.com/1090442016/7z-jbinding-port  （LGPL-2.1，最小可复用补丁集）

---

## One-line tweet

Just ported official 7-Zip LZMA SDK 26.02 into 7-Zip-JBinding for Android — now you can *create*
encrypted/split 7z archives, 4-5x faster than commons-compress. Minimal patch set, LGPL-2.1:
https://github.com/1090442016/7z-jbinding-port
