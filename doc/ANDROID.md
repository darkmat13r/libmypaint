# Building and Using libmypaint on Android

This guide explains how to cross‑compile libmypaint for Android with the Android NDK and how to integrate it into an Android application through JNI.

It assumes you are comfortable with:
- Android Studio + Gradle basics
- Android NDK r23 or newer (LLVM/Clang toolchains)
- Command line builds with Autotools

If you just want desktop builds, see BUILDING.md and README.md.


## Contents
- Overview
- Choose ABIs and API levels
- Build prerequisites
- Cross‑compiling json-c (dependency)
- Cross‑compiling libmypaint
- Output layout and packaging for Gradle
- Using libmypaint via JNI (minimal example)
- Tips and troubleshooting

---

## Overview
libmypaint is an Autotools project written in C. On Android you will:
1) Build json-c for Android (static or shared), per ABI.
2) Build libmypaint for Android against that json-c, per ABI.
3) Package the resulting .so (or .a) into your app/AAR under src/main/jniLibs/<ABI>.
4) Add a small JNI layer to call the library from Kotlin/Java.

You can build a minimal configuration that excludes GLib and GObject Introspection:
- Configure flags: `--disable-introspection --without-glib --disable-i18n`
- This keeps the footprint smaller and simplifies cross‑compilation.


## Choose ABIs and API levels
Typical ABIs:
- arm64-v8a (recommended)
- armeabi-v7a (optional, 32‑bit)
- x86_64 (emulator/desktop devices)

Recommended minimum API level: 21 (Android 5.0) for all ABIs. Adjust to your needs.

Set once:
- ANDROID_NDK_ROOT: path to your NDK, e.g. $HOME/Library/Android/sdk/ndk/26.1.10909125
- API: 21 (or higher)


## Build prerequisites
Install standard host build tools on your development machine:
- Python 3 (only if building from git to run autogen.sh)
- Autotools: autoconf, automake, libtool, intltool, gettext
- pkg-config (host)

On macOS with Homebrew:
- `brew install autoconf automake libtool intltool gettext pkg-config`
- Ensure gettext tools in PATH: `export PATH="$(brew --prefix gettext)/bin:$PATH"`

On Linux use your distro packages (see BUILDING.md for details).


### Where is the Android NDK installed by default?
- macOS (Android Studio default): $HOME/Library/Android/sdk/ndk/<version>
  - Examples: $HOME/Library/Android/sdk/ndk/26.1.10909125
  - Older layouts: $HOME/Library/Android/sdk/ndk-bundle
- Linux (Android Studio default): $HOME/Android/Sdk/ndk/<version>
- Windows (Android Studio default): %LOCALAPPDATA%\Android\Sdk\ndk\<version>

Tips to locate it:
- In Android Studio: Preferences > Appearance & Behavior > System Settings > Android SDK > SDK Tools tab, then check "NDK" and note the path at the top.
- From a terminal: list directories under your SDK: ls "$HOME/Library/Android/sdk/ndk" (macOS), ls "$HOME/Android/Sdk/ndk" (Linux).

Once found, set:
- export ANDROID_NDK_ROOT="$HOME/Library/Android/sdk/ndk/<version>"

Persist it for zsh (macOS) by appending to ~/.zprofile so your terminal always has the variable set:

```
# Replace <version> with your installed NDK version directory
printf '\n# Android NDK\nexport ANDROID_NDK_ROOT="$HOME/Library/Android/sdk/ndk/<version>"\n' >> ~/.zprofile
# Reload current shell session (or open a new terminal)
source ~/.zprofile
```

If you also want the NDK toolchain binaries in your PATH by default (optional), append this too:
```
# Add NDK toolchain to PATH (resolves clang, llvm-* wrappers automatically)
HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)"
printf 'export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/%s/bin:$PATH"\n' "$HOST_TAG" >> ~/.zprofile
source ~/.zprofile
```

## Environment helper: pick your target and compilers
The NDK provides Clang frontends that embed the target triple and API level in the compiler name.

For arm64-v8a:
```
export ANDROID_NDK_ROOT="$HOME/Library/Android/sdk/ndk/26.1.10909125"
export API=21
export TOOLCHAIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)"

# Compilers (note the API level suffix)
export CC="${TOOLCHAIN}/bin/aarch64-linux-android${API}-clang"
export CXX="${TOOLCHAIN}/bin/aarch64-linux-android${API}-clang++"
export AR="${TOOLCHAIN}/bin/llvm-ar"
export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
export STRIP="${TOOLCHAIN}/bin/llvm-strip"

# Sysroot is implied by these clang wrappers, but you can also set:
export SYSROOT="${TOOLCHAIN}/sysroot"

# Generic optimization flags (safe defaults)
export CFLAGS="-O3 -fPIC"
export LDFLAGS=""
```

For other ABIs, change the triple in CC/CXX:
- armeabi-v7a: `armv7a-linux-androideabi${API}-clang` (and `clang++`)
- x86_64: `x86_64-linux-android${API}-clang` (and `clang++`)

You will repeat the build per ABI.


