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
                // Bitmap expects ARGB, but our byte array is RGBA; we need to swizzle.
                val argb = ByteArray(bytes.size)
                var i = 0
                while (i < bytes.size) {
                    val r = bytes[i + 0]
                    val g = bytes[i + 1]
                    val b = bytes[i + 2]
                    val a = bytes[i + 3]
                    argb[i + 0] = a
                    argb[i + 1] = r
                    argb[i + 2] = g
                    argb[i + 3] = b
                    i += 4
                }
                val buffer = java.nio.ByteBuffer.wrap(argb)
                bmp.copyPixelsFromBuffer(buffer)
                imageView.setImageBitmap(bmp)
            }
        }

        renderBtn.setOnClickListener { render() }
        render()
    }
}
