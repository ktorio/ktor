# Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.

include("$ENV{VCPKG_ROOT}/triplets/x64-linux.cmake")
set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${CMAKE_CURRENT_LIST_DIR}/../toolchains/linux.cmake")
set(VCPKG_FIXUP_ELF_RPATH OFF)