## Cross‑compiling json-c (static or shared)
Android doesn’t provide json-c. Build it first for each ABI. Example (arm64):

```
# Get json-c source
JSONC_VER=0.17
curl -L -o json-c-${JSONC_VER}.tar.gz https://s3.amazonaws.com/json-c_releases/releases/json-c-${JSONC_VER}.tar.gz
rm -rf json-c-${JSONC_VER}
mkdir -p build-android/json-c/arm64-v8a && tar xf json-c-${JSONC_VER}.tar.gz
cd json-c-${JSONC_VER}

./configure \
  --host=aarch64-linux-android \
  --prefix="$PWD/../build-android/json-c/arm64-v8a/prefix" \
  CC="$CC" AR="$AR" RANLIB="$RANLIB" \
  CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
  --disable-shared --enable-static

make -j$(getconf _NPROCESSORS_ONLN)
make install
cd ..
```

Notes:
- You can choose `--enable-shared` if you prefer a shared libjson-c.so. For app simplicity, static json-c plus a shared libmypaint.so is common.
- Repeat with adjusted CC/CXX/--host and output directories for each ABI.

The important outputs are:
- prefix/lib/libjson-c.a (or .so)
- prefix/include/json-c/*.h
- prefix/lib/pkgconfig/json-c.pc (optional; you can also set CFLAGS/LIBS manually)


## Cross‑compiling libmypaint
From the libmypaint source directory:

1) If you cloned from git, generate the build system:
```
./autogen.sh
```

2) Configure for Android, pointing to your json-c install for this ABI. Minimal build (no GLib, no introspection):

```
ABI=arm64-v8a
JSONC_PREFIX="$(pwd)/../build-android/json-c/${ABI}/prefix"

# Point pkg-config to json-c for cross builds (if you installed the .pc file)
export PKG_CONFIG_LIBDIR="${JSONC_PREFIX}/lib/pkgconfig"
export PKG_CONFIG_PATH="${PKG_CONFIG_LIBDIR}"

# Alternatively, bypass pkg-config by setting CPPFLAGS/LIBS directly:
# export CPPFLAGS="-I${JSONC_PREFIX}/include"
# export LIBS="${JSONC_PREFIX}/lib/libjson-c.a"

./configure \
  --host=aarch64-linux-android \
  --prefix="$PWD/build-android/libmypaint/${ABI}/prefix" \
  CC="$CC" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" \
  CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" \
  --disable-introspection --without-glib --disable-i18n

make -j$(getconf _NPROCESSORS_ONLN)
make install
```

Outputs of interest:
- build-android/libmypaint/${ABI}/prefix/lib/libmypaint.so (shared) and/or libmypaint.a (static)
- build-android/libmypaint/${ABI}/prefix/include/mypaint*.h
- build-android/libmypaint/${ABI}/prefix/lib/pkgconfig/libmypaint.pc (optional)

Repeat for each ABI by changing CC/CXX and `--host` accordingly:
- armeabi-v7a: `--host=arm-linux-androideabi` and CC=`armv7a-linux-androideabi${API}-clang`
- x86_64: `--host=x86_64-linux-android` and CC=`x86_64-linux-android${API}-clang`

Shared vs static:
- By default Autotools builds shared libraries when possible. Android supports shared .so without versioned filenames.
- If you prefer a static lib for linking into your own JNI shared library, use `--disable-shared --enable-static`.


## Packaging for Gradle (Android Studio)
Project structure excerpt (app module):
```
app/
  src/main/
    jniLibs/
      arm64-v8a/
        libmypaint.so
      armeabi-v7a/
        libmypaint.so
      x86_64/
        libmypaint.so
    cpp/
      CMakeLists.txt    # if using CMake for your JNI lib
      mypaint_jni.cpp   # your JNI glue, links against libmypaint
```

Place the ABI‑specific libmypaint.so files in the matching jniLibs subfolders. If you built a static libmypaint.a instead, you won’t place it in jniLibs; you will link it into your own JNI .so at build time.

Headers:
- Add the include directory `prefix/include` to your CMake include paths.
- If you built json-c as a static lib, also add its include path and .a to your link line.

Gradle (CMake) example snippet:
```
# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)
project(mypaint_android C)

set(CMAKE_C_STANDARD 11)

# NDK provides log
find_library(log-lib log)

# Adjust these to your actual paths inside the project or to an external location
set(LIBMYPAINT_PREFIX ${CMAKE_SOURCE_DIR}/../jni-prebuilt/arm64-v8a) # example
include_directories(${LIBMYPAINT_PREFIX}/include)
link_directories(${LIBMYPAINT_PREFIX}/lib)

add_library(mypaint-jni SHARED mypaint_jni.c)

# Link either to prebuilt shared lib or to static archive
add_library(mypaint SHARED IMPORTED)
set_target_properties(mypaint PROPERTIES IMPORTED_LOCATION
  ${LIBMYPAINT_PREFIX}/lib/libmypaint.so)

# If you have json-c as a separate shared lib, import similarly; if static, add full path to target_link_libraries

target_link_libraries(mypaint-jni
  mypaint
  ${log-lib})
```

If you prefer Gradle’s "externalNativeBuild" with ndk-build (Android.mk), the concept is the same: include the headers, import or link the library and ensure per‑ABI paths are correct.


## Using libmypaint via JNI (minimal example)
Below is a tiny JNI layer that exercises the core brush/surface API, similar to examples/minimal.c in this repo. In real apps, you’ll integrate libmypaint with your own tiled surface or with bitmaps rendered via OpenGL/Vulkan.

Java/Kotlin side:
```kotlin
class MyPaintBridge {
    companion object {
        init { System.loadLibrary("mypaint-jni") }
    }
    external fun renderDemo(width: Int, height: Int): ByteArray
}
```

JNI C code (mypaint_jni.c):
```c
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "mypaint-brush.h"
#include "mypaint-fixed-tiled-surface.h"

static void stroke_to(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
    float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
    float pressure = 1.0f, ytilt = 0.0f, xtilt = 0.0f, dtime = 1.0f/10.0f;
    gboolean linear = FALSE;
    mypaint_brush_stroke_to(brush, surf, x, y, pressure, xtilt, ytilt, dtime,
                            viewzoom, viewrotation, barrel_rotation, linear);
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_mypaint_MyPaintBridge_renderDemo(JNIEnv* env, jobject thiz,
                                                  jint width, jint height) {
    int w = width, h = height;

    MyPaintBrush *brush = mypaint_brush_new();
    mypaint_brush_from_defaults(brush);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_H, 0.0f);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_S, 1.0f);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_V, 1.0f);

    MyPaintFixedTiledSurface *surface = mypaint_fixed_tiled_surface_new(w, h);

    mypaint_surface_begin_atomic((MyPaintSurface*)surface);
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, 4*w/5.0f, h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, 4*w/5.0f, 4*h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, 4*h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
    mypaint_surface_end_atomic((MyPaintSurface *)surface, NULL);

    // Convert surface tiles to a simple RGBA byte array for the Java layer.
    // The fixed tiled surface stores 4 floats per pixel (premultiplied RGBA in [0,1]).
    size_t count = (size_t)w * (size_t)h;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)(count * 4));
    jbyte* outBytes = (*env)->GetByteArrayElements(env, out, NULL);

    const float* fpx = mypaint_fixed_tiled_surface_get_data(surface);
    for (size_t i = 0; i < count; ++i) {
        float r = fpx[4*i + 0];
        float g = fpx[4*i + 1];
        float b = fpx[4*i + 2];
        float a = fpx[4*i + 3];
        ((unsigned char*)outBytes)[4*i + 0] = (unsigned char)(r * 255.0f + 0.5f);
        ((unsigned char*)outBytes)[4*i + 1] = (unsigned char)(g * 255.0f + 0.5f);
        ((unsigned char*)outBytes)[4*i + 2] = (unsigned char)(b * 255.0f + 0.5f);
        ((unsigned char*)outBytes)[4*i + 3] = (unsigned char)(a * 255.0f + 0.5f);
    }

    (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

    mypaint_brush_unref(brush);
    mypaint_surface_unref((MyPaintSurface *)surface);

    return out;
}
```

Notes:
- The fixed tiled surface is a simple CPU surface meant for testing. For production you may implement your own MyPaintSurface to render directly where you need it.
- Brush definitions: libmypaint provides the engine; brush presets live in the separate mypaint-brushes project. You can load brush settings from JSON using libmypaint’s API or set values programmatically as above.


## Tips and troubleshooting
- C compiler cannot create executables
  - Usually caused by host compilers or flags leaking into the cross build. Ensure CC points to the NDK clang with the correct triple and API suffix.
  - Unset custom host CFLAGS/LDFLAGS before configuring for Android.
- json-c not found
  - Ensure PKG_CONFIG_PATH/PKG_CONFIG_LIBDIR point to the json-c for the target ABI, or set CPPFLAGS/LIBS explicitly.
- configure: error: source directory already configured; run "make distclean" there first
  - The provided scripts/build-android-so-libs.sh detects this automatically: it cleans any in-tree configure artifacts in the repository root, ensures a fresh per-ABI build directory, and retries configure.
  - If you want to proactively force a clean reconfigure on the next run, set RECONFIGURE=1: `RECONFIGURE=1 ./scripts/build-android-so-libs.sh`.
- Link errors about missing log or Android support libs
  - When building your JNI .so, link against `log` (NDK) if you use __android_log_print, etc.
- Mixed ABIs
  - All native libraries in your APK must provide the same set of ABIs. Either ship one ABI (arm64-v8a) or all needed ABIs consistently.
- API level mismatches
  - The API level is encoded in the compiler name. Use the same API for all third‑party libs you compile.
- Versioned libraries
  - Android doesn’t support versioned SONAMEs. libmypaint’s build system already accounts for this via libtool. You’ll get plain `libmypaint.so`. 

## License and attribution
- libmypaint is ISC licensed. See COPYING.
- When shipping brush presets from mypaint-brushes, review their licenses as well.

If you get stuck, please open an issue with your NDK version, target ABIs/API, exact configure/make output, and the steps you followed.