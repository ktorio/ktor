# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.6_7-jdk-noble AS dev

ARG USERNAME=developer
ARG USER_UID=1001
ARG USER_GID=$USER_UID

# Use Bash in "strict mode" to run scripts
SHELL ["/bin/bash", "-euxo", "pipefail", "-c"]

RUN <<EOT
  # Add Adoptium repository to download Eclipse Temurin JDK
  # https://adoptium.net/installation/linux/#_deb_installation_on_debian_or_ubuntu
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
  echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

  # Make apt-get non-interactive
  export DEBIAN_FRONTEND=noninteractive

  apt-get update
  apt-get install --yes --no-install-recommends \
    git util-linux openssh-server \
    ca-certificates curl fonts-liberation \
    libasound2t64 libatk-bridge2.0-0 libatk1.0-0 libc6 libcairo2 libcups2 libcurl4 libcurl4-gnutls-dev \
    libdbus-1-3 libexpat1 libfontconfig1 libgbm1 libgdk-pixbuf2.0-0 libglib2.0-0 libgtk-3-0 libncurses6 libnspr4 \
    libnss3 libpango-1.0-0 libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 \
    libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 lsb-release \
    nodejs npm \
    temurin-8-jdk temurin-11-jdk temurin-17-jdk \
    zip unzip wget xdg-utils sudo
  apt-get clean
  rm -rf /var/lib/apt/lists/*
EOT

# Create non-root user
RUN <<EOT
  groupadd --gid $USER_GID $USERNAME
  useradd --uid $USER_UID --gid $USER_GID --create-home $USERNAME
  echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME
  chmod 0440 /etc/sudoers.d/$USERNAME
EOT

# Switch user and default shell
USER $USERNAME
ENV HOME=/home/$USERNAME \
    SHELL=/bin/bash
WORKDIR $HOME

# Change shell back to bash from jshell
CMD ["/bin/bash"]


# Dev image with installed Android sdkmanager
FROM dev AS dev-android

# Specify build tools version.
# Use the latest available version by default.
# https://developer.android.com/tools/releases/build-tools
ARG build_tools="35.0.0"
ENV ANDROID_BUILD_TOOLS_VERSION=$build_tools

# Define Android environment variables
# https://developer.android.com/tools/variables
# Keep in mind, variable can't be declared and used in the same ENV instructuion
ENV ANDROID_HOME="/opt/android-sdk" \
    ANDROID_USER_HOME="$HOME/.android"
ENV PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
ENV PATH="$PATH:$ANDROID_HOME/platform-tools"
ENV PATH="$PATH:$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS_VERSION"

# Commandline Tools revisions: https://dl.google.com/android/repository/repository2-3.xml
# NOTE: Remember to update cmdline-tools-package.xml on commandlinetools update.
ARG commandlinetools_url=https://dl.google.com/android/repository/commandlinetools-linux-12266719_latest.zip
ARG commandlinetools_sha1=47e61d3bb57b5e907a74f225a767a767a8b4d7a5

WORKDIR $ANDROID_HOME
RUN <<EOF
  # Download and install Android Commandline Tools
  wget --quiet --output-document=commandlinetools.zip ${commandlinetools_url}
  echo "$commandlinetools_sha1 commandlinetools.zip" | sha1sum --check

  unzip -qq commandlinetools.zip -d tmp/
  mkdir cmdline-tools/
  mv tmp/cmdline-tools cmdline-tools/latest
  rm -rf tmp commandlinetools.zip

  # Workaround for "Warning: File .android/repositories.cfg could not be loaded."
  mkdir --parents ${ANDROID_USER_HOME}
  touch "$ANDROID_USER_HOME/repositories.cfg"

  # Accept licenses and install build tools and platform tools
  # https://developer.android.com/tools/releases/build-tools
  # https://developer.android.com/tools/releases/platform-tools
  (yes || true) | sdkmanager --licenses
  (yes || true) | sdkmanager \
    "build-tools;$ANDROID_BUILD_TOOLS_VERSION" \
    "platform-tools"
  rm -rf "$ANDROID_USER_HOME/cache"
EOF

# Make cmdline-tools visible for sdkmanager
COPY cmdline-tools-package.xml "cmdline-tools/latest/package.xml"

WORKDIR $HOME
