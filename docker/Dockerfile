# syntax=docker/dockerfile:1.7
# One-device internal TrailONE 505 build. Uses existing application Gradle versions.
# The base-image digest is from the exact Gradle image used in the prior Docker build.
FROM gradle:8.13-jdk17-noble@sha256:ec973fb6579488ecba8cd24f4728cd819eda45d6b26f7f44a81682e1c034898f AS builder

USER root
ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/home/gradle/.gradle
ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/*

# Align with the existing Gradle config and previously tested Android SDK setup.
ARG ANDROID_CMDLINE_TOOLS_REV=15859902
RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
 && curl -fsSL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REV}_latest.zip" -o /tmp/android-tools.zip \
 && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
 && mv /tmp/android-tools/cmdline-tools/* "${ANDROID_SDK_ROOT}/cmdline-tools/latest/" \
 && rm -rf /tmp/android-tools /tmp/android-tools.zip

RUN yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null \
 && sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" "platforms;android-36" "build-tools;35.0.0" "platform-tools" \
 && mkdir -p /home/gradle/.android /home/gradle/.gradle \
 && chown -R gradle:gradle "${ANDROID_SDK_ROOT}" /home/gradle/.android /home/gradle/.gradle

USER gradle
WORKDIR /workspace
COPY --chown=gradle:gradle . .

# Changes in the host keystore invalidate APK build caching without putting the
# keystore itself into any image layer, logs, or archived artifact.
ARG SIGNING_KEY_SHA256
RUN --mount=type=cache,id=trailone505-gradle-v1,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    --mount=type=secret,id=trailone_debug_keystore,target=/home/gradle/.android/debug.keystore,uid=1000,gid=1000,mode=0400,required=true \
    set -eu; \
    test -n "${SIGNING_KEY_SHA256}"; \
    test -s /home/gradle/.android/debug.keystore; \
    keytool -list -keystore /home/gradle/.android/debug.keystore -storepass android -alias androiddebugkey >/dev/null; \
    gradle --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug; \
    "${ANDROID_SDK_ROOT}/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk

# No keys or build-system internals are included in the exported artifact stage.
FROM scratch AS artifacts
COPY --from=builder /workspace/app/build/outputs/apk/debug/app-debug.apk /TrailOneGPX-debug.apk
COPY --from=builder /workspace/app/build/test-results/testDebugUnitTest/ /reports/testDebugUnitTest/
