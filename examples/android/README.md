Android demo app using libmypaint via JNI

This sample shows how to integrate the libmypaint brush engine into an Android app using a small JNI bridge built with CMake.

Prerequisites
- Build libmypaint for Android per doc/ANDROID.md for your chosen ABIs (arm64-v8a recommended).
- Copy the resulting libmypaint.so to app/src/main/jniLibs/<ABI>/libmypaint.so
- Ensure the header files for the same build are available under build-android/libmypaint/<ABI>/prefix/include (default path used by CMakeLists).

Open and run in Android Studio
1. File > Open… and select this directory (examples/android).
2. Let Gradle sync. It will build a small JNI library (mypaint-jni) that links to the prebuilt libmypaint.so from jniLibs.
3. Run on a device/emulator. The app will render a red rectangle using libmypaint and display it in an ImageView.

Notes
- To change the libmypaint include prefix, pass -DLIBMYPAINT_PREFIX=/path/to/prefix via Gradle’s cmake arguments or edit app/src/main/cpp/CMakeLists.txt.
- If you built json-c as a shared library separately from libmypaint, you also need to place its .so in jniLibs/<ABI>/ and import it in the CMakeLists similarly to libmypaint.
- The JNI function signature is Java_com_example_mypaint_MyPaintBridge_renderDemo; adjust package/class names if you refactor the Kotlin code.
