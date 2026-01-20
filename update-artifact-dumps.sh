#!/bin/bash

set -e

./gradlew validatePublishedArtifacts --dump publishAndroidNativePublications
./gradlew validatePublishedArtifacts --dump publishDarwinPublications
./gradlew validatePublishedArtifacts --dump publishJsPublications
./gradlew validatePublishedArtifacts --dump publishJvmAndCommonPublications
./gradlew validatePublishedArtifacts --dump publishLinuxPublications
./gradlew validatePublishedArtifacts --dump publishWindowsPublications
