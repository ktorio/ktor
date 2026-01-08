# syntax=docker/dockerfile:1
# check=skip=FromPlatformFlagConstDisallowed

# Enforce linux/amd64 platform because of two reasons:
# 1. Chrome doesn't provide arm64 binaries for Linux (https://issues.chromium.org/issues/374811603)
# 2. Kotlin/Native doesn't support linux/arm64 as a host platform (KT-36871)
FROM --platform=linux/amd64 eclipse-temurin:21.0.9_10-jdk-noble AS dev

ARG USERNAME=developer
ARG USER_UID=1001
ARG USER_GID=$USER_UID

# Use Bash in "strict mode" to run scripts
SHELL ["/bin/bash", "-euo", "pipefail", "-c"]

COPY chrome-dependencies.txt /tmp/chrome-dependencies.txt

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
    $(grep --invert-match '^#' /tmp/chrome-dependencies.txt | tr '\n' ' ') \
    temurin-8-jdk temurin-11-jdk temurin-17-jdk \
    zip unzip wget xdg-utils sudo
  apt-get clean
  rm -rf /var/lib/apt/lists/* /tmp/chrome-dependencies.txt
EOT

# Create a non-root user
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

# Common variables
ENV KTOR_DEV="true"
ARG local_bin="$HOME/.local/bin"
ENV PATH="$local_bin:$PATH"

# Install Node.js
ARG node_version="22"
ARG nvm_version="0.40.3"
ENV NVM_DIR="$HOME/.nvm"

RUN <<EOT
  # Install NVM
  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v$nvm_version/install.sh | bash
  source $NVM_DIR/nvm.sh

  # Install Node and Yarn Classic
  nvm install "$node_version"
  npm install -g yarn

  # Make Node.js binaries available without "source $NVM_DIR/nvm.sh"
  # This should be done *after* installation of Yarn
  mkdir --parents "$local_bin"
  ln -s "$NVM_BIN"/* "$local_bin"

  # Print versions
  echo "Node $(node --version)"
  echo "NPM $(npm --version)"
  echo "Yarn $(yarn --version)"
EOT

# Install Chrome
ENV CHROME_BIN="$local_bin/chrome"

RUN <<EOT
  source $NVM_DIR/nvm.sh
  browsers_dir="$HOME/.cache/browsers"

  npx @puppeteer/browsers install chrome@stable --path "$browsers_dir"

  # Make Chrome binary visible for Karma
  chrome_bin_path=$(npx @puppeteer/browsers list --path "$browsers_dir" | head -n1 | sed 's/.* //')
  ln -s "$chrome_bin_path" "$CHROME_BIN"
EOT

# Change shell back to bash from jshell
CMD ["/bin/bash"]


# Dev image with installed Android sdkmanager
FROM dev AS dev-android

# Specify build tools version.
# Use the latest available version by default.
# https://developer.android.com/tools/releases/build-tools
ARG build_tools="36.0.0"
ENV ANDROID_BUILD_TOOLS_VERSION=$build_tools

# Define Android environment variables
# https://developer.android.com/tools/variables
# Keep in mind, variable can't be declared and used in the same ENV instructuion
ENV ANDROID_HOME="/opt/android-sdk" \
    ANDROID_USER_HOME="$HOME/.android"
ENV PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
ENV PATH="$PATH:$ANDROID_HOME/platform-tools"
ENV PATH="$PATH:$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS_VERSION"

# Commandline Tools revisions: https://dl.google.com/android/repository/repository2-3.xml (search for "cmdline-tools;latest")
# NOTE: Remember to update cmdline-tools-package.xml on commandlinetools update.
ARG commandlinetools_url=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
ARG commandlinetools_sha1=5fdcc763663eefb86a5b8879697aa6088b041e70

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
