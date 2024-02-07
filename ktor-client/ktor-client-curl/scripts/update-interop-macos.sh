#!/bin/sh

ARCHS="x86_64 arm64"
CURL_VERSION="8.6.0"

platform="macos"

for arch in $ARCHS; do
    case $arch in
    x86_64)
        source_arch="amd64"
        target_arch="X64"
        ;;
    arm64)
        source_arch="arm64"
        target_arch="Arm64"
        ;;
    esac
done

cd static-curl

rm -R "build/${platform}" || true
rm -R "release/curl-${platform}"-* || true

# build libraries
ARCHS="${ARCHS}" CURL_VERSION="${CURL_VERSION}" TLS_LIB=openssl DIR=$(pwd)"/build/${platform}" RELEASE_DIR=$(pwd) sh curl-static-mac.sh

rm -R "../../desktop/interop/${platform}${target_arch}/include" || true
cp -a "release/curl-${platform}-${source_arch}/include" "../../desktop/interop/${platform}${target_arch}/"

rm -R "../../desktop/interop/${platform}${target_arch}/lib" || true
cp -a "release/curl-${platform}-${source_arch}/lib" "../../desktop/interop/${platform}${target_arch}/"
