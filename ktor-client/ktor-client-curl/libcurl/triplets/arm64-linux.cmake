include("$ENV{VCPKG_ROOT}/triplets/community/arm64-linux.cmake")
set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${CMAKE_CURRENT_LIST_DIR}/../toolchains/linux.cmake")
# Static libraries don't need rpath fixup, skip patchelf requirement
set(VCPKG_FIXUP_ELF_RPATH OFF)
