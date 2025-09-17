package com.example.mypaint

class MyPaintBridge {
    companion object {
        init {
            System.loadLibrary("mypaint-jni")
        }
    }
    external fun renderDemo(width: Int, height: Int): ByteArray?
}
