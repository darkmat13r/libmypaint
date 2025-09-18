package com.example.mypaint

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * GLSurfaceView-based drawing view that renders the libmypaint canvas into a GL texture
 * and displays it full-screen. Input events are queued and processed on a background
 * worker thread to avoid UI-thread stalls.
 */
class GLDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    // Native bridge
    private val bridge = MyPaintBridge()

    // Fixed canvas size for the native surface
    private val canvasWidth = 1024
    private val canvasHeight = 800

    // GL renderer (runs on GL thread)
    private val renderer = CanvasRenderer(canvasWidth, canvasHeight) { bridge.readRgba() }

    // Touch mapping (fitCenter-like) from view coords -> canvas coords
    private val drawMatrix = Matrix()           // canvas->view
    private val inverseMatrix = Matrix()        // view->canvas
    private val dstRect = RectF()

    // Event queue for strokes and dabs
    private val eventQueue = LinkedBlockingQueue<StrokeEvent>()
    @Volatile private var running = true

    // Background worker that consumes events and calls into libmypaint
    private val workerThread = thread(start = true, name = "StrokeWorker") {
        var inStroke = false
        var pendingBrushJson: String? = null
        while (running) {
            try {
                val ev = eventQueue.take()
                when (ev) {
                    is StrokeEvent.Begin -> {
                        if (!inStroke) {
                            bridge.beginStroke()
                            inStroke = true
                        }
                        bridge.strokeTo(ev.x, ev.y, ev.pressure, ev.dt, ev.xtilt, ev.ytilt)
                        // Flush mid-stroke so renderer can see updates before stroke end
                        bridge.flush()
                        requestRenderOnGL()
                    }
                    is StrokeEvent.Move -> {
                        // Drain additional events and process them; prefer coalescing consecutive moves
                        val drained: MutableList<StrokeEvent> = ArrayList(16)
                        drained.add(ev)
                        eventQueue.drainTo(drained, 100)
                        for (e2 in drained) {
                            when (e2) {
                                is StrokeEvent.Move -> {
                                    bridge.strokeTo(e2.x, e2.y, e2.pressure, e2.dt, e2.xtilt, e2.ytilt)
                                }
                                is StrokeEvent.Begin -> {
                                    if (!inStroke) {
                                        bridge.beginStroke()
                                        inStroke = true
                                    }
                                    bridge.strokeTo(e2.x, e2.y, e2.pressure, e2.dt, e2.xtilt, e2.ytilt)
                                }
                                is StrokeEvent.End -> {
                                    bridge.strokeTo(e2.x, e2.y, e2.pressure, e2.dt, e2.xtilt, e2.ytilt)
                                    bridge.endStroke()
                                    inStroke = false
                                    // If a brush preset change was requested during the stroke, apply it now
                                    pendingBrushJson?.let { json ->
                                        val ok = bridge.loadBrushFromString(json)
                                        if (!ok) {
                                            android.util.Log.e("GLDrawingView", "Deferred brush load failed (mypaint false)")
                                        } else {
                                            android.util.Log.d("GLDrawingView", "Deferred brush preset applied after stroke end")
                                        }
                                        pendingBrushJson = null
                                    }
                                }
                                is StrokeEvent.Action -> {
                                    when (e2.kind) {
                                        StrokeAction.Clear -> {
                                            // If clearing mid-stroke, end it cleanly first to avoid engine state oddities
                                            if (inStroke) {
                                                bridge.endStroke()
                                                inStroke = false
                                            }
                                            bridge.clearCanvas()
                                        }
                                        is StrokeAction.SetBrushSize -> bridge.setBrushSize(e2.kind.sizePx)
                                        is StrokeAction.SetColor -> bridge.setColorRgb(e2.kind.r, e2.kind.g, e2.kind.b)
                                        is StrokeAction.LoadBrush -> {
                                            val json = e2.kind.json
                                            if (inStroke) {
                                                // Defer until stroke end to avoid breaking the current stroke
                                                pendingBrushJson = json
                                                android.util.Log.d("GLDrawingView", "Deferring brush load until stroke end")
                                            } else {
                                                val ok = bridge.loadBrushFromString(json)
                                                if (!ok) {
                                                    android.util.Log.e("GLDrawingView", "Brush preset load failed (mypaint false)")
                                                } else {
                                                    android.util.Log.d("GLDrawingView", "Brush preset loaded successfully")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Flush once after processing the batch so GL sees updates
                        bridge.flush()
                        requestRenderOnGL()
                    }
                    is StrokeEvent.End -> {
                        bridge.strokeTo(ev.x, ev.y, ev.pressure, ev.dt, ev.xtilt, ev.ytilt)
                        bridge.endStroke()
                        inStroke = false
                        // Apply any deferred brush preset now
                        pendingBrushJson?.let { json ->
                            val ok = bridge.loadBrushFromString(json)
                            if (!ok) {
                                android.util.Log.e("GLDrawingView", "Deferred brush load failed (mypaint false)")
                            } else {
                                android.util.Log.d("GLDrawingView", "Deferred brush preset applied after stroke end")
                            }
                            pendingBrushJson = null
                        }
                        requestRenderOnGL()
                    }
                    is StrokeEvent.Action -> {
                        when (ev.kind) {
                            StrokeAction.Clear -> {
                                if (inStroke) {
                                    bridge.endStroke()
                                    inStroke = false
                                }
                                bridge.clearCanvas()
                                requestRenderOnGL()
                            }
                            is StrokeAction.SetBrushSize -> bridge.setBrushSize(ev.kind.sizePx)
                            is StrokeAction.SetColor -> bridge.setColorRgb(ev.kind.r, ev.kind.g, ev.kind.b)
                            is StrokeAction.LoadBrush -> {
                                val json = ev.kind.json
                                if (inStroke) {
                                    pendingBrushJson = json
                                    android.util.Log.d("GLDrawingView", "Deferring brush load until stroke end")
                                } else {
                                    val ok = bridge.loadBrushFromString(json)
                                    if (!ok) {
                                        android.util.Log.e("GLDrawingView", "Brush preset load failed (mypaint false)")
                                    } else {
                                        android.util.Log.d("GLDrawingView", "Brush preset loaded successfully")
                                    }
                                    requestRenderOnGL()
                                }
                            }
                        }
                    }
                }
            } catch (ie: InterruptedException) {
                // Exit when interrupted
                break
            } catch (t: Throwable) {
                // Keep worker alive on unexpected exceptions
                android.util.Log.e("GLDrawingView", "Worker error", t)
            }
        }
    }

    // Touch manager to produce rich events
    private val touchManager = TouchEventManager(
        onStart = { pe ->
            enqueueBegin(pe)
        },
        onMove = { pe ->
            enqueueMove(pe)
        },
        onEnd = { pe ->
            enqueueEnd(pe)
        }
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY

        // Initialize native canvas once
        bridge.initCanvas(canvasWidth, canvasHeight)
        bridge.setColorRgb(0.0f, 0.7f, 1.0f)

        // Trigger first upload
        requestRender()
    }

    fun clear() {
        eventQueue.offer(StrokeEvent.Action(StrokeAction.Clear))
    }

    fun setBrushSize(sizePx: Float) {
        eventQueue.offer(StrokeEvent.Action(StrokeAction.SetBrushSize(sizePx)))
    }

    fun setColor(r: Float, g: Float, b: Float) {
        eventQueue.offer(StrokeEvent.Action(StrokeAction.SetColor(r, g, b)))
    }

    fun loadBrushFromString(json: String) {
        eventQueue.offer(StrokeEvent.Action(StrokeAction.LoadBrush(json)))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        workerThread.interrupt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeFitCenterMatrices(w, h)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchManager.process(event.actionMasked, event) { vx, vy ->
            mapViewToCanvas(vx, vy)
        }
    }

    private fun computeFitCenterMatrices(w: Int, h: Int) {
        val bw = canvasWidth.toFloat()
        val bh = canvasHeight.toFloat()
        val vw = w.toFloat().coerceAtLeast(1f)
        val vh = h.toFloat().coerceAtLeast(1f)

        dstRect.set(0f, 0f, vw, vh)
        val src = RectF(0f, 0f, bw, bh)
        drawMatrix.reset()
        drawMatrix.setRectToRect(src, dstRect, Matrix.ScaleToFit.CENTER)
        drawMatrix.invert(inverseMatrix)
    }

    private fun mapViewToCanvas(x: Float, y: Float): Pair<Float, Float>? {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        val ix = pts[0]
        val iy = pts[1]
        if (ix < 0f || iy < 0f || ix > canvasWidth || iy > canvasHeight) return null
        return Pair(ix, iy)
    }

    private fun enqueueBegin(pe: PointerEvent) {
        eventQueue.offer(StrokeEvent.Begin(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.xtilt, pe.ytilt))
    }

    private fun enqueueMove(pe: PointerEvent) {
        eventQueue.offer(StrokeEvent.Move(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.xtilt, pe.ytilt))
    }

    private fun enqueueEnd(pe: PointerEvent) {
        eventQueue.offer(StrokeEvent.End(pe.x, pe.y, pe.pressure, pe.deltaTimeSec, pe.xtilt, pe.ytilt))
    }

    private fun requestRenderOnGL() {
        // Post to GL thread safely
        queueEvent { requestRender() }
    }

    // Renderer implementation: draws a full-screen textured quad with latest canvas pixels.
    private class CanvasRenderer(
        val widthPx: Int,
        val heightPx: Int,
        val readPixels: () -> ByteArray?
    ) : Renderer {
        private var program = 0
        private var textureId = 0
        private var vertexBuffer: ByteBuffer? = null
        private var texcoordBuffer: ByteBuffer? = null
        private var scaleX: Float = 1f
        private var scaleY: Float = 1f

        override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            program = buildProgram(VERT_SRC, FRAG_SRC)
            textureId = createTexture(widthPx, heightPx)
            setupBuffers()
        }

        override fun onSurfaceChanged(unused: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            // Compute aspect-preserving scale so texture displays with correct image size-based projection
            val texAspect = widthPx.toFloat() / heightPx.toFloat()
            val viewAspect = if (height != 0) width.toFloat() / height.toFloat() else texAspect
            if (viewAspect > texAspect) {
                // Wider view: limit X
                scaleX = texAspect / viewAspect
                scaleY = 1f
            } else {
                // Taller view: limit Y
                scaleX = 1f
                scaleY = viewAspect / texAspect
            }
        }

        override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Upload latest pixels if available
            readPixels()?.let { rgba ->
                val buf = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder())
                buf.put(rgba)
                buf.position(0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0,
                    0, 0, widthPx, heightPx,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                    buf
                )
            }

            drawTexturedQuad()
        }

        private fun setupBuffers() {
            // Fullscreen triangle strip
            val verts = floatArrayOf(
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
            )
            val uvs = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )
            vertexBuffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
            vertexBuffer!!.asFloatBuffer().put(verts).position(0)
            texcoordBuffer = ByteBuffer.allocateDirect(uvs.size * 4).order(ByteOrder.nativeOrder())
            texcoordBuffer!!.asFloatBuffer().put(uvs).position(0)
        }

        private fun drawTexturedQuad() {
            GLES20.glUseProgram(program)
            val posLoc = GLES20.glGetAttribLocation(program, "aPos")
            val uvLoc = GLES20.glGetAttribLocation(program, "aUV")
            val samplerLoc = GLES20.glGetUniformLocation(program, "uTex")
            val scaleLoc = GLES20.glGetUniformLocation(program, "uScale")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(samplerLoc, 0)
            // Apply aspect-preserving scale based on image size vs view size
            GLES20.glUniform2f(scaleLoc, scaleX, scaleY)

            vertexBuffer!!.position(0)
            texcoordBuffer!!.position(0)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glEnableVertexAttribArray(uvLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, texcoordBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(uvLoc)
        }

        private fun createTexture(w: Int, h: Int): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // Allocate storage
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            return id
        }

        private fun buildProgram(vs: String, fs: String): Int {
            val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
            val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, v)
            GLES20.glAttachShader(prog, f)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                throw RuntimeException("Program link failed: $log")
            }
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(id)
                GLES20.glDeleteShader(id)
                throw RuntimeException("Shader compile failed: $log")
            }
            return id
        }

        companion object {
            private const val VERT_SRC = """
                attribute vec2 aPos;
                attribute vec2 aUV;
                varying vec2 vUV;
                uniform vec2 uScale; // scales NDC to preserve image aspect
                void main(){
                    vUV = aUV;
                    vec2 pos = aPos * uScale;
                    gl_Position = vec4(pos, 0.0, 1.0);
                }
            """
            private const val FRAG_SRC = """
                precision mediump float;
                varying vec2 vUV;
                uniform sampler2D uTex;
                void main(){
                    gl_FragColor = texture2D(uTex, vUV);
                }
            """
        }
    }
    private fun loadBrushImmediately(json: String) {
        val ok = bridge.loadBrushFromString(json)
        if (!ok) {
            Log.e("GLDrawingView", "Failed to load brush preset from JSON (mypaint returned false)")
        } else {
            Log.d("GLDrawingView", "Brush preset loaded successfully (batch)")
        }
    }
}



// Stroke event model
private sealed class StrokeEvent {
    data class Begin(val x: Float, val y: Float, val pressure: Float, val dt: Float, val xtilt: Float, val ytilt: Float) : StrokeEvent()
    data class Move(val x: Float, val y: Float, val pressure: Float, val dt: Float, val xtilt: Float, val ytilt: Float) : StrokeEvent()
    data class End(val x: Float, val y: Float, val pressure: Float, val dt: Float, val xtilt: Float, val ytilt: Float) : StrokeEvent()
    data class Action(val kind: StrokeAction) : StrokeEvent()
}

private sealed class StrokeAction {
    data object Clear : StrokeAction()
    data class SetBrushSize(val sizePx: Float) : StrokeAction()
    data class SetColor(val r: Float, val g: Float, val b: Float) : StrokeAction()
    data class LoadBrush(val json: String) : StrokeAction()
}
