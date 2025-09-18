package com.example.mypaint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageView: ImageView = findViewById(R.id.imageView)
        val renderBtn: Button = findViewById(R.id.renderBtn)

        val bridge = MyPaintBridge()
        val width = 600
        val height = 300

        fun render() {
            val bytes = bridge.renderDemo(width, height)
            if (bytes != null) {
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Copy RGBA into bitmap
                // Bitmap's memory layout for ARGB_8888 is platform-endian. On little-endian it is BGRA.
                // Our native buffer is RGBA. We must swizzle to BGRA and PREMULTIPLY RGB to satisfy Canvas.
                val bgra = ByteArray(bytes.size)
                var i = 0
                while (i < bytes.size) {
                    val r = bytes[i + 0].toInt() and 0xFF
                    val g = bytes[i + 1].toInt() and 0xFF
                    val b = bytes[i + 2].toInt() and 0xFF
                    val a = bytes[i + 3].toInt() and 0xFF
                    // Premultiply (rounding): c' = (c * a + 127) / 255
                    val pr = (r * a + 127) / 255
                    val pg = (g * a + 127) / 255
                    val pb = (b * a + 127) / 255
                    bgra[i + 0] = pb.toByte() // B
                    bgra[i + 1] = pg.toByte() // G
                    bgra[i + 2] = pr.toByte() // R
                    bgra[i + 3] = a.toByte()  // A
                    i += 4
                }
                val buffer = java.nio.ByteBuffer.wrap(bgra)
                // Bitmap with premultiplied alpha (default true) is required by Canvas
                bmp.setHasAlpha(true)
                bmp.setPremultiplied(true)
                bmp.copyPixelsFromBuffer(buffer)
                imageView.setImageBitmap(bmp)
            }
        }

        renderBtn.setOnClickListener { render() }
        render()
    }
}
