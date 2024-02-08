# Static cURL with HTTP3

Static cURL binary built with HTTP3, brotli, and zstd support.

The script will automatically retrieve the latest version of each component.  
Simply execute it to compile the most recent version.

**Included components**

- [openssl](https://www.openssl.org)
- [quictls](https://github.com/quictls/openssl)
- [libssh2](https://github.com/libssh2/libssh2)
- [nghttp3](https://github.com/ngtcp2/nghttp3)
- [ngtcp2](https://github.com/ngtcp2/ngtcp2)
- [nghttp2](https://github.com/nghttp2/nghttp2)
- [brotli](https://github.com/google/brotli)
- [zstd](https://github.com/facebook/zstd)
- [zlib](https://zlib.net)
- [libidn2](https://github.com/libidn/libidn2)
- [c-ares](https://c-ares.haxx.se)
- [libpsl](https://rockdaboot.github.io/libpsl/)

`curl -V`
- Protocols: dict file ftp ftps gopher gophers http https imap imaps mqtt pop3 pop3s rtsp scp sftp smb smbs smtp smtps telnet tftp ws wss
- Features: alt-svc AsynchDNS brotli HSTS HTTP2 HTTP3 HTTPS-proxy IDN IPv6 Largefile libz NTLM PSL SSL threadsafe TLS-SRP TrackMemory UnixSockets zstd

## Usage

Download the latest release from the [Releases page](https://github.com/stunnel/static-curl/releases/latest).  
Extract the archive and use it.  
The binary is built with GitHub Actions.

## Compile

This script utilizes `clang` or `qbt-musl-cross-make` for cross-compilation on Linux, providing support for the following architectures:

- Linux
  - x86_64
  - aarch64
  - armv7l
  - i686
  - riscv64
  - s390x
  - mips64
  - mips64el
  - mips
  - mipsel
  - powerpc64le
  - powerpc
- macOS
  - x86_64
  - aarch64
- Windows
  - x86_64
  - aarch64
  - i686
  - armv7

### How to compile

#### Linux

- To compile locally, install Docker, clone the Git repository, navigate to the repository directory, and then execute the following command:  
`sh curl-static-cross.sh`  
script will create a container and compile the host architecture cURL only.

- To compile in docker, run:  
  ```shell
  docker run --network host --rm -v $(pwd):/mnt -w /mnt \
      --name "build-curl-$(date +%Y%m%d-%H%M)" \
      -e ARCH=all \
      -e ARCHS="x86_64 aarch64 armv7l i686 riscv64 s390x mips64 mips64el mips mipsel powerpc64le powerpc" \
      -e TLS_LIB="openssl" \
      -e CURL_VERSION="" \
      -e QUICTLS_VERSION="" \
      -e OPENSSL_VERSION="" \
      -e NGTCP2_VERSION="" \
      -e NGHTTP3_VERSION="" \
      -e NGHTTP2_VERSION="" \
      -e ZLIB_VERSION="" \
      -e LIBUNISTRING_VERSION="" \
      -e LIBIDN2_VERSION="" \
      -e LIBPSL_VERSION="" \
      -e ARES_VERSION="" \
      debian:latest sh curl-static-cross.sh
  ```

#### macOS

Run the following command to compile:

```shell
ARCHS="x86_64 arm64" \
    TLS_LIB=openssl \
    CURL_VERSION="" \
    QUICTLS_VERSION="" \
    OPENSSL_VERSION="" \
    NGTCP2_VERSION="" \
    NGHTTP3_VERSION="" \
    NGHTTP2_VERSION="" \
    LIBIDN2_VERSION="" \
    LIBUNISTRING_VERSION="" \
    ZLIB_VERSION="" \
    BROTLI_VERSION="" \
    ZSTD_VERSION="" \
    LIBSSH2_VERSION="" \
    LIBPSL_VERSION="" \
    ARES_VERSION="" \
    bash curl-static-mac.sh
```

#### Windows

- To compile locally, install Docker, clone the Git repository, navigate to the repository directory, and then execute the following command:  
  `ARCH=x86_64 sh curl-static-win.sh`  
  script will create a Linux container and cross-compile cURL via [LLVM MinGW toolchain](https://github.com/mstorsjo/llvm-mingw).

- To compile in docker, run:
  ```shell
  docker run --network host --rm -v $(pwd):/mnt -w /mnt \
      --name "build-curl-$(date +%Y%m%d-%H%M)" \
      -e ARCH=all \
      -e ARCHS="x86_64 i686 aarch64 armv7" \
      -e TLS_LIB="openssl" \
      -e CURL_VERSION="" \
      -e QUICTLS_VERSION="" \
      -e OPENSSL_VERSION="" \
      -e NGTCP2_VERSION="" \
      -e NGHTTP3_VERSION="" \
      -e NGHTTP2_VERSION="" \
      -e ZLIB_VERSION="" \
      -e LIBUNISTRING_VERSION="" \
      -e LIBIDN2_VERSION="" \
      -e LIBPSL_VERSION="" \
      -e ARES_VERSION="" \
      mstorsjo/llvm-mingw:latest sh curl-static-win.sh
  ```

#### Environment Variables

Supported Environment Variables list:  
For all `VERSION` variables, leaving them blank will automatically fetch the latest version.

- `ARCH`: The architecture to compile. The default is the host architecture. If set to `all`, all architectures listed in `ARCHS` will be compiled.
- `ARCHS`: The list of architectures to compile. You can set one or multiple architectures from the following options: `x86_64 aarch64 armv7l i686 riscv64 s390x mips64 mips64el mips mipsel powerpc64le powerpc`.
- `TLS_LIB`: The TLS library. `quictls`(default) or `openssl`(requires openssl 3.2.0 or later, and curl 8.6.0+).
- `CURL_VERSION`: The version of cURL. If set to `dev`, will clone the latest source code from GitHub.
- `QUICTLS_VERSION`: The version of quictls.
- `OPENSSL_VERSION`: The version of OpenSSL.
- `NGTCP2_VERSION`: The version of ngtcp2.
- `NGHTTP3_VERSION`: The version of nghttp3.
- `NGHTTP2_VERSION`: The version of nghttp2.
- `LIBUNISTRING_VERSION`: The version of libunistring.
- `LIBIDN2_VERSION`: The version of libidn2.
- `LIBPSL_VERSION`: The version of libpsl.
- `LIBSSH2_VERSION`: The version of libssh2.
- `ZLIB_VERSION`: The version of zlib.
- `BROTLI_VERSION`: The version of brotli.
- `ZSTD_VERSION`: The version of zstd.
- `ARES_VERSION`: The version of c-ares.
- `ENABLE_DEBUG`: Enable curl debug. Default is `false`, set to `true` or `yes` to enable it.

The compiled files will be saved in the current `release` directory.

## Why build cURL on my own?

Because I need to test HTTP3, but currently there is no Linux distribution's cURL that supports HTTP3.
