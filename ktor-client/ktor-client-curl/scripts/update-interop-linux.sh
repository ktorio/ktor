#!/bin/sh

ARCHES="x86_64 aarch64"
CURL_VERSION="8.8.0"

platform="linux"

cd static-curl

rm -R "release/curl-${platform}"-* || true

# build libraries
ARCHES="${ARCHES}" CURL_VERSION="${CURL_VERSION}" TLS_LIB=openssl CONTAINER_IMAGE=debian:11 STATIC_LIBRARY=1 sh curl-static-cross.sh

for source_arch in $ARCHES; do
    case $source_arch in
        x86_64)  target_arch="X64" ;;
        aarch64) target_arch="Arm64" ;;
    esac

    source_dir="release/curl-${platform}-${source_arch}-dev"
    target_dir="../../desktop/interop/${platform}${target_arch}"
    rm -R $source_dir || true && mkdir $source_dir
    tar xf release/curl-${platform}-${source_arch}-dev-*.tar.xz --strip-components 1 -C $source_dir

    rm -R "${target_dir}/include" || true
    cp -a "${source_dir}/include" "${target_dir}/"

    rm -R "${target_dir}/lib" || true && mkdir "${target_dir}/lib"
    cp -a "${source_dir}/lib/"*.a "${target_dir}/lib"
    cp -a "${source_dir}/lib64/"*.a "${target_dir}/lib"
done
