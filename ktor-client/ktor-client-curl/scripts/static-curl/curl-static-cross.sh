#!/bin/sh

# To compile locally, install Docker, clone the Git repository, navigate to the repository directory,
# and then execute the following command:
# ARCH=aarch64 CURL_VERSION=8.6.0 TLS_LIB=openssl QUICTLS_VERSION=3.1.5 \
#     ZLIB_VERSION= CONTAINER_IMAGE=debian:latest \
#     sh curl-static-cross.sh
# script will create a container and compile curl.

# or compile or cross-compile in docker, run:
# docker run --network host --rm -v $(pwd):/mnt -w /mnt \
#     --name "build-curl-$(date +%Y%m%d-%H%M)" \
#     -e RELEASE_DIR=/mnt \
#     -e ARCH=all \
#     -e ARCHS="x86_64 aarch64 armv7l i686 riscv64 s390x" \
#     -e ENABLE_DEBUG=0 \
#     -e CURL_VERSION=8.6.0 \
#     -e TLS_LIB=openssl \
#     -e QUICTLS_VERSION=3.1.5 \
#     -e OPENSSL_VERSION="" \
#     -e NGTCP2_VERSION="" \
#     -e NGHTTP3_VERSION="" \
#     -e NGHTTP2_VERSION="" \
#     -e ZLIB_VERSION="" \
#     -e LIBUNISTRING_VERSION=1.1 \
#     -e LIBIDN2_VERSION=2.3.4 \
#     -e LIBPSL_VERSION="" \
#     -e BROTLI_VERSION="" \
#     -e ZSTD_VERSION="" \
#     -e LIBSSH2_VERSION="" \
#     -e CONTAINER_IMAGE=debian:latest \
#     debian:latest sh curl-static-cross.sh
# Supported architectures: x86_64, aarch64, armv7l, i686, riscv64, s390x,
#                          mips64, mips64el, mips, mipsel, powerpc64le, powerpc


init_env() {
    export DIR=${DIR:-/data};
    export PREFIX="${DIR}/curl";
    export RELEASE_DIR=${RELEASE_DIR:-/mnt};
    export ARCH_HOST=$(uname -m)

    case "${ENABLE_DEBUG}" in
        true|1|yes|on|y|Y)
            ENABLE_DEBUG="--enable-debug" ;;
        *)
            ENABLE_DEBUG="" ;;
    esac

    echo "Source directory: ${DIR}"
    echo "Prefix directory: ${PREFIX}"
    echo "Release directory: ${RELEASE_DIR}"
    echo "Host Architecture: ${ARCH_HOST}"
    echo "Architecture: ${ARCH}"
    echo "Architecture list: ${ARCHS}"
    echo "cURL version: ${CURL_VERSION}"
    echo "TLS Library: ${TLS_LIB}"
    echo "QuicTLS version: ${QUICTLS_VERSION}"
    echo "OpenSSL version: ${OPENSSL_VERSION}"
    echo "ngtcp2 version: ${NGTCP2_VERSION}"
    echo "nghttp3 version: ${NGHTTP3_VERSION}"
    echo "nghttp2 version: ${NGHTTP2_VERSION}"
    echo "zlib version: ${ZLIB_VERSION}"
    echo "libunistring version: ${LIBUNISTRING_VERSION}"
    echo "libidn2 version: ${LIBIDN2_VERSION}"
    echo "libpsl version: ${LIBPSL_VERSION}"
    echo "brotli version: ${BROTLI_VERSION}"
    echo "zstd version: ${ZSTD_VERSION}"
    echo "libssh2 version: ${LIBSSH2_VERSION}"
    echo "c-ares version: ${ARES_VERSION}"

    export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig";

    . /etc/os-release;  # get the ID variable
    mkdir -p "${RELEASE_DIR}/release/"
}

install_packages_alpine() {
    apk update;
    apk upgrade;
    apk add \
        build-base clang automake cmake autoconf libtool binutils linux-headers \
        curl wget git jq xz grep sed groff gnupg perl python3 \
        ca-certificates ca-certificates-bundle \
        cunit-dev \
        zlib-static zlib-dev \
        libunistring-static libunistring-dev \
        libidn2-static libidn2-dev \
        libpsl-static libpsl-dev \
        zstd-static zstd-dev;
}

