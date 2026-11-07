#!/usr/bin/env bash
# changjietu C3F1 Build Environment Preflight
#
# Deterministic, fail-fast check of host prerequisites required to build this
# project (see README "Supported build matrix"). Independent of Gradle business
# logic; run this BEFORE ./gradlew to surface missing prerequisites early,
# instead of an obscure daemon/processDebugResources startup failure.
#
# Env overrides (used by negative preflight tests):
#   PREFLIGHT_SDK_DIR   override Android SDK root (default: local.properties sdk.dir,
#                       else ANDROID_HOME, else ANDROID_SDK_ROOT)
#   JAVA_HOME           JDK used to resolve `java` (default: `java` on PATH)
#
# Exit code: 0 = all prerequisites satisfied; 1 = fatal missing prerequisite.

set -u

fail() { echo "PREFLIGHT FAIL: $1" >&2; exit 1; }
report() { printf '%-40s %s\n' "$1" "$2"; }

# --- 1. host architecture ----------------------------------------------
HOST_ARCH=$(uname -m)
report "host_arch" "$HOST_ARCH"

# --- 2. Java major version MUST be 17 (the verified JDK) ---------------
JAVA_BIN="java"
if [ -n "${JAVA_HOME:-}" ]; then JAVA_BIN="$JAVA_HOME/bin/java"; fi
if ! command -v "$JAVA_BIN" >/dev/null 2>&1 && [ ! -x "$JAVA_BIN" ]; then
  fail "java executable not found (resolved JAVA_BIN=$JAVA_BIN). Set JAVA_HOME to a JDK 17, e.g. /usr/lib/jvm/java-17-openjdk-arm64."
fi
JAVA_VER=$( "$JAVA_BIN" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' )
JAVA_MAJOR=$( printf '%s' "$JAVA_VER" | awk -F. '{print $1}' )
if [ "$JAVA_MAJOR" = "1" ]; then JAVA_MAJOR=$( printf '%s' "$JAVA_VER" | awk -F. '{print $2}' ); fi
report "java_version" "$JAVA_VER (major=$JAVA_MAJOR)"
if [ "$JAVA_MAJOR" != "17" ]; then
  fail "Java major version is $JAVA_MAJOR, but this project is validated on JDK 17 only. Set JAVA_HOME to a JDK 17 (found: $JAVA_BIN)."
fi

# --- 3. Android SDK root resolvable ------------------------------------
SDK_DIR="${PREFLIGHT_SDK_DIR:-}"
if [ -z "$SDK_DIR" ]; then
  DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
  if [ -f "$DIR/../local.properties" ]; then
    SDK_DIR=$(sed -n 's/^sdk\.dir=//p' "$DIR/../local.properties" | tail -n1)
  fi
fi
if [ -z "$SDK_DIR" ] && [ -n "${ANDROID_HOME:-}" ]; then SDK_DIR="$ANDROID_HOME"; fi
if [ -z "$SDK_DIR" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then SDK_DIR="$ANDROID_SDK_ROOT"; fi
if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
  fail "Android SDK root unresolved. Provide local.properties sdk.dir, or ANDROID_HOME / ANDROID_SDK_ROOT / PREFLIGHT_SDK_DIR."
fi
report "sdk_root" "$SDK_DIR"

# --- 4. build-tools;35.0.0 exists --------------------------------------
BT_DIR="$SDK_DIR/build-tools/35.0.0"
if [ ! -d "$BT_DIR" ]; then
  fail "Android SDK Build Tools 35.0.0 missing ($BT_DIR). Install: sdkmanager 'build-tools;35.0.0' (or via Android Studio SDK Manager)."
fi
report "build_tools" "35.0.0 -> $BT_DIR"

# --- 5. SDK aapt2 exists + executable ----------------------------------
AAPT2="$BT_DIR/aapt2"
if [ ! -x "$AAPT2" ]; then
  fail "SDK aapt2 not found or not executable ($AAPT2). Reinstall build-tools;35.0.0."
fi
report "aapt2_path" "$AAPT2"
report "aapt2_sha256" "$( sha256sum "$AAPT2" | awk '{print $1}' )"

# --- 6. aapt2 version (native on x86-64, via qemu on arm64) ------------
if [ "$HOST_ARCH" = "aarch64" ] || [ "$HOST_ARCH" = "arm64" ]; then
  if command -v qemu-x86_64 >/dev/null 2>&1; then
    QEMU_BIN=$(command -v qemu-x86_64)
    report "qemu_path" "$QEMU_BIN"
    report "qemu_version" "$( "$QEMU_BIN" --version 2>&1 | head -n1 )"
  else
    fail "qemu-x86_64 not found on PATH. ARM64 host requires it to run the x86-64 SDK aapt2. Install qemu-user (provides qemu-x86_64), e.g. 'apt install qemu-user'."
  fi
  report "aapt2_version" "$( "$QEMU_BIN" "$AAPT2" version 2>&1 | head -n1 )"
  report "aapt2_executor" "qemu-x86_64 -> SDK aapt2"
else
  report "aapt2_version" "$( "$AAPT2" version 2>&1 | head -n1 )"
  report "aapt2_executor" "native SDK aapt2"
fi

# --- 7. wrapper-resolved real AAPT2 path -------------------------------
report "wrapper_resolved_aapt2" "$AAPT2"
report "preflight" "PASS"