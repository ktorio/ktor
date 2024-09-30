#!/bin/sh

ARCHES="x86_64 arm64"
CURL_VERSION="8.8.0"

platform="macos"

cd static-curl

rm -R "build/${platform}" || true
rm -R "release/curl-${platform}"-* || true

# build libraries
ARCHES="${ARCHES}" CURL_VERSION="${CURL_VERSION}" TLS_LIB=openssl DIR=$(pwd)"/build/${platform}" RELEASE_DIR=$(pwd) ARES_VERSION="1.28.1" sh curl-static-mac.sh

for source_arch in $ARCHES; do
    case $source_arch in
        x86_64) target_arch="X64" ;;
        arm64) target_arch="Arm64" ;;
    esac

    source_dir="release/curl-${platform}-${source_arch}-dev"
    target_dir="../../desktop/interop/${platform}${target_arch}"
    rm -R $source_dir || true && mkdir $source_dir
    tar xf release/curl-${platform}-${source_arch}-dev-*.tar.xz --strip-components 1 -C $source_dir

    rm -R "${target_dir}/include" || true
    cp -a "${source_dir}/include" "${target_dir}/"

    rm -R "${target_dir}/lib" || true && mkdir "${target_dir}/lib"
    cp -a "${source_dir}/lib/"*.a "${target_dir}/lib"
done