install_packages_debian() {
    export DEBIAN_FRONTEND=noninteractive;
    apt-get update -y > /dev/null;
    apt-get install -y apt-utils > /dev/null;
    apt-get upgrade -y > /dev/null;
    apt-get install -y automake cmake autoconf libtool binutils pkg-config \
        curl wget git jq xz-utils grep sed groff gnupg libcunit1-dev libgpg-error-dev;
    available_clang=$(apt-cache search clang | grep -E '^clang-[0-9]+ ' | awk '{print $1}' | sort -V | tail -n 1)
    if [ -n "${available_clang}" ]; then
        apt-get install -y "${available_clang}";
        CLANG_VERSION=$(echo "${available_clang}" | cut -d- -f2);
    else
        apt-get install -y clang;
    fi
}

install_packages() {
    case "${ID}" in
        debian|ubuntu|devuan)
            install_packages_debian ;;
        alpine)
            install_packages_alpine ;;
        *)
            echo "Unsupported distribution: ${ID}";
            exit 1 ;;
    esac
}

install_cross_compile() {
    echo "Installing cross compile toolchain, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    change_dir;
    local url

    if [ ! -f "github-qbt-musl-cross-make.json" ]; then
        # GitHub API has a limit of 60 requests per hour, cache the results.
        curl --retry 5 --retry-max-time 120 -s \
            "https://api.github.com/repos/userdocs/qbt-musl-cross-make/releases" -o "github-qbt-musl-cross-make.json"
    fi

    browser_download_url=$(jq -r '.' "github-qbt-musl-cross-make.json" \
        | grep browser_download_url \
        | grep -i "${ARCH}-" \
        | head -1)
    url=$(printf "%s" "${browser_download_url}" | awk '{print $2}' | sed 's/"//g')
    download_and_extract "${url}"

    ln -s "${DIR}/${SOURCE_DIR}/${SOURCE_DIR}" "/${SOURCE_DIR}"
    cd "/${SOURCE_DIR}/lib/"
    mv libatomic.so libatomic.so.bak
    ln -s libatomic.a libatomic.so

    export CC=${DIR}/${SOURCE_DIR}/bin/${SOURCE_DIR}-cc \
           CXX=${DIR}/${SOURCE_DIR}/bin/${SOURCE_DIR}-c++ \
           STRIP=${DIR}/${SOURCE_DIR}/bin/${SOURCE_DIR}-strip \
           PATH=${DIR}/${SOURCE_DIR}/bin:$PATH
}

install_cross_compile_debian() {
    echo "Installing cross compile toolchain, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local arch_compiler c_lib arch_name
    arch_compiler=${ARCH}
    c_lib=gnu
    arch_name=${ARCH}

    case "${ARCH}" in
        armv7l)
            arch_compiler=arm
            c_lib=gnueabihf
            arch_name=arm
            ;;
        mips64|mips64el)
            c_lib=gnuabi64
            ;;
        x86_64)
            arch_name=x86-64
            ;;
    esac

    apt install -y "gcc-${arch_name}-linux-${c_lib}" \
                   "g++-${arch_name}-linux-${c_lib}" \
                   "binutils-${arch_name}-linux-${c_lib}";

    if [ -z "${CLANG_VERSION}" ]; then
        export CC="clang -target ${arch_compiler}-linux-${c_lib}" \
               CXX="clang++ -target ${arch_compiler}-linux-${c_lib}"
    else
        export CC="clang-${CLANG_VERSION} -target ${arch_compiler}-linux-${c_lib}" \
               CXX="clang++-${CLANG_VERSION} -target ${arch_compiler}-linux-${c_lib}"
    fi

    export LD="/usr/bin/${arch_compiler}-linux-${c_lib}-ld" \
           STRIP="/usr/bin/${arch_compiler}-linux-${c_lib}-strip" \
           CFLAGS="-O3" \
           LDFLAGS="--ld-path=/usr/bin/${arch_compiler}-linux-${c_lib}-ld ${LDFLAGS}";
}

