#!/usr/bin/env bash
set -euo pipefail

# build-android-so-libs.sh
#
# Purpose:
#   Build libmypaint as shared libraries (.so) for multiple Android ABIs using the Android NDK.
#   The script also builds json-c for each ABI (static by default) and then links libmypaint against it.
#   Finally, it copies the resulting libmypaint.so into the Android sample app under
#   examples/android/app/src/main/jniLibs/<ABI>/.
#
# Prereqs:
#   - ANDROID_NDK_ROOT must point to your installed NDK (e.g., $HOME/Library/Android/sdk/ndk/26.1.10909125)
#   - Standard host build tools: autoconf, automake, libtool, intltool, gettext, pkg-config
#   - Python (only when building from git to run ./autogen.sh)
#
# Usage:
#   ANDROID_NDK_ROOT=...</path/to/ndk> ./scripts/build-android-so-libs.sh
#
# Customization via environment variables:
#   ABIS="arm64-v8a armeabi-v7a x86_64"   # Space-separated list of ABIs to build
#   API=21                                 # Android API level
#   JSONC_VER=0.17                         # json-c version to download/build
#   SHARED_JSONC=0                         # Set to 1 to build json-c as shared; default is static
#   CLEAN=0                                # Set to 1 to remove previous build-android artifacts first
#   JOBS                                    # Parallel jobs for make (defaults to host CPU count)
#   RECONFIGURE=0                           # Set to 1 to force a clean reconfigure of libmypaint per ABI before building
#
# Notes:
#   - The script performs out-of-tree builds under ./build-android/{json-c,libmypaint}/<ABI>
#   - For details about the cross-compilation steps, see doc/ANDROID.md

# ------------------------- Configuration -------------------------
: "${ANDROID_NDK_ROOT:?Please set ANDROID_NDK_ROOT to your Android NDK path. On macOS, the default is $HOME/Library/Android/sdk/ndk/<version> (e.g., 26.1.10909125). You can locate it via Android Studio > SDK Manager or by checking $HOME/Library/Android/sdk/ndk.}"
API="${API:-21}"
ABIS_DEFAULT="arm64-v8a armeabi-v7a x86_64"
ABIS="${ABIS:-$ABIS_DEFAULT}"
JSONC_VER="${JSONC_VER:-0.17}"
SHARED_JSONC="${SHARED_JSONC:-0}"
CLEAN="${CLEAN:-0}"
RECONFIGURE="${RECONFIGURE:-0}"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build-android"
JSONC_TARBALL="json-c-${JSONC_VER}.tar.gz"
JSONC_URL="https://s3.amazonaws.com/json-c_releases/releases/${JSONC_TARBALL}"
ANDROID_JNI_LIBS_DIR="$ROOT_DIR/examples/android/app/src/main/jniLibs"

# Determine host tag for the NDK toolchain path (linux-x86_64, darwin-x86_64, darwin-arm64, windows-x86_64)
UNAME_S=$(uname -s | tr '[:upper:]' '[:lower:]')
UNAME_M=$(uname -m)
case "$UNAME_M" in
  x86_64|amd64) UNAME_M=x86_64 ;;
  arm64|aarch64) UNAME_M=arm64 ;;
  *) : ;; # leave as-is
esac
HOST_TAG="${UNAME_S}-${UNAME_M}"
TOOLCHAIN_BASE="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt"
if [[ ! -d "$TOOLCHAIN_BASE/$HOST_TAG" ]]; then
  # Fallbacks for historical NDKs/platforms
  if [[ -d "$TOOLCHAIN_BASE/${UNAME_S}-x86_64" ]]; then
    HOST_TAG="${UNAME_S}-x86_64"
  elif [[ -d "$TOOLCHAIN_BASE/${UNAME_S}-aarch64" ]]; then
    HOST_TAG="${UNAME_S}-aarch64"
  else
    echo "ERROR: Could not locate NDK toolchain under $TOOLCHAIN_BASE for host $HOST_TAG" >&2
    exit 1
  fi
fi
TOOLCHAIN="$TOOLCHAIN_BASE/$HOST_TAG"

if [[ "$CLEAN" == "1" ]]; then
  echo "[CLEAN] Removing $BUILD_DIR"
  rm -rf "$BUILD_DIR"
fi

mkdir -p "$BUILD_DIR" "$ANDROID_JNI_LIBS_DIR"

# Download json-c tarball once (if needed)
if [[ ! -f "$BUILD_DIR/$JSONC_TARBALL" ]]; then
  echo "[DOWNLOAD] ${JSONC_URL}"
  curl -L -o "$BUILD_DIR/$JSONC_TARBALL" "$JSONC_URL"
