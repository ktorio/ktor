# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-noble

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
    ca-certificates curl fonts-liberation git \
    libasound2t64 libatk-bridge2.0-0 libatk1.0-0 libc6 libcairo2 libcups2 libcurl4 libcurl4-gnutls-dev \
    libdbus-1-3 libexpat1 libfontconfig1 libgbm1 libgdk-pixbuf2.0-0 libglib2.0-0 libgtk-3-0 libncurses6 libnspr4 \
    libnss3 libpango-1.0-0 libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 \
    libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 lsb-release \
    nodejs npm \
    temurin-8-jdk temurin-11-jdk temurin-17-jdk \
    unzip wget xdg-utils sudo
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