install_qemu() {
    local qemu_arch=$1
    echo "Installing QEMU ${qemu_arch}, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"

    case "${ID}" in
        debian|ubuntu|devuan)
            apt-get install -y qemu-user-static > /dev/null ;;
        alpine)
            apk add "qemu-${qemu_arch}" ;;
    esac
}

arch_variants() {
    echo "Setting up the ARCH and OpenSSL arch, Arch: ${ARCH}"
    local qemu_arch

    [ -z "${ARCH}" ] && ARCH="${ARCH_HOST}"
    case "${ARCH}" in
        x86_64)     arch="amd64" ;;
        aarch64)    arch="arm64" ;;
        armv7l)     arch="armv7" ;;
        i686)       arch="i686" ;;
        *)          arch="${ARCH}" ;;
    esac

    EC_NISTP_64_GCC_128=""
    OPENSSL_ARCH=""

    case "${ARCH}" in
        x86_64)         qemu_arch="x86_64"
                        EC_NISTP_64_GCC_128="enable-ec_nistp_64_gcc_128"
                        if [ "${ID}" = "alpine" ] && [ "${ARCH}" != "${ARCH_HOST}" ]; then
                            OPENSSL_ARCH="linux-x86_64";
                        else
                            OPENSSL_ARCH="linux-x86_64-clang";
                        fi ;;
        aarch64)        qemu_arch="aarch64"
                        EC_NISTP_64_GCC_128="enable-ec_nistp_64_gcc_128"
                        OPENSSL_ARCH="linux-aarch64" ;;
        armv7l)         qemu_arch="arm"
                        OPENSSL_ARCH="linux-armv4" ;;
        i686)           qemu_arch="i386"
                        OPENSSL_ARCH="linux-x86" ;;
        riscv64)        qemu_arch="riscv64"
                        EC_NISTP_64_GCC_128="enable-ec_nistp_64_gcc_128"
                        OPENSSL_ARCH="linux64-riscv64" ;;
        s390x)          qemu_arch="s390x"
                        OPENSSL_ARCH="linux64-s390x" ;;
        mips64)         qemu_arch="mips64"
                        OPENSSL_ARCH="linux64-mips64" ;;
        mips64el)       qemu_arch="mips64el"
                        OPENSSL_ARCH="linux64-mips64" ;;
        mips)           qemu_arch="mips"
                        OPENSSL_ARCH="linux-mips32" ;;
        mipsel)         qemu_arch="mipsel"
                        OPENSSL_ARCH="linux-mips32" ;;
        powerpc64le)    qemu_arch="ppc64le"
                        OPENSSL_ARCH="linux-ppc64le" ;;
        powerpc)        qemu_arch="ppc"
                        OPENSSL_ARCH="linux-ppc" ;;
    esac

    unset LD STRIP LDFLAGS
    TARGET="${ARCH}-pc-linux-gnu"
    export LDFLAGS="-L${PREFIX}/lib -L${PREFIX}/lib64";
    if [ "${ARCH}" != "${ARCH_HOST}" ]; then
        # If the architecture is not the same as the host, need to cross compile
        install_qemu "${qemu_arch}";

        if [ "${ARCH}" = "mips" ]  || [ "${ARCH}" = "i686" ] || [ "${ID}" = "alpine" ]; then
            # Cross-compilation failed with atomic using clang in MIPS and i686.
            # Alpine does not have a GCC cross-compile toolchain.
            # Therefore, musl-cross-make is used for compilation.
            install_cross_compile;
        else
            # Uses Clang for default cross-compilation
            install_cross_compile_debian;
        fi
    else
        # If the architecture is the same as the host, no need to cross compile
        if [ -z "${CLANG_VERSION}" ]; then
            export CC=clang CXX=clang++
        else
            export CC="clang-${CLANG_VERSION}" CXX="clang++-${CLANG_VERSION}"
        fi
    fi
}

