# Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.

cmake_minimum_required(VERSION 3.20)
project(ktor_io_uring_jni LANGUAGES C)

if(NOT DEFINED JAVA_HOME OR JAVA_HOME STREQUAL "")
    message(FATAL_ERROR "JAVA_HOME must point to a JDK containing JNI headers")
endif()

find_path(JNI_INCLUDE_DIR jni.h PATHS "${JAVA_HOME}/include" NO_DEFAULT_PATH REQUIRED)
find_path(
    JNI_PLATFORM_INCLUDE_DIR
    jni_md.h
    PATHS "${JAVA_HOME}/include/linux" "${JAVA_HOME}/include/darwin"
    NO_DEFAULT_PATH
    REQUIRED
)
find_path(LIBURING_INCLUDE_DIR liburing.h REQUIRED)

set(_library_suffixes ${CMAKE_FIND_LIBRARY_SUFFIXES})
set(CMAKE_FIND_LIBRARY_SUFFIXES ".a")
find_library(LIBURING_LIBRARY uring REQUIRED)
set(CMAKE_FIND_LIBRARY_SUFFIXES ${_library_suffixes})

add_library(ktor_io_uring SHARED ktor_io_uring_jni.c)
target_include_directories(ktor_io_uring PRIVATE ${JNI_INCLUDE_DIR} ${JNI_PLATFORM_INCLUDE_DIR} ${LIBURING_INCLUDE_DIR})
target_link_libraries(ktor_io_uring PRIVATE ${LIBURING_LIBRARY})
set_target_properties(
    ktor_io_uring
    PROPERTIES
    OUTPUT_NAME ktor_io_uring
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
    POSITION_INDEPENDENT_CODE ON
)

install(TARGETS ktor_io_uring LIBRARY DESTINATION lib RUNTIME DESTINATION bin)