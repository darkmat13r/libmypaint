package com.example.mypaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi

/**
 * High-performance custom view for MyPaint drawing.
 * Owns the native canvas (via MyPaintBridge) and performs efficient rendering.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Canvas dimensions for native drawing surface
    private val canvasWidth = 1024
    private val canvasHeight = 800

    private val bridge = MyPaintBridge()

    // Reusable bitmap for displaying the native canvas
    private val bitmap: Bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)

    // Buffers reused to avoid allocations in the render loop
    private var bgraBuffer: ByteArray = ByteArray(canvasWidth * canvasHeight * 4)

    // View<->Bitmap mapping (fitCenter)
    private val dstRect = RectF()
    private val drawMatrix = Matrix()           // bitmap->view
    private val inverseMatrix = Matrix()        // view->bitmap

    // Touch manager to produce rich events
    private val touchManager = TouchEventManager(
        onStart = { pe ->
            bridge.beginStroke()
            bridge.strokeTo(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.tilt, pe.tilt)
            updateBitmapAndInvalidate()
        },
        onMove = { pe ->
            bridge.strokeTo(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.tilt, pe.tilt)
            updateBitmapAndInvalidate()
        },
        onEnd = { pe ->
            bridge.strokeTo(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.tilt, pe.tilt)
            bridge.endStroke()
            updateBitmapAndInvalidate()
        }
    )

    init {
        // Initialize native canvas once
        bridge.initCanvas(canvasWidth, canvasHeight)
        bridge.setColorRgb(0.0f, 0.7f, 1.0f)
        // Seed the bitmap with initial canvas content
        updateBitmapAndInvalidate()
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun clear() {
        bridge.clearCanvas()
        updateBitmapAndInvalidate()
    }

    fun setColor(r: Float, g: Float, b: Float) {
        bridge.setColorRgb(r, g, b)
    }

    fun setBrushSize(sizePx: Float) {
        bridge.setBrushSize(sizePx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeFitCenterMatrices(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // draw bitmap scaled to fit
        canvas.drawBitmap(bitmap, drawMatrix, null)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchManager.process(event.actionMasked, event) { vx, vy ->
            mapViewToBitmap(vx, vy)
        }
    }

    private fun computeFitCenterMatrices(w: Int, h: Int) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val vw = w.toFloat().coerceAtLeast(1f)
        val vh = h.toFloat().coerceAtLeast(1f)

        dstRect.set(0f, 0f, vw, vh)
        val src = RectF(0f, 0f, bw, bh)
        drawMatrix.reset()
        drawMatrix.setRectToRect(src, dstRect, Matrix.ScaleToFit.CENTER)
        // Inverse for mapping view->bitmap
        drawMatrix.invert(inverseMatrix)
    }

    private fun mapViewToBitmap(x: Float, y: Float): Pair<Float, Float>? {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        val ix = pts[0]
        val iy = pts[1]
        if (ix < 0f || iy < 0f || ix > bitmap.width || iy > bitmap.height) return null
        return Pair(ix, iy)
    }

    private fun updateBitmapAndInvalidate() {
        bridge.readRgba()?.let { rgba ->
            if (bgraBuffer.size != rgba.size) {
                bgraBuffer = ByteArray(rgba.size)
            }
            rgbaToPremultipliedBGRA(rgba, bgraBuffer)
            val buffer = java.nio.ByteBuffer.wrap(bgraBuffer)
            bitmap.setHasAlpha(true)
            bitmap.setPremultiplied(true)
            bitmap.copyPixelsFromBuffer(buffer)
            invalidate()
        }
    }

    // Convert RGBA bytes to premultiplied BGRA (Android Bitmap native order)
    private fun rgbaToPremultipliedBGRA(src: ByteArray, dst: ByteArray) {
        var i = 0
        while (i < src.size) {
            val r = src[i + 0].toInt() and 0xFF
            val g = src[i + 1].toInt() and 0xFF
            val b = src[i + 2].toInt() and 0xFF
            val a = src[i + 3].toInt() and 0xFF
            val pr = (r * a + 127) / 255
            val pg = (g * a + 127) / 255
            val pb = (b * a + 127) / 255
            dst[i + 0] = pb.toByte()
            dst[i + 1] = pg.toByte()
            dst[i + 2] = pr.toByte()
            dst[i + 3] = a.toByte()
            i += 4
        }
    }
}