_get_github() {
    local repo release_file auth_header status_code size_of
    repo=$1
    release_file="github-${repo#*/}.json"

    # GitHub API has a limit of 60 requests per hour, cache the results.
    echo "Downloading ${repo} releases from GitHub"
    echo "URL: https://api.github.com/repos/${repo}/releases"

    # get token from github settings
    auth_header=""
    set +o xtrace
    if [ -n "${TOKEN_READ}" ]; then
        auth_header="token ${TOKEN_READ}"
    fi

    status_code=$(curl --retry 5 --retry-max-time 120 "https://api.github.com/repos/${repo}/releases" \
        -w "%{http_code}" \
        -o "${release_file}" \
        -H "Authorization: ${auth_header}" \
        -s -L --compressed)

    set -o xtrace
    size_of=$(stat -c "%s" "${release_file}")
    if [ "${size_of}" -lt 200 ] || [ "${status_code}" -ne 200 ]; then
        echo "The release of ${repo} is empty, download tags instead."
        set +o xtrace
        status_code=$(curl --retry 5 --retry-max-time 120 "https://api.github.com/repos/${repo}/tags" \
            -w "%{http_code}" \
            -o "${release_file}" \
            -H "Authorization: ${auth_header}" \
            -s -L --compressed)
        set -o xtrace
    fi
    auth_header=""

    if [ "${status_code}" -ne 200 ]; then
        echo "ERROR. Failed to download ${repo} releases from GitHub, status code: ${status_code}"
        cat "${release_file}"
        exit 1
    fi
}

_get_tag() {
    # Function to get the latest tag based on given criteria
    jq -c -r "[.[] | select(${2})][0]" "${1}" > /tmp/tmp_release.json;
}

_get_latest_tag() {
    local release_file release_json
    release_file=$1

    # Get the latest tag that is not a draft and not a pre-release
    _get_tag "${release_file}" "(.prerelease != true) and (.draft != true)"

    release_json=$(cat /tmp/tmp_release.json)

    # If no tag found, get the latest tag that is not a draft
    if [ "${release_json}" = "null" ] || [ -z "${release_json}" ]; then
        _get_tag "${release_file}" ".draft != true"
        release_json=$(cat /tmp/tmp_release.json)
    fi

    # If still no tag found, get the first tag
    if [ "${release_json}" = "null" ] || [ -z "${release_json}" ]; then
        _get_tag "${release_file}" "."
    fi
}

url_from_github() {
    local browser_download_urls browser_download_url url repo version tag_name release_file
    repo=$1
    version=$2
    release_file="github-${repo#*/}.json"

    if [ ! -f "${release_file}" ]; then
        _get_github "${repo}"
    fi

    if [ -z "${version}" ]; then
        _get_latest_tag "${release_file}"
    else
        jq -c -r "map(select(.tag_name == \"${version}\")
                  // select(.tag_name | startswith(\"${version}\"))
                  // select(.tag_name | endswith(\"${version}\"))
                  // select(.tag_name | contains(\"${version}\"))
                  // select(.name == \"${version}\")
                  // select(.name | startswith(\"${version}\"))
                  // select(.name | endswith(\"${version}\"))
                  // select(.name | contains(\"${version}\")))[0]" \
            "${release_file}" > /tmp/tmp_release.json
    fi

    browser_download_urls=$(jq -r '.assets[]' /tmp/tmp_release.json | grep browser_download_url || true)

    if [ -n "${browser_download_urls}" ]; then
        suffixes="tar.xz tar.gz tar.bz2 tgz"
        for suffix in ${suffixes}; do
            browser_download_url=$(printf "%s" "${browser_download_urls}" | grep "${suffix}\"" || true)
            [ -n "$browser_download_url" ] && break
        done

        url=$(printf "%s" "${browser_download_url}" | head -1 | awk '{print $2}' | sed 's/"//g' || true)
    fi

    if [ -z "${url}" ]; then
        tag_name=$(jq -r '.tag_name // .name' /tmp/tmp_release.json | head -1)
        # get from "Source Code" of releases
        if [ "${tag_name}" = "null" ] || [ "${tag_name}" = "" ]; then
            echo "ERROR. Failed to get the ${version} from ${repo} of GitHub"
            exit 1
        fi
        url="https://github.com/${repo}/archive/refs/tags/${tag_name}.tar.gz"
    fi

    rm -f /tmp/tmp_release.json;
    URL="${url}"
}

