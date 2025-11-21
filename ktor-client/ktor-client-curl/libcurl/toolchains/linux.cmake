# Include the default vcpkg Linux toolchain
include("$ENV{VCPKG_ROOT}/scripts/toolchains/linux.cmake")

# Custom toolchain configuration
if(DEFINED ENV{TOOLCHAIN_DIR})
    set(CMAKE_SYSROOT "$ENV{TOOLCHAIN_DIR}/$ENV{TOOLCHAIN_TARGET}/sysroot" CACHE STRING "" FORCE)
    set(CMAKE_C_COMPILER "$ENV{TOOLCHAIN_DIR}/bin/$ENV{TOOLCHAIN_TARGET}-gcc" CACHE FILEPATH "" FORCE)
    set(CMAKE_CXX_COMPILER "$ENV{TOOLCHAIN_DIR}/bin/$ENV{TOOLCHAIN_TARGET}-g++" CACHE FILEPATH "" FORCE)
endif()
