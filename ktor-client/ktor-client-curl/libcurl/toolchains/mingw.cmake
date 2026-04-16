# Custom toolchain using Konan configuration
if(DEFINED ENV{TOOLCHAIN_LLVM_HOME})
    # Normalize path to avoid backslash escape issues on Windows
    file(TO_CMAKE_PATH "$ENV{TOOLCHAIN_LLVM_HOME}" LLVM_HOME)

    set(CMAKE_C_COMPILER "${LLVM_HOME}/bin/clang.exe")
    set(CMAKE_CXX_COMPILER "${LLVM_HOME}/bin/clang++.exe")
    set(CMAKE_AR "${LLVM_HOME}/bin/llvm-ar.exe")
    set(CMAKE_RANLIB ":")

    if(DEFINED ENV{TOOLCHAIN_SYSROOT})
        file(TO_CMAKE_PATH "$ENV{TOOLCHAIN_SYSROOT}" SYSROOT_PATH)
        set(CMAKE_SYSROOT "${SYSROOT_PATH}")
    endif()

    if(DEFINED ENV{TOOLCHAIN_LINKER})
        file(TO_CMAKE_PATH "$ENV{TOOLCHAIN_LINKER}" LINKER_PATH)
        set(CMAKE_LINKER "${LINKER_PATH}")
        string(APPEND CMAKE_EXE_LINKER_FLAGS " -fuse-ld=${LINKER_PATH}")
        string(APPEND CMAKE_SHARED_LINKER_FLAGS " -fuse-ld=${LINKER_PATH}")
    endif()
endif()

# Include the default vcpkg toolchain
include("$ENV{VCPKG_ROOT}/scripts/toolchains/mingw.cmake")
