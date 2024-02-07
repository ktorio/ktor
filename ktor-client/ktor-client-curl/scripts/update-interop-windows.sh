#!/bin/sh

ARCHES="x86_64"
CURL_VERSION="8.8.0"

source_platform="windows"
target_platform="mingw"
docker_image="local/llvm-mingw"

# build local llvm mingw image
if [ -z "$(docker images -q $docker_image 2> /dev/null)" ]; then
  docker build -t $docker_image -f docker/llvm-mingw.Dockerfile .
fi

cd static-curl

rm -R "release/curl-${source_platform}"-* || true

# build libraries
ARCHES="${ARCHES}" CURL_VERSION="${CURL_VERSION}" TLS_LIB=openssl CONTAINER_IMAGE=$docker_image sh curl-static-win.sh

for source_arch in $ARCHES; do
    case $source_arch in
        x86_64)  target_arch="X64" ;;
        aarch64) target_arch="Arm64" ;;
    esac

    source_dir="release/curl-${source_platform}-${source_arch}-dev"
    target_dir="../../desktop/interop/${target_platform}${target_arch}"
    rm -R $source_dir || true && mkdir $source_dir
    tar xf release/curl-${source_platform}-${source_arch}-dev-*.tar.xz --strip-components 1 -C $source_dir

    rm -R "${target_dir}/include" || true
    cp -a "${source_dir}/include" "${target_dir}/"

    rm -R "${target_dir}/lib" || true && mkdir "${target_dir}/lib"
    cp -a "${source_dir}/lib/"*.a "${target_dir}/lib"
    cp -a "${source_dir}/lib64/"*.a "${target_dir}/lib"
done
