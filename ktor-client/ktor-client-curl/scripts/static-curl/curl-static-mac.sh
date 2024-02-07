#!/bin/bash

# To compile locally, clone the Git repository, navigate to the repository directory,
# and then execute the following command:
# ARCHES="x86_64 arm64" CURL_VERSION=8.6.0 TLS_LIB=openssl QUICTLS_VERSION=3.1.5 bash curl-static-mac.sh


shopt -s expand_aliases;
alias grep=rg;
alias sed=gsed;
alias awk=gawk;
alias stat=gstat;
alias make=gmake;

init_env() {
    local number
    export DIR="${DIR:-${HOME}/build}"
    export RELEASE_DIR=${RELEASE_DIR:-${HOME}};
    number=$(sysctl -n hw.ncpu 2>/dev/null)
    export CPU_CORES=${number:-1}

    case "${ENABLE_DEBUG}" in
        true|1|yes|on|y|Y)
            ENABLE_DEBUG="--enable-debug" ;;
        *)
            ENABLE_DEBUG="" ;;
    esac

    echo "Source directory: ${DIR}"
    echo "Release directory: ${RELEASE_DIR}"
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

    export LDFLAGS="-framework CoreFoundation -framework SystemConfiguration"
    export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"

    mkdir -p "${DIR}"
}

install_packages() {
    brew install automake autoconf libtool binutils pkg-config coreutils cmake make llvm \
         curl wget git jq xz ripgrep gnu-sed gawk groff gnupg pcre2 cunit ca-certificates;
}

_clang_path() {
    # find the path of clang
    clang_path=$(which /usr/local/opt/llvm/bin/clang || which /opt/homebrew/opt/llvm/bin/clang \
        || which /Library/Developer/CommandLineTools/usr/bin/clang || which clang || true)
    clang_pp_path=$(which /usr/local/opt/llvm/bin/clang++ || which /opt/homebrew/opt/llvm/bin/clang++ \
        || which /Library/Developer/CommandLineTools/usr/bin/clang++ || which clang++ || true)

    if [ -z "${clang_path}" ] || [ -z "${clang_pp_path}" ]; then
        echo "clang not found"
        exit 1
    fi
}

arch_variants() {
    echo "Setting up the ARCH and OpenSSL arch"
    _clang_path;
    [ -z "${ARCH}" ] && ARCH="$(uname -m)"
    case "${ARCH}" in
        x86_64)   ARCHFLAGS="-arch x86_64"
                  OPENSSL_ARCH="darwin64-x86_64"
                  TARGET="x86_64-apple-darwin"
                  export CC="${clang_path} -target x86_64-apple-macos11"
                  export CXX="${clang_pp_path} -target x86_64-apple-macos11"
                  ;;
        arm64)    ARCHFLAGS="-arch arm64"
                  OPENSSL_ARCH="darwin64-arm64"
                  TARGET="aarch64-apple-darwin"
                  export CC="${clang_path} -target arm64-apple-macos11"
                  export CXX="${clang_pp_path} -target arm64-apple-macos11"
                  ;;
    esac
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

        FILENAME=$(curl --retry 5 --retry-max-time 120 -sIL "${url}" | sed -n -e 's/^Content-Disposition:.*filename=//ip' | \
            tail -1 | sed 's/\r//g; s/\n//g; s/\"//g' | grep -oP '[\x20-\x7E]+' || true)
        if [ "${FILENAME}" = "" ]; then
            FILENAME=${url##*/}
        fi

        echo "Downloaded ${FILENAME}"
    else
        echo "Already downloaded ${FILENAME}"
    fi

    # If the file is a tarball, extract it
    if echo "${FILENAME}" | grep -qP '.*\.(tar\.xz|tar\.gz|tar\.bz2|tgz)$'; then
        # SOURCE_DIR=$(echo "${FILENAME}" | sed -E "s/\.tar\.(xz|bz2|gz)//g" | sed 's/\.tgz//g')
        SOURCE_DIR=$(tar -tf "${FILENAME}" | head -n 1 | cut -d'/' -f1)
        [ -d "${SOURCE_DIR}" ] && rm -rf "${SOURCE_DIR}"
        tar -xf "${FILENAME}"
        cd "${SOURCE_DIR}"
    fi
}

change_dir() {
    mkdir -p "${DIR}";
    cd "${DIR}";
}

_copy_license() {
    # $1: original file name; $2: target file name
    mkdir -p "${PREFIX}/licenses/";
    cp -p "${1}" "${PREFIX}/licenses/${2}";
}

