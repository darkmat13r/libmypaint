Place your prebuilt libmypaint.so here in ABI-specific subdirectories:

- arm64-v8a/libmypaint.so
- armeabi-v7a/libmypaint.so (optional)
- x86_64/libmypaint.so (optional)

You can build these with the instructions in doc/ANDROID.md. The CMakeLists.txt in app/src/main/cpp imports the .so from this directory at build time.
