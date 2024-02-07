FROM ubuntu:20.04

RUN apt-get update -qq && \
    DEBIAN_FRONTEND="noninteractive" apt-get install -qqy --no-install-recommends \
    git wget bzip2 file unzip libtool pkg-config cmake build-essential \
    automake yasm gettext autopoint vim-tiny python3 python3-distutils \
    ninja-build ca-certificates curl less zip && \
    apt-get clean -y && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

ENV TOOLCHAIN_PREFIX=/opt/llvm-mingw

ARG TOOLCHAIN_ARCHS="i686 x86_64 armv7 aarch64"
ARG DEFAULT_CRT=ucrt
ARG LLVM_VERSION=20230614

# Download llvm toolchain
RUN mkdir -p $TOOLCHAIN_PREFIX && ARCH=$(uname -m) && wget -O llvm-mingw.tar.xz \
    https://github.com/mstorsjo/llvm-mingw/releases/download/$LLVM_VERSION/llvm-mingw-$LLVM_VERSION-$DEFAULT_CRT-ubuntu-20.04-$ARCH.tar.xz && \
    tar xf llvm-mingw.tar.xz --strip-components 1 -C $TOOLCHAIN_PREFIX && rm -R llvm-mingw*

# Install cmake > 3.18 where was fixed bug with windres
RUN wget https://github.com/Kitware/CMake/releases/download/v3.27.7/cmake-3.27.7-Linux-$(uname -m).tar.gz && \
    tar -zxf cmake-*.tar.gz -C /opt && \
    rm cmake-*.tar.gz && \
    mv /opt/cmake-* /opt/cmake

ENV PATH=/opt/cmake/bin:$TOOLCHAIN_PREFIX/bin:$PATH
