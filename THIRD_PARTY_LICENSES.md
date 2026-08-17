# Third-party licenses

This project (7-Zip-JBinding × LZMA SDK 26.02 port) consists of the following parts:

| Component | License | Notes |
|---|---|---|
| **LZMA SDK 26.02** | Public Domain | Igor Pavlov, official 7-Zip C++ SDK. No copyright constraints |
| **7-Zip-JBinding bridge** | LGPL-2.1-or-later | Original `jbinding-cpp` / `java/` code, Borisa Tomic et al. |
| **This port's patches** | LGPL-2.1-or-later | `jbinding_myunknown_compat.h`, `SplitSevenZOutStream.kt`, `JBinding7zArchiveExplorer.kt`, `CPPToJavaSequentialInStream.cpp` patch |
| **7-Zip main program** (if bundled) | LGPL-2.1-or-later + 7-Zip additional terms | See 7-Zip official site |

## LZMA SDK (public domain) — excerpt

> Igor Pavlov, 1999-2025. This code is placed in the public domain.

## 7-Zip-JBinding (LGPL-2.1)

> Copyright (C) 2007-2020 7-Zip-JBinding authors
> This library is free software; you can redistribute it and/or modify it under the
> terms of the GNU Lesser General Public License as published by the Free Software
> Foundation; either version 2.1 of the License, or (at your option) any later version.

## Compliance notes

1. **Dynamic linking**: JBinding is loaded by the app as `lib7-Zip-JBinding.so` dynamically, satisfying
   LGPL's "dynamic linking" requirement — your app body may use any license (e.g. GPL-3.0 or proprietary).
2. **Source availability**: if you distribute this `.so`, you must provide the corresponding complete source
   (i.e. this repository) or a written offer to obtain it.
3. **Preserve copyright headers**: do not strip copyright headers from `jbinding-cpp` and `java/` source files.
4. **7-Zip name**: 7-Zip and LZMA names may describe a compatible implementation but must not imply official endorsement.
