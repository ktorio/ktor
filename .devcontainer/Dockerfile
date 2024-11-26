FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt update \
    && apt install -yq ca-certificates curl fonts-liberation git \
      libasound2t64 libatk-bridge2.0-0 libatk1.0-0 libc6 libcairo2 libcups2 libcurl4 libcurl4-gnutls-dev \
      libdbus-1-3 libexpat1 libfontconfig1 libgbm1 libgdk-pixbuf2.0-0 libglib2.0-0 libgtk-3-0 libncurses6 libnspr4 \
      libnss3 libpango-1.0-0 libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 \
      libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 lsb-release \
      nodejs npm \
      openjdk-11-jdk openjdk-17-jdk openjdk-21-jdk openjdk-8-jdk \
      unzip wget xdg-utils \
    && apt clean \
    && mkdir -p /etc/sudoers.d \
    && useradd -m -s /bin/bash developer \
    && echo "developer ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers.d/nopasswd \
    && chmod 0440 /etc/sudoers.d/nopasswd

USER developer

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

RUN export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    && export "JAVA_HOME=$(/usr/libexec/java_home)" >> ~/.bash_profile
