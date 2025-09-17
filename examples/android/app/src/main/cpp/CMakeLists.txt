cmake_minimum_required(VERSION 3.22)
project(mypaint_android_jni C)

set(CMAKE_C_STANDARD 11)

# Default include prefix where libmypaint was installed per doc/ANDROID.md
# This assumes you built from the repository root into build-android/libmypaint/<ABI>/prefix
# You can override this from Gradle by passing -DLIBMYPAINT_PREFIX=/path/to/prefix
if(NOT DEFINED ANDROID_ABI)
  set(ANDROID_ABI "arm64-v8a")
endif()

set(DEFAULT_PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../build-android/libmypaint/${ANDROID_ABI}/prefix")
set(LIBMYPAINT_PREFIX "${DEFAULT_PREFIX}" CACHE PATH "Install prefix of libmypaint for the current ABI")

message(STATUS "Using LIBMYPAINT_PREFIX=${LIBMYPAINT_PREFIX}")

# Import the prebuilt shared library from jniLibs
# Place libmypaint-2.0.so under: app/src/main/jniLibs/<ABI>/libmypaint-2.0.so
add_library(mypaint SHARED IMPORTED)
set_target_properties(mypaint PROPERTIES
  IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmypaint.so")

# Android log
find_library(log-lib log)

# JNI shim
add_library(mypaint-jni SHARED mypaint_jni.c)

target_include_directories(mypaint-jni PRIVATE
  ${LIBMYPAINT_PREFIX}/include
  ${LIBMYPAINT_PREFIX}/include/libmypaint-${LIBMYPAINT_API_VERSION}
  ${LIBMYPAINT_PREFIX}/include/libmypaint-2.0
  ${LIBMYPAINT_PREFIX}/include/libmypaint-${LIBMYPAINT_API_PLATFORM_VERSION}
)

# If libmypaint was linked statically to json-c, you don't need to link json-c here.
# If you built json-c as a shared library, import and link it similarly to mypaint.

target_link_libraries(mypaint-jni
  mypaint
  ${log-lib}
)
