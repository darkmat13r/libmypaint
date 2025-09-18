package com.example.mypaint

class MyPaintBridge {
    companion object {
        init {
            System.loadLibrary("mypaint-jni")
        }
    }
    // Existing demo renderer
    external fun renderDemo(width: Int, height: Int): ByteArray?

    // Persistent canvas and interactive stroke API
    external fun initCanvas(width: Int, height: Int)
    external fun clearCanvas()
    external fun beginStroke()
    external fun strokeTo(x: Float, y: Float, pressure: Float, dtime: Float)
    external fun endStroke()
    external fun readRgba(): ByteArray?
}
