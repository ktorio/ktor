# Custom toolchain using Konan configuration
if(DEFINED ENV{TOOLCHAIN_LLVM_HOME})
    # Clang from LLVM
    set(CMAKE_C_COMPILER "$ENV{TOOLCHAIN_LLVM_HOME}/bin/clang")
    set(CMAKE_CXX_COMPILER "$ENV{TOOLCHAIN_LLVM_HOME}/bin/clang++")
    set(CMAKE_AR "$ENV{TOOLCHAIN_LLVM_HOME}/bin/llvm-ar")
    set(CMAKE_RANLIB ":")

    if(DEFINED ENV{TOOLCHAIN_SYSROOT})
        set(CMAKE_SYSROOT "$ENV{TOOLCHAIN_SYSROOT}")
    endif()

    if(DEFINED ENV{TOOLCHAIN_TRIPLE})
        string(APPEND CMAKE_C_FLAGS " -target $ENV{TOOLCHAIN_TRIPLE}")
        string(APPEND CMAKE_CXX_FLAGS " -target $ENV{TOOLCHAIN_TRIPLE}")
    endif()

    if(DEFINED ENV{TOOLCHAIN_GCC_TOOLCHAIN})
        string(APPEND CMAKE_C_FLAGS " --gcc-toolchain=$ENV{TOOLCHAIN_GCC_TOOLCHAIN}")
        string(APPEND CMAKE_CXX_FLAGS " --gcc-toolchain=$ENV{TOOLCHAIN_GCC_TOOLCHAIN}")
    endif()

    if(DEFINED ENV{TOOLCHAIN_LINKER})
        set(CMAKE_LINKER "$ENV{TOOLCHAIN_LINKER}")
        string(APPEND CMAKE_EXE_LINKER_FLAGS " -fuse-ld=$ENV{TOOLCHAIN_LINKER}")
        string(APPEND CMAKE_SHARED_LINKER_FLAGS " -fuse-ld=$ENV{TOOLCHAIN_LINKER}")
    endif()

    if(DEFINED ENV{TOOLCHAIN_LIBGCC})
        string(APPEND CMAKE_EXE_LINKER_FLAGS " -L$ENV{TOOLCHAIN_LIBGCC}")
        string(APPEND CMAKE_SHARED_LINKER_FLAGS " -L$ENV{TOOLCHAIN_LIBGCC}")
    endif()
endif()

if(CMAKE_CROSSCOMPILING)
    # Set CA paths for curl as they're not inferred automatically when cross-compiling
    # Use standard Linux paths that match native builds
    set(CURL_CA_BUNDLE "/etc/ssl/certs/ca-certificates.crt")
    set(CURL_CA_PATH "/etc/ssl/certs")
endif()

# Include default vcpkg toolchain for VCPKG_ variables
include("$ENV{VCPKG_ROOT}/scripts/toolchains/linux.cmake")
