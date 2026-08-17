#ifndef JBINDING_MYUNKNOWN_COMPAT_H_
#define JBINDING_MYUNKNOWN_COMPAT_H_

// ⚠️ 16.02 → 26.02 compatibility layer (ported 2026-08-17)
// Official LZMA SDK 26.02 removed the MY_UNKNOWN_IMP* macros and switched to Z7_COM_UNKNOWN_IMP_*.
// Semantics are identical (QueryInterface + AddRef/Release); this header maps them so the JBinding
// bridge classes compile without per-file rewrites.
// This header MUST be included AFTER "Common/MyCom.h" (26.02).

#include "Common/MyCom.h"

// ⚠️ 26.02's Z7_COM_ADDREF_RELEASE / Z7_COM_QI_BEGIN generate private methods marked Z7_final, but
// the JBinding bridge classes need to: 1) have subclasses override the parent QueryInterface/AddRef/
// Release (to merge multiple interfaces), and 2) call the parent AddRef/Release from subclasses.
// Therefore these macros are redefined as public + overridable (matching 16.02 semantics).
// Note: Z7_COM_UNKNOWN_IMP_* expands to these macros internally, so redefining them takes effect.
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
