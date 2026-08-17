#ifndef JBINDING_MYUNKNOWN_COMPAT_H_
#define JBINDING_MYUNKNOWN_COMPAT_H_

// ⚠️ 16.02 → 26.02 兼容层（2026-08-17 移植）
// 官方 LZMA SDK 26.02 移除了 MY_UNKNOWN_IMP* 宏，改用 Z7_COM_UNKNOWN_IMP_*。
// 语义完全等价（QueryInterface + AddRef/Release），此处建立映射避免逐文件改。
// 本头必须在 include "Common/MyCom.h"（26.02）之后包含。

#include "Common/MyCom.h"

// ⚠️ 26.02 的 Z7_COM_ADDREF_RELEASE / Z7_COM_QI_BEGIN 生成带 Z7_final 的 private 方法，
// JBinding 桥接类需要：1) 子类覆盖父类 QueryInterface/AddRef/Release（合并多接口）
// 2) 从子类调用父类 AddRef/Release。因此此处重定义为 public + 可覆盖版本（对齐 16.02 语义）。
// 注意：宏展开时 Z7_COM_UNKNOWN_IMP_* 内部引用的就是这些宏，重定义后自动生效。
#ifdef Z7_COM_QI_BEGIN
#undef Z7_COM_QI_BEGIN
#endif
#define Z7_COM_QI_BEGIN \
  public: STDMETHOD(QueryInterface) (REFGUID iid, void **outObject) throw() Z7_override \
    { *outObject = NULL;

#ifdef Z7_COM_ADDREF_RELEASE
#undef Z7_COM_ADDREF_RELEASE
#endif
#define Z7_COM_ADDREF_RELEASE \
  public: \
  STDMETHOD_(ULONG, AddRef)() throw() Z7_override \
    { return ++_m_RefCount; } \
  STDMETHOD_(ULONG, Release)() throw() Z7_override \
    { if (--_m_RefCount != 0) return _m_RefCount; \
      delete this;  return 0; }

#define MY_UNKNOWN_IMP           Z7_COM_UNKNOWN_IMP_0
#define MY_UNKNOWN_IMP1(i1)      Z7_COM_UNKNOWN_IMP_1(i1)
#define MY_UNKNOWN_IMP2(i1, i2)  Z7_COM_UNKNOWN_IMP_2(i1, i2)
#define MY_UNKNOWN_IMP3(i1, i2, i3) Z7_COM_UNKNOWN_IMP_3(i1, i2, i3)
#define MY_UNKNOWN_IMP4(i1, i2, i3, i4) Z7_COM_UNKNOWN_IMP_4(i1, i2, i3, i4)
#define MY_UNKNOWN_IMP5(i1, i2, i3, i4, i5) Z7_COM_UNKNOWN_IMP_5(i1, i2, i3, i4, i5)

#endif // JBINDING_MYUNKNOWN_COMPAT_H_
