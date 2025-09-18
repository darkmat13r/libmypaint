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
    external fun setColorRgb(r: Float, g: Float, b: Float)
    external fun beginStroke()
    external fun strokeTo(x: Float, y: Float, pressure: Float, dtime: Float, xTilt: Float = Float.NaN, yTilt: Float = Float.NaN)
    external fun endStroke()
    external fun readRgba(): ByteArray?

    // Ensure visible intermediate results while inside atomic
    external fun flush()

    // Brush controls
    external fun setBrushSize(sizePx: Float)

    // Brush preset loading (.myb JSON string)
    external fun loadBrushFromString(json: String): Boolean
}