fi

# Ensure autogen/configure are present for libmypaint (in case building from git)
if [[ ! -x "$ROOT_DIR/configure --disable-i18n" ]]; then
  echo "[BOOTSTRAP] Running ./autogen.sh to generate configure scripts"
  (cd "$ROOT_DIR" && ./autogen.sh)
fi

# If the source tree was previously configured in-tree, clean it now.
# Some Autotools setups complain when the source dir contains config.status/Makefile
# while attempting an out-of-tree configure (per-ABI). Auto-clean to avoid:
#   "configure: error: source directory already configured; run 'make distclean' there first"
if [[ -f "$ROOT_DIR/config.status" || -f "$ROOT_DIR/Makefile" ]]; then
  echo "[SOURCE][CLEAN] Detected in-tree configure artifacts in repo root. Running 'make distclean' to allow clean out-of-tree builds..."
  (cd "$ROOT_DIR" && make distclean) || true
  rm -f "$ROOT_DIR/config.log" "$ROOT_DIR/config.status" "$ROOT_DIR/config.cache" 2>/dev/null || true
fi

# Map ABI to target triples and --host values
abi_to_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7a-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "" ; return 1 ;;
  esac
}
abi_to_host() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "arm-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "" ; return 1 ;;
  esac
}

build_one_abi() {
  local ABI="$1"
  local TRIPLE HOST CC CXX AR RANLIB STRIP SYSROOT

  TRIPLE=$(abi_to_triple "$ABI")
  HOST=$(abi_to_host "$ABI")
  if [[ -z "$TRIPLE" || -z "$HOST" ]]; then
    echo "[SKIP] Unknown ABI: $ABI" >&2
    return 1
  fi

  echo "\n==== Building for ABI: $ABI (API $API) ===="

  CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
  CXX="$TOOLCHAIN/bin/${TRIPLE}${API}-clang++"
  AR="$TOOLCHAIN/bin/llvm-ar"
  RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
  STRIP="$TOOLCHAIN/bin/llvm-strip"
  SYSROOT="$TOOLCHAIN/sysroot"

  export CC CXX AR RANLIB STRIP
  export CFLAGS="-O3 -fPIC"
  export LDFLAGS=""

  # ---------- Build json-c ----------
  local JSONC_BUILD="$BUILD_DIR/json-c/$ABI"
  local JSONC_PREFIX="$JSONC_BUILD/prefix"
  if [[ ! -f "$JSONC_PREFIX/lib/libjson-c.a" && ! -f "$JSONC_PREFIX/lib/libjson-c.so" ]]; then
    echo "[JSON-C] Extracting and building json-c ${JSONC_VER} for $ABI"
    rm -rf "$JSONC_BUILD" && mkdir -p "$JSONC_BUILD"
    ( cd "$JSONC_BUILD" && tar xf "$BUILD_DIR/$JSONC_TARBALL" )
    local SRC_DIR
    SRC_DIR="$(cd "$JSONC_BUILD" && ls -d json-c-* | head -n1)"
    if [[ -x "$JSONC_BUILD/$SRC_DIR/configure" ]]; then
      echo "[JSON-C] Using Autotools configure for json-c"
      ( cd "$JSONC_BUILD/$SRC_DIR" && \
        ./configure \
          --host="$HOST" \
          --prefix="$JSONC_PREFIX" \
          CC="$CC" AR="$AR" RANLIB="$RANLIB" \
          CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
          $( [[ "$SHARED_JSONC" == "1" ]] && echo "--enable-shared --disable-static" || echo "--disable-shared --enable-static" )
      )
      if [[ ! -f "$JSONC_BUILD/$SRC_DIR/Makefile" ]]; then
        echo "[JSON-C][ERROR] configure did not produce a Makefile at $JSONC_BUILD/$SRC_DIR" >&2
        if [[ -f "$JSONC_BUILD/$SRC_DIR/config.log" ]]; then
          echo "[JSON-C][HINT] Last 100 lines of config.log:" >&2
          tail -n 100 "$JSONC_BUILD/$SRC_DIR/config.log" >&2 || true
        fi
        exit 1
      fi
      ( cd "$JSONC_BUILD/$SRC_DIR" && make -j"$JOBS" && make install )
    else
      echo "[JSON-C] configure not found; falling back to CMake build for json-c"
      local CMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
      local AND_ABI
      AND_ABI="$ABI"
      # Create out-of-source build dir for cmake
      mkdir -p "$JSONC_BUILD/$SRC_DIR/build"
      ( cd "$JSONC_BUILD/$SRC_DIR/build" && \
        cmake -G "Unix Makefiles" \
          -DCMAKE_TOOLCHAIN_FILE="$CMAKE_TOOLCHAIN_FILE" \
          -DANDROID_NDK="$ANDROID_NDK_ROOT" \
          -DANDROID_ABI="$AND_ABI" \
          -DANDROID_PLATFORM="android-$API" \
          -DCMAKE_BUILD_TYPE=Release \
          -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
          -DBUILD_TESTING=OFF \
          -DBUILD_SHARED_LIBS=$( [[ "$SHARED_JSONC" == "1" ]] && echo ON || echo OFF ) \
          -DCMAKE_INSTALL_PREFIX="$JSONC_PREFIX" \
          .. \
      )
      if [[ ! -f "$JSONC_BUILD/$SRC_DIR/build/Makefile" && ! -f "$JSONC_BUILD/$SRC_DIR/build/build.ninja" ]]; then
        echo "[JSON-C][ERROR] CMake configuration failed for json-c at $JSONC_BUILD/$SRC_DIR/build" >&2
        exit 1
      fi
      ( cd "$JSONC_BUILD/$SRC_DIR/build" && cmake --build . -j"$JOBS" )
      ( cd "$JSONC_BUILD/$SRC_DIR/build" && cmake --install . )
    fi
  else
    echo "[JSON-C] Using existing json-c for $ABI at $JSONC_PREFIX"
  fi

  # Prepare pkg-config env to prefer the per-ABI json-c
  export PKG_CONFIG_LIBDIR="$JSONC_PREFIX/lib/pkgconfig"
  export PKG_CONFIG_PATH="$PKG_CONFIG_LIBDIR"
  export CPPFLAGS="-I$JSONC_PREFIX/include"
  if [[ -f "$JSONC_PREFIX/lib/libjson-c.a" ]]; then
    export LIBS="$JSONC_PREFIX/lib/libjson-c.a"
  else
    export LIBS="-ljson-c"
    export LD_LIBRARY_PATH="$JSONC_PREFIX/lib:${LD_LIBRARY_PATH:-}"
  fi

  # ---------- Build libmypaint ----------
  local MP_BUILD="$BUILD_DIR/libmypaint/$ABI"
  local MP_PREFIX="$MP_BUILD/prefix"
  mkdir -p "$MP_BUILD"

  if [[ ! -f "$MP_PREFIX/lib/libmypaint.so" ]]; then
    # Always ensure a clean start if this build dir was previously configured
    if [[ -d "$MP_BUILD" && ( -f "$MP_BUILD/Makefile" || -f "$MP_BUILD/config.status" || -f "$MP_BUILD/config.log" ) ]]; then
      echo "[MYPAINT][CLEAN] Detected previous configure/build in $MP_BUILD. Starting from scratch."
      rm -rf "$MP_BUILD"
      mkdir -p "$MP_BUILD"
    fi

    # Optional proactive clean reconfigure if requested
    if [[ "$RECONFIGURE" == "1" && -d "$MP_BUILD" ]]; then
      echo "[MYPAINT][RECONFIGURE] Forcing a clean reconfigure in $MP_BUILD"
      if [[ -f "$MP_BUILD/Makefile" ]]; then
        ( cd "$MP_BUILD" && make distclean ) || true
      fi
      rm -f "$MP_BUILD/config.log" "$MP_BUILD/config.status" "$MP_BUILD/config.cache" 2>/dev/null || true
    fi
    echo "[MYPAINT] Configuring libmypaint for $ABI"
    echo "[MYPAINT][ENV] CC=$CC"
    echo "[MYPAINT][ENV] CFLAGS=$CFLAGS"
    echo "[MYPAINT][ENV] PKG_CONFIG_LIBDIR=$PKG_CONFIG_LIBDIR"
    echo "[MYPAINT][ENV] PKG_CONFIG_PATH=$PKG_CONFIG_PATH"
    echo "[MYPAINT][ENV] CPPFLAGS=$CPPFLAGS"
    echo "[MYPAINT][ENV] LIBS=$LIBS"
    echo "[MYPAINT][ENV] JSONC_PREFIX=$JSONC_PREFIX"
    ( pkg-config --modversion json-c 2>/dev/null || echo "[MYPAINT][INFO] pkg-config json-c not found; relying on CPPFLAGS/LIBS" ) || true

    configure_once() {
      ( cd "$MP_BUILD" && \
        "$ROOT_DIR/configure" \
          --host="$HOST" \
          --prefix="$MP_PREFIX" \
          CC="$CC" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
          CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
          --disable-introspection --without-glib --disable-gegl \
          --enable-shared --disable-static  --disable-i18n
      )
    }

    if ! configure_once; then
      # If the failure is due to "source directory already configured" hint, clean and retry once
      if [[ -f "$MP_BUILD/config.log" ]] && grep -qi "source directory already configured" "$MP_BUILD/config.log"; then
        echo "[MYPAINT][CLEANUP] Detected prior configuration state in $MP_BUILD. Running 'make distclean' and retrying configure..."
        if [[ -f "$MP_BUILD/Makefile" ]]; then
          ( cd "$MP_BUILD" && make distclean ) || true
        fi
        # Also remove Autotools cache files that can confuse reconfigure
        rm -f "$MP_BUILD/config.log" "$MP_BUILD/config.status" "$MP_BUILD/config.cache" 2>/dev/null || true
        if ! configure_once; then
          echo "[MYPAINT][ERROR] configure failed again after distclean in $MP_BUILD" >&2
          if [[ -f "$MP_BUILD/config.log" ]]; then
            echo "[MYPAINT][HINT] Last 200 lines of config.log:" >&2
            tail -n 200 "$MP_BUILD/config.log" >&2 || true
          fi
          exit 1
        fi
      else
        echo "[MYPAINT][ERROR] configure failed for $ABI" >&2
        if [[ -f "$MP_BUILD/config.log" ]]; then
          echo "[MYPAINT][HINT] Last 200 lines of config.log:" >&2
          tail -n 200 "$MP_BUILD/config.log" >&2 || true
        fi
        exit 1
      fi
    fi

    if [[ ! -f "$MP_BUILD/Makefile" ]]; then
      echo "[MYPAINT][ERROR] configure did not produce a Makefile at $MP_BUILD" >&2
      if [[ -f "$MP_BUILD/config.log" ]]; then
        echo "[MYPAINT][HINT] Last 200 lines of config.log:" >&2
        tail -n 200 "$MP_BUILD/config.log" >&2 || true
      fi
      exit 1
    fi
    echo "[MYPAINT] Building libmypaint for $ABI"
    ( cd "$MP_BUILD" && make -j"$JOBS" && make install )
  else
    echo "[MYPAINT] Using existing libmypaint for $ABI at $MP_PREFIX"
  fi

  # Copy .so to Android sample jniLibs
  if [[ ! -d "$MP_PREFIX/lib" ]]; then
    echo "[MYPAINT][ERROR] Expected install lib dir not found: $MP_PREFIX/lib" >&2
    echo "[MYPAINT][HINT] The build or install step likely failed earlier. See logs above." >&2
    return 1
  fi
  local SO_SRC
  SO_SRC=$(find "$MP_PREFIX/lib" -maxdepth 1 -name 'libmypaint*.so' -print -quit 2>/dev/null || true)
  if [[ -z "$SO_SRC" ]]; then
    echo "[MYPAINT][ERROR] libmypaint .so not found in $MP_PREFIX/lib" >&2
    return 1
  fi
  local ABI_JNI_DIR="$ANDROID_JNI_LIBS_DIR/$ABI"
  mkdir -p "$ABI_JNI_DIR"
  cp -f "$SO_SRC" "$ABI_JNI_DIR/libmypaint-2.0.so"
  echo "[OUT] Copied $(basename "$SO_SRC") -> $ABI_JNI_DIR/libmypaint-2.0.so"

  # Print hint for headers (useful for CMake include dirs)
  echo "[HINT] Headers available at: $MP_PREFIX/include"
}

# ------------------------- Main loop ----------------------------
START_TIME=$(date +%s)
echo "Using NDK: $ANDROID_NDK_ROOT"
echo "Toolchain: $TOOLCHAIN"
echo "ABIs: $ABIS"
echo "API: $API"
echo "json-c: $JSONC_VER (shared=$SHARED_JSONC)"

declare -a BUILT_ABIS=()
for ABI in $ABIS; do
  if build_one_abi "$ABI"; then
    BUILT_ABIS+=("$ABI")
  else
    echo "[FAIL] Build failed for ABI: $ABI" >&2
    exit 1
  fi
done

END_TIME=$(date +%s)
DUR=$((END_TIME-START_TIME))

echo "\nAll done in ${DUR}s. Built ABIs: ${BUILT_ABIS[*]}"
echo "Shared libraries placed under: $ANDROID_JNI_LIBS_DIR/<ABI>/libmypaint.so"
echo "If using Android Studio, you can now build/run the sample app under examples/android."