download_and_extract() {
    echo "Downloading $1"
    local url

    url="$1"
    FILENAME=${url##*/}

    if [ ! -f "${FILENAME}" ]; then
        wget -c --no-verbose --content-disposition "${url}";

        FILENAME=$(curl --retry 5 --retry-max-time 120 -sIL "${url}" | \
            sed -n -e 's/^Content-Disposition:.*filename=//ip' | \
            tail -1 | sed 's/\r//g; s/\n//g; s/\"//g' | grep -oP '[\x20-\x7E]+' || true)
        if [ "${FILENAME}" = "" ]; then
            FILENAME=${url##*/}
        fi

        echo "Downloaded ${FILENAME}"
    else
        echo "Already downloaded ${FILENAME}"
    fi

    # If the file is a tarball, extract it
    if expr "${FILENAME}" : '.*\.\(tar\.xz\|tar\.gz\|tar\.bz2\|tgz\)$' > /dev/null; then
        # SOURCE_DIR=$(echo "${FILENAME}" | sed -E "s/\.tar\.(xz|bz2|gz)//g" | sed 's/\.tgz//g')
        SOURCE_DIR=$(tar -tf "${FILENAME}" | head -n 1 | cut -d'/' -f1)
        [ -d "${SOURCE_DIR}" ] && rm -rf "${SOURCE_DIR}"
        tar -axf "${FILENAME}"
        cd "${SOURCE_DIR}"
    fi
}

change_dir() {
    mkdir -p "${DIR}";
    cd "${DIR}";
}

compile_zlib() {
    echo "Compiling zlib, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github madler/zlib "${ZLIB_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    ./configure --prefix="${PREFIX}" --static;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-zlib" ]; then cp -p LICENSE "${RELEASE_DIR}/release/LICENSE-zlib" || true; fi
}

compile_libunistring() {
    echo "Compiling libunistring, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    [ -z "${LIBUNISTRING_VERSION}" ] && LIBUNISTRING_VERSION="latest"
    url="https://mirrors.kernel.org/gnu/libunistring/libunistring-${LIBUNISTRING_VERSION}.tar.xz"
    download_and_extract "${url}"

    ./configure --host "${TARGET}" --prefix="${PREFIX}" --disable-rpath --disable-shared;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-libunistring" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-libunistring" || true; fi
}

compile_libidn2() {
    echo "Compiling libidn2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    [ -z "${LIBIDN2_VERSION}" ] && LIBIDN2_VERSION="latest"
    url="https://mirrors.kernel.org/gnu/libidn/libidn2-${LIBIDN2_VERSION}.tar.gz"
    download_and_extract "${url}"

    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
    ./configure \
        --host "${TARGET}" \
        --with-libunistring-prefix="${PREFIX}" \
        --prefix="${PREFIX}" \
        --disable-shared;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-libidn2" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-libidn2" || true; fi
}

compile_libpsl() {
    echo "Compiling libpsl, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github rockdaboot/libpsl "${LIBPSL_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
      ./configure --host="${TARGET}" --prefix="${PREFIX}" \
        --enable-static --enable-shared=no --enable-builtin --disable-runtime;

    make -j "$(nproc)" LDFLAGS="-static -all-static -Wl,-s ${LDFLAGS}";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-libpsl" ]; then cp -p LICENSE "${RELEASE_DIR}/release/LICENSE-libpsl" || true; fi
}

compile_ares() {
    echo "Compiling c-ares, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github c-ares/c-ares "${ARES_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --disable-shared;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-c-ares" ]; then cp -p LICENSE.md "${RELEASE_DIR}/release/LICENSE-c-ares" || true; fi
}

compile_tls() {
    echo "Compiling ${TLS_LIB}, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    if [ "${TLS_LIB}" = "openssl" ]; then
        url_from_github openssl/openssl "${OPENSSL_VERSION}"
    else
        url_from_github quictls/openssl "${QUICTLS_VERSION}"
    fi

    url="${URL}"
    download_and_extract "${url}"

    ./Configure \
        ${OPENSSL_ARCH} \
        -fPIC \
        --prefix="${PREFIX}" \
        threads no-shared \
        enable-ktls \
        ${EC_NISTP_64_GCC_128} \
        enable-tls1_3 \
        enable-ssl3 enable-ssl3-method \
        enable-des enable-rc4 \
        enable-weak-ssl-ciphers;

    make -j "$(nproc)";
    make install_sw;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-openssl" ]; then cp -p LICENSE.txt "${RELEASE_DIR}/release/LICENSE-openssl" || true; fi
}

compile_libssh2() {
    echo "Compiling libssh2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"

    local url
    change_dir;

    url_from_github libssh2/libssh2 "${LIBSSH2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -fi
    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-shared=no \
            --with-crypto=openssl --with-libssl-prefix="${PREFIX}" \
            --disable-examples-build;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-libssh2" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-libssh2" || true; fi
}

compile_nghttp2() {
    echo "Compiling nghttp2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github nghttp2/nghttp2 "${NGHTTP2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-http3 \
            --enable-lib-only --enable-shared=no;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-nghttp2" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-nghttp2" || true; fi
}

compile_ngtcp2() {
    if [ "${TLS_LIB}" = "openssl" ]; then
        return
    fi
    echo "Compiling ngtcp2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"

    local url
    change_dir;

    url_from_github ngtcp2/ngtcp2 "${NGTCP2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --with-openssl="${PREFIX}" \
            --with-libnghttp3="${PREFIX}" --enable-lib-only --enable-shared=no;

    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-ngtcp2" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-ngtcp2" || true; fi
}

compile_nghttp3() {
    echo "Compiling nghttp3, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github ngtcp2/nghttp3 "${NGHTTP3_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-shared=no --enable-lib-only;
    make -j "$(nproc)";
    make install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-nghttp3" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-nghttp3" || true; fi
}

compile_brotli() {
    echo "Compiling brotli, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github google/brotli "${BROTLI_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    mkdir -p out
    cd out/

    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="${PREFIX}" -DBUILD_SHARED_LIBS=OFF ..;
    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        cmake --build . --config Release --target install;

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-brotli" ]; then cp -p ../LICENSE "${RELEASE_DIR}/release/LICENSE-brotli" || true; fi
    cd "${PREFIX}/lib/"
    if [ -f libbrotlidec-static.a ] && [ ! -f libbrotlidec.a ]; then ln -f libbrotlidec-static.a libbrotlidec.a; fi
    if [ -f libbrotlienc-static.a ] && [ ! -f libbrotlienc.a ]; then ln -f libbrotlienc-static.a libbrotlienc.a; fi
    if [ -f libbrotlicommon-static.a ] && [ ! -f libbrotlicommon.a ]; then ln -f libbrotlicommon-static.a libbrotlicommon.a; fi
}

compile_zstd() {
    echo "Compiling zstd, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github facebook/zstd "${ZSTD_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
        make -j "$(nproc)" PREFIX="${PREFIX}";
    make install;

    if [ ! -f "${PREFIX}/lib/libzstd.a" ]; then cp -f lib/libzstd.a "${PREFIX}/lib/libzstd.a"; fi
    if [ ! -f "${RELEASE_DIR}/release/LICENSE-zstd" ]; then cp -p LICENSE "${RELEASE_DIR}/release/LICENSE-zstd" || true; fi
}

curl_config() {
    echo "Configuring curl, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local with_openssl_quic

    # --with-openssl-quic and --with-ngtcp2 are mutually exclusive
    with_openssl_quic=""
    if [ "${TLS_LIB}" = "openssl" ]; then
        with_openssl_quic="--with-openssl-quic"
    else
        with_openssl_quic="--with-ngtcp2"
    fi

    if [ ! -f configure ]; then
        autoreconf -fi;
    fi

    PKG_CONFIG="pkg-config --static" \
        ./configure \
            --host="${TARGET}" \
            --prefix="${PREFIX}" \
            --enable-static --disable-shared \
            --with-openssl "${with_openssl_quic}" --with-brotli --with-zstd \
            --with-nghttp2 --with-nghttp3 \
            --with-libidn2 --with-libssh2 \
            --enable-hsts --enable-mime --enable-cookies \
            --enable-http-auth --enable-manual \
            --enable-proxy --enable-file --enable-http \
            --enable-ftp --enable-telnet --enable-tftp \
            --enable-pop3 --enable-imap --enable-smtp \
            --enable-gopher --enable-mqtt \
            --enable-doh --enable-dateparse --enable-verbose \
            --enable-alt-svc --enable-websockets \
            --enable-ipv6 --enable-unix-sockets --enable-socketpair \
            --enable-headers-api --enable-versioned-symbols \
            --enable-threaded-resolver --enable-optimize --enable-pthreads \
            --enable-warnings --enable-werror \
            --enable-curldebug --enable-dict --enable-netrc \
            --enable-bearer-auth --enable-tls-srp --enable-dnsshuffle \
            --enable-get-easy-options --enable-progress-meter \
            --with-ca-bundle=/etc/ssl/certs/ca-certificates.crt \
            --with-ca-path=/etc/ssl/certs \
            --with-ca-fallback --enable-ares \
            --disable-ldap --disable-ldaps "${ENABLE_DEBUG}";
}

compile_curl() {
    echo "Compiling curl, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    if [ "${CURL_VERSION}" = "dev" ]; then
        if [ ! -d "curl-dev" ]; then
            git clone --depth 1 https://github.com/curl/curl.git curl-dev;
        fi
        cd curl-dev;
        make clean || true;
    else
        url_from_github curl/curl "${CURL_VERSION}";
        url="${URL}";
        download_and_extract "${url}";
        if [ ! -f src/.checksrc ]; then echo "enable STDERR" > src/.checksrc; fi
        [ -z "${CURL_VERSION}" ] && CURL_VERSION=$(echo "${SOURCE_DIR}" | cut -d'-' -f 2);
    fi

    curl_config;
    if [ "${ARCH}" = "armv7l" ] || [ "${ARCH}" = "mipsel" ] || [ "${ARCH}" = "mips" ] \
        || [ "${ARCH}" = "powerpc" ] || [ "${ARCH}" = "i686" ]; then
        # add -Wno-cast-align to avoid error alignment from 4 to 8
        make -j "$(nproc)" LDFLAGS="-static -all-static -Wl,-s ${LDFLAGS}" CFLAGS="-Wno-cast-align ${CFLAGS}";
    else
        make -j "$(nproc)" LDFLAGS="-static -all-static -Wl,-s ${LDFLAGS}";
    fi

    if [ ! -f "${RELEASE_DIR}/release/LICENSE-curl" ]; then cp -p COPYING "${RELEASE_DIR}/release/LICENSE-curl" || true; fi
    install_curl;
}

install_curl() {
    mkdir -p "${RELEASE_DIR}/release/"

    ls -l src/curl
    cp -pf src/curl "${RELEASE_DIR}/release/curl-linux-${arch}"

    if [ ! -f "${RELEASE_DIR}/release/version.txt" ]; then
        echo "${CURL_VERSION}" > "${RELEASE_DIR}/release/version.txt"
    fi
    if [ ! -f "${RELEASE_DIR}/release/version-info.txt" ]; then
        src/curl -V >> "${RELEASE_DIR}/release/version-info.txt"
    fi
}

_arch_match() {
    local arch_search="$1"
    local arch_array="$2"

    for element in ${arch_array}; do
        if [ "${element}" = "${arch_search}" ]; then
            return 0    # in the array
        fi
    done

    return 1            # not in the array
}

_arch_valid() {
    local  arch_x86_64="x86_64 aarch64 armv7l riscv64 s390x mips64 mips64el powerpc64le mipsel i686 mips powerpc"
    local arch_aarch64="x86_64 aarch64 armv7l riscv64 s390x mips64 mips64el powerpc64le mipsel"

    if [ "${ARCH_HOST}" = "x86_64" ]; then
        result=$(_arch_match "${ARCH}" "${arch_x86_64}")
    elif [ "${ARCH_HOST}" = "aarch64" ] && [ "${ID}" = "debian" ]; then
        result=$(_arch_match "${ARCH}" "${arch_aarch64}")
    else
        result=1
    fi

    return ${result}
}

_build_in_docker() {
    echo "Not running in docker, starting a docker container to build cURL."
    local container_image

    cd "$(dirname "$0")";
    base_name=$(basename "$0")
    current_time=$(date "+%Y%m%d-%H%M")
    container_image=${CONTAINER_IMAGE:-debian:latest}  # or alpine:latest

    [ -z "${ARCH}" ] && ARCH=$(uname -m)
    container_name="build-curl-${ARCH}-${current_time}"
    RELEASE_DIR=${RELEASE_DIR:-/mnt}

    # Run in docker,
    #   delete the container after running,
    #   mount the current directory into the container,
    #   pass all the environment variables to the container,
    #   log the output to a file.
    docker run --rm \
        --name "${container_name}" \
        --network host \
        -v "$(pwd):${RELEASE_DIR}" -w "${RELEASE_DIR}" \
        -e RELEASE_DIR="${RELEASE_DIR}" \
        -e ARCH="${ARCH}" \
        -e ARCHS="${ARCHS}" \
        -e ENABLE_DEBUG="${ENABLE_DEBUG}" \
        -e CURL_VERSION="${CURL_VERSION}" \
        -e TLS_LIB="${TLS_LIB}" \
        -e QUICTLS_VERSION="${QUICTLS_VERSION}" \
        -e OPENSSL_VERSION="${OPENSSL_VERSION}" \
        -e NGTCP2_VERSION="${NGTCP2_VERSION}" \
        -e NGHTTP3_VERSION="${NGHTTP3_VERSION}" \
        -e NGHTTP2_VERSION="${NGHTTP2_VERSION}" \
        -e ZLIB_VERSION="${ZLIB_VERSION}" \
        -e ZSTD_VERSION="${ZSTD_VERSION}" \
        -e BROTLI_VERSION="${BROTLI_VERSION}" \
        -e LIBSSH2_VERSION="${LIBSSH2_VERSION}" \
        -e LIBUNISTRING_VERSION="${LIBUNISTRING_VERSION}" \
        -e LIBIDN2_VERSION="${LIBIDN2_VERSION}" \
        "${container_image}" sh "${RELEASE_DIR}/${base_name}" 2>&1 | tee -a "${container_name}.log"

    # Exit script after docker finishes
    exit;
}

compile() {
    echo "Compiling for ${ARCH}"
    arch_variants;

    compile_tls;
    compile_zlib;
    compile_zstd;
    compile_libunistring;
    compile_libidn2;
    compile_libpsl;
    compile_ares;
    compile_libssh2;
    compile_nghttp3;
    compile_ngtcp2;
    compile_nghttp2;
    compile_brotli;
    compile_curl;
}

main() {
    local base_name current_time container_name arch_temp

    if [ "${ARCH}" = "all" ] && [ "${ARCHS}" = "" ]; then
        echo "Please set the ARCHS variable."
        exit 1;
    fi

    # If not in docker, run the script in docker and exit
    if [ ! -f /.dockerenv ]; then
        _build_in_docker;
    fi

    init_env;                    # Initialize the build env
    install_packages;            # Install dependencies
    set -o errexit -o xtrace;

    # if ${ARCH} = "all", then compile all the ARCHS
    if [ "${ARCH}" = "all" ]; then
        echo "Compiling for all ARCHs: ${ARCHS}"

        for arch_temp in ${ARCHS}; do
            # Set the ARCH, PREFIX and PKG_CONFIG_PATH env variables
            export ARCH=${arch_temp}
            export PREFIX="${DIR}/curl-${ARCH}"
            export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig"

            echo "Prefix directory: ${PREFIX}"

            if _arch_valid; then
                compile;
            else
                echo "Unsupported architecture ${ARCH} in ${ARCH_HOST}";
            fi
        done
    else
        # else compile for the host ARCH
        compile;
    fi
}

# If the first argument is not "--source-only" then run the script,
# otherwise just provide the functions
if [ "$1" != "--source-only" ]; then
    main "$@";
fi