compile_zlib() {
    echo "Compiling zlib, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github madler/zlib "${ZLIB_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    ./configure --prefix="${PREFIX}" --static;
    make -j "${CPU_CORES}";
    make install;

    _copy_license LICENSE zlib;
}

compile_libunistring() {
    echo "Compiling libunistring, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    [ -z "${LIBUNISTRING_VERSION}" ] && LIBUNISTRING_VERSION="latest"
    url="https://mirrors.kernel.org/gnu/libunistring/libunistring-${LIBUNISTRING_VERSION}.tar.xz"
    download_and_extract "${url}"

    ./configure --host="${TARGET}" --prefix="${PREFIX}" --disable-rpath --disable-shared;
    make -j "${CPU_CORES}";
    make install;

    _copy_license COPYING libunistring;
}

compile_libidn2() {
    echo "Compiling libidn2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    [ -z "${LIBIDN2_VERSION}" ] && LIBIDN2_VERSION="latest"
    url="https://mirrors.kernel.org/gnu/libidn/libidn2-${LIBIDN2_VERSION}.tar.gz"
    download_and_extract "${url}"

    PKG_CONFIG="pkg-config --static" \
    ./configure \
        --host="${TARGET}" \
        --with-libunistring-prefix="${PREFIX}" \
        --prefix="${PREFIX}" \
        --disable-shared;
    make -j "${CPU_CORES}";
    make install;

    _copy_license COPYING libidn2;
}

compile_libpsl() {
    echo "Compiling libpsl, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github rockdaboot/libpsl "${LIBPSL_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    PKG_CONFIG="pkg-config --static --with-path=${PREFIX}/lib/pkgconfig:${PREFIX}/lib64/pkgconfig" \
      LDFLAGS="-L${PREFIX}/lib -L${PREFIX}/lib64 ${LDFLAGS}" \
      ./configure --host="${TARGET}" --prefix="${PREFIX}" \
        --enable-static --enable-shared=no --enable-builtin --disable-runtime;
    make -j "${CPU_CORES}";
    make install;

    _copy_license LICENSE libpsl;
}

compile_ares() {
    echo "Compiling c-ares, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github c-ares/c-ares "${ARES_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --disable-shared;
    CFLAGS="-I${PREFIX}/include -I${PREFIX}/include/brotli" make -j "$(nproc)";
    make install;

    _copy_license LICENSE.md c-ares;
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
        enable-ec_nistp_64_gcc_128 \
        enable-tls1_3 \
        enable-ssl3 enable-ssl3-method \
        enable-des enable-rc4 \
        enable-weak-ssl-ciphers;

    make -j "${CPU_CORES}";
    make install_sw;

    _copy_license LICENSE.txt openssl;
}

compile_libssh2() {
    echo "Compiling libssh2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github libssh2/libssh2 "${LIBSSH2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -fi

    PKG_CONFIG="pkg-config --static" \
        LDFLAGS="-L${PREFIX}/lib ${LDFLAGS}" CFLAGS="-O3" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-shared=no \
            --with-crypto=openssl --with-libssl-prefix="${PREFIX}";
    make -j "${CPU_CORES}";
    make install;

    _copy_license COPYING libssh2;
}

compile_nghttp2() {
    echo "Compiling nghttp2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github nghttp2/nghttp2 "${NGHTTP2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-http3 \
            --enable-lib-only --enable-shared=no;
    make -j "${CPU_CORES}";
    make install;

    _copy_license COPYING nghttp2;
}

compile_ngtcp2() {
    echo "Compiling ngtcp2, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    if [ "${TLS_LIB}" = "openssl" ]; then
        return
    fi
    local url
    change_dir;

    url_from_github ngtcp2/ngtcp2 "${NGTCP2_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --with-openssl="${PREFIX}" \
            --with-libnghttp3="${PREFIX}" --enable-lib-only --enable-shared=no;

    make -j "${CPU_CORES}";
    make install;
    cp -a crypto/includes/ngtcp2/ngtcp2_crypto_quictls.h crypto/includes/ngtcp2/ngtcp2_crypto.h \
        "${PREFIX}/include/ngtcp2/"

    _copy_license COPYING ngtcp2;
}

compile_nghttp3() {
    echo "Compiling nghttp3, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    url_from_github ngtcp2/nghttp3 "${NGHTTP3_VERSION}"
    url="${URL}"
    download_and_extract "${url}"

    autoreconf -i --force
    PKG_CONFIG="pkg-config --static" \
        ./configure --host="${TARGET}" --prefix="${PREFIX}" --enable-static --enable-shared=no \
        --enable-lib-only --disable-dependency-tracking;
    make -j "${CPU_CORES}";
    make install;

    _copy_license COPYING nghttp3;
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

    PKG_CONFIG="pkg-config --static" \
        cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="${PREFIX}" -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_OSX_ARCHITECTURES:STRING="${ARCH}" ..;
    PKG_CONFIG="pkg-config --static" \
        cmake --build . --config Release --target install;

    _copy_license ../LICENSE brotli;
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

    PKG_CONFIG="pkg-config --static" \
        make -j "${CPU_CORES}" PREFIX="${PREFIX}";
    make install;

    _copy_license LICENSE zstd
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
            --host="${ARCH}-apple-darwin" \
            --prefix="${PREFIX}" \
            --disable-shared --enable-static \
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
            --with-ca-bundle=/etc/ssl/cert.pem \
            --with-ca-path=/etc/ssl/certs \
            --with-ca-fallback --enable-ares \
            --disable-ldap --disable-ldaps --disable-rtsp \
            --disable-rtmp --disable-rtmps "${ENABLE_DEBUG}" \
            CFLAGS="-I${PREFIX}/include" \
            CPPFLAGS="-I${PREFIX}/include";
}

compile_curl() {
    echo "Compiling cURL, Arch: ${ARCH}" | tee "${RELEASE_DIR}/running"
    local url
    change_dir;

    # move the dylib
    mkdir -p "${PREFIX}/lib/dylib"
    mv "${PREFIX}/lib/"*.dylib "${PREFIX}/lib/dylib/"

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
        [ -z "${CURL_VERSION}" ] && CURL_VERSION=$(echo "${SOURCE_DIR}" | cut -d'-' -f 2);
    fi

    if [ ! -f src/.checksrc ]; then echo "enable STDERR" > src/.checksrc; fi
    curl_config;
    LDFLAGS="-L${PREFIX}/lib -static -all-static -Wl,-s ${LDFLAGS}" \
        CFLAGS="-I${PREFIX}/include -I${PREFIX}/include/brotli" \
        CPPFLAGS="-I${PREFIX}/include -I${PREFIX}/include/brotli" \
        make -j "${CPU_CORES}";

    _copy_license COPYING curl;
    make install;
}

install_curl() {
    mkdir -p "${RELEASE_DIR}/release/bin/"

    ls -l "${PREFIX}"/bin/curl;
    cp -pf "${PREFIX}/bin/curl" "${RELEASE_DIR}/release/bin/curl-macos-${ARCH}";

    ${PREFIX}/bin/curl -V || true

    if [ ! -f "${RELEASE_DIR}/version.txt" ]; then
        echo "${CURL_VERSION}" > "${RELEASE_DIR}/version.txt"
    fi

    XZ_OPT=-9 tar -Jcf "${RELEASE_DIR}/release/curl-macos-${ARCH}-dev-${CURL_VERSION}.tar.xz" -C "${DIR}" "curl-${ARCH}"
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
    local arches="x86_64 arm64"
    return $(_arch_match "${ARCH}" "${arches}")
}

compile() {
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

    install_curl;
}

main() {
    local arch_temp

    if [ "${ARCHES}" = "" ] && [ "${ARCHS}" = "" ] && [ "${ARCH}" = "" ]; then
        ARCHES="$(uname -m)";
    elif [ "${ARCHES}" = "" ] && [ "${ARCHS}" != "" ]; then
        ARCHES="${ARCHS}";    # previous misspellings, keep this parameter for compatibility
    elif [ "${ARCHES}" = "" ] && [ "${ARCH}" != "" ]; then
        ARCHES="${ARCH}";
    fi

    init_env;                    # Initialize the build env
    install_packages;            # Install dependencies
    set -o errexit -o xtrace;

    echo "Compiling for all ARCHes: ${ARCHES}"
    for arch_temp in ${ARCHES}; do
        # Set the ARCH, PREFIX and PKG_CONFIG_PATH env variables
        export ARCH=${arch_temp}
        export PREFIX="${DIR}/curl-${ARCH}"
        export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"

        echo "Architecture: ${ARCH}"
        echo "Prefix directory: ${PREFIX}"

        if _arch_valid; then
            compile;
        else
            echo "Unsupported architecture ${ARCH}";
        fi
    done
}

# If the first argument is not "--source-only" then run the script,
# otherwise just provide the functions
if [ "$1" != "--source-only" ]; then
    main "$@";
fi
