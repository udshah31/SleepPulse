#!/bin/sh
set -eu

REPO_ROOT="${SRCROOT}/.."
CONFIGURATION_LOWER=$(printf '%s' "${CONFIGURATION:-Debug}" | tr '[:upper:]' '[:lower:]')
case "${SDK_NAME:-iphonesimulator}" in
  iphonesimulator*) KOTLIN_TARGET="iosSimulatorArm64"; SECONDARY_KOTLIN_TARGET="iosX64" ;;
  iphoneos*) KOTLIN_TARGET="iosArm64" ;;
  *) echo "Unsupported SDK_NAME: ${SDK_NAME:-}" >&2; exit 1 ;;
esac

case "$CONFIGURATION_LOWER" in
  debug) GRADLE_TASK=":shared:linkDebugFramework${KOTLIN_TARGET}"; SECONDARY_GRADLE_TASK=":shared:linkDebugFramework${SECONDARY_KOTLIN_TARGET:-}" ;;
  release) GRADLE_TASK=":shared:linkReleaseFramework${KOTLIN_TARGET}"; SECONDARY_GRADLE_TASK=":shared:linkReleaseFramework${SECONDARY_KOTLIN_TARGET:-}" ;;
  *) echo "Unsupported CONFIGURATION: ${CONFIGURATION:-}" >&2; exit 1 ;;
esac

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17)
  export JAVA_HOME
fi

"$REPO_ROOT/gradlew" -p "$REPO_ROOT" -PenableIosTargets=true "$GRADLE_TASK" --console=plain

if [ -n "${SECONDARY_KOTLIN_TARGET:-}" ]; then
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" -PenableIosTargets=true "$SECONDARY_GRADLE_TASK" --console=plain
fi

FRAMEWORK_BUILD_TYPE="${CONFIGURATION_LOWER}Framework"
SOURCE_FRAMEWORK="$REPO_ROOT/shared/build/bin/$KOTLIN_TARGET/$FRAMEWORK_BUILD_TYPE/SleepPulseShared.framework"
DESTINATION="${SRCROOT}/build/KotlinFrameworks/${CONFIGURATION}/${SDK_NAME}/SleepPulseShared.framework"

if [ ! -d "$SOURCE_FRAMEWORK" ]; then
  echo "Kotlin framework was not produced at $SOURCE_FRAMEWORK" >&2
  exit 1
fi

mkdir -p "${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}"
rm -rf "${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}/SleepPulseShared.framework"
mkdir -p "$(dirname "$DESTINATION")"
rm -rf "$DESTINATION"
cp -R "$SOURCE_FRAMEWORK" "$DESTINATION"

if [ -n "${SECONDARY_KOTLIN_TARGET:-}" ]; then
  SECONDARY_SOURCE_FRAMEWORK="$REPO_ROOT/shared/build/bin/$SECONDARY_KOTLIN_TARGET/$FRAMEWORK_BUILD_TYPE/SleepPulseShared.framework"
  lipo -create \
    "$SOURCE_FRAMEWORK/SleepPulseShared" \
    "$SECONDARY_SOURCE_FRAMEWORK/SleepPulseShared" \
    -output "$DESTINATION/SleepPulseShared"
fi
