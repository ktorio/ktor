FROM ubuntu:18.04

RUN apt-get update -qq && apt-get install -qqy --no-install-recommends \
    git wget bzip2 file unzip libtool pkg-config cmake build-essential \
    automake yasm gettext autopoint vim python ninja-build subversion \
    ca-certificates wget && \
    apt-get clean -y && \
    rm -rf /var/lib/apt/lists/*


RUN git config --global user.name "LLVM MinGW" && \
    git config --global user.email root@localhost

WORKDIR /build

ENV TOOLCHAIN_PREFIX=/opt/llvm-mingw

ARG TOOLCHAIN_ARCHS="i686 x86_64 armv7 aarch64"

ARG DEFAULT_CRT=ucrt
ARG LLVM_VERSION=20210423
ARG WINRES_VERSION=20211002

# Build everything that uses the llvm monorepo. We need to build the mingw runtime before the compiler-rt/libunwind/libcxxabi/libcxx runtimes.
RUN mkdir -p $TOOLCHAIN_PREFIX && ARCH=$(uname -m) && wget -O llvm-mingw.tar.xz \
    https://github.com/mstorsjo/llvm-mingw/releases/download/$LLVM_VERSION/llvm-mingw-$LLVM_VERSION-ucrt-ubuntu-18.04-$ARCH.tar.xz && \
    tar xf llvm-mingw.tar.xz --strip-components 1 -C $TOOLCHAIN_PREFIX && rm -R llvm-mingw*

# Build everything that uses the llvm monorepo. We need to build the mingw runtime before the compiler-rt/libunwind/libcxxabi/libcxx runtimes.
RUN mkdir -p /tmp/llvm-mingw && ARCH=$(uname -m) && wget -O llvm-mingw.tar.xz \
    https://github.com/mstorsjo/llvm-mingw/releases/download/$WINRES_VERSION/llvm-mingw-$WINRES_VERSION-ucrt-ubuntu-18.04-$ARCH.tar.xz && \
    tar xf llvm-mingw.tar.xz --strip-components 1 -C /tmp/llvm-mingw && rm -R llvm-mingw* && \
    cp /tmp/llvm-mingw/lib/libLLVM-13.so $TOOLCHAIN_PREFIX/lib && \
    cd $TOOLCHAIN_PREFIX/bin && \
    cp /tmp/llvm-mingw/bin/llvm-rc ./ && \
    ln -sf llvm-rc llvm-windres && \
    for arch in $TOOLCHAIN_ARCHS; do ln -sf llvm-windres $arch-w64-mingw32-windres; done && \
    rm -R /tmp/llvm-mingw

# Install cmake > 3.18 where was fixed bug with windres
RUN wget https://github.com/Kitware/CMake/releases/download/v3.19.3/cmake-3.19.3-Linux-$(uname -m).tar.gz && \
    tar -zxf cmake-*.tar.gz -C /opt && \
    rm cmake-*.tar.gz && \
    mv /opt/cmake-* /opt/cmake

ENV PATH=/opt/cmake/bin:$TOOLCHAIN_PREFIX/bin:$PATH
