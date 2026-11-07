set(VCPKG_POLICY_EMPTY_INCLUDE_FOLDER enabled)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME must point to a JDK containing JNI headers")
endif()

vcpkg_cmake_configure(
    SOURCE_PATH "${CMAKE_CURRENT_LIST_DIR}"
    OPTIONS "-DJAVA_HOME=$ENV{JAVA_HOME}"
)

vcpkg_cmake_install()
vcpkg_copy_pdbs()
vcpkg_install_copyright(FILE_LIST "${CMAKE_CURRENT_LIST_DIR}/../../../../../LICENSE")