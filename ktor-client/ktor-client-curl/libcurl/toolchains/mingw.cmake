# Include the default vcpkg toolchain
include("$ENV{VCPKG_ROOT}/scripts/toolchains/mingw.cmake")

# Custom toolchain configuration
if(DEFINED ENV{TOOLCHAIN_DIR})
    set(CMAKE_C_COMPILER "$ENV{TOOLCHAIN_DIR}/bin/clang.exe" CACHE FILEPATH "" FORCE)
    set(CMAKE_CXX_COMPILER "$ENV{TOOLCHAIN_DIR}/bin/clang++.exe" CACHE FILEPATH "" FORCE)
    set(CMAKE_AR "$ENV{TOOLCHAIN_DIR}/bin/llvm-ar.exe" CACHE FILEPATH "" FORCE)
    set(CMAKE_LINKER "$ENV{TOOLCHAIN_DIR}/bin/ld.lld.exe" CACHE FILEPATH "" FORCE)
endif()
