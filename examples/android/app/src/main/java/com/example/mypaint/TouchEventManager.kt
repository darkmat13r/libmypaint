package com.example.mypaint

import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi

/**
 * Efficiently transforms MotionEvent streams into PointerEvent callbacks,
 * handling historical samples and consistent delta time computation.
 */
class TouchEventManager(
    private val onStart: (PointerEvent) -> Unit,
    private val onMove: (PointerEvent) -> Unit,
    private val onEnd: (PointerEvent) -> Unit,
) {
    private var lastEventTimeNs: Long = 0L
    private var lastMappedX: Float? = null
    private var lastMappedY: Float? = null
    private var lastPressure: Float? = null
    private var lastXTilt: Float? = null
    private var lastYTilt: Float? = null

    fun reset() {
        lastEventTimeNs = 0L
        lastMappedX = null
        lastMappedY = null
        lastPressure = null
        lastXTilt = null
        lastYTilt = null
    }

    /**
     * Process a MotionEvent for a single-pointer drawing scenario.
     * Caller is responsible for mapping x/y to the drawing surface space.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun process(
        action: Int,
        ev: MotionEvent,
        mapper: (Float, Float) -> Pair<Float, Float>?,
    ): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val mapped = mapper(ev.x, ev.y) ?: return false
                lastEventTimeNs = ev.eventTimeNanos
                // Update last mapped/cache
                lastMappedX = mapped.first
                lastMappedY = mapped.second
                lastPressure = ev.pressure.coerceIn(0f, 1f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val tilt = ev.getAxisValue(MotionEvent.AXIS_TILT)
                    lastXTilt = tilt
                    lastYTilt = tilt
                }
                val pe = buildPointerEvent(ev, mapped.first, mapped.second, lastEventTimeNs, 1f / 60f)
                onStart(pe)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val history = ev.historySize
                for (i in 0 until history) {
                    val mx = ev.getHistoricalX(i)
                    val my = ev.getHistoricalY(i)
                    val mapped = mapper(mx, my) ?: continue
                    val tMs = ev.getHistoricalEventTime(i)
                    val tNs = tMs * 1_000_000L
                    val dt = computeDt(tNs)
                    // Update caches
                    lastMappedX = mapped.first
                    lastMappedY = mapped.second
                    lastPressure = ev.getHistoricalPressure(i).coerceIn(0f, 1f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val tilt = ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, i)
                        lastXTilt = tilt
                        lastYTilt = tilt
                    }
                    val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt, historicalIndex = i)
                    onMove(pe)
                    lastEventTimeNs = tNs
                }
                val mapped = mapper(ev.x, ev.y) ?: return false
                val tNs = ev.eventTimeNanos
                val dt = computeDt(tNs)
                // Update caches
                lastMappedX = mapped.first
                lastMappedY = mapped.second
                lastPressure = ev.pressure.coerceIn(0f, 1f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val tilt = ev.getAxisValue(MotionEvent.AXIS_TILT)
                    lastXTilt = tilt
                    lastYTilt = tilt
                }
                val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt)
                onMove(pe)
                lastEventTimeNs = tNs
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tNs = ev.eventTimeNanos
                val dt = computeDt(tNs)
                val mapped = mapper(ev.x, ev.y)
                if (mapped != null) {
                    // Normal case: inside view; update cache and emit mapped coords
                    lastMappedX = mapped.first
                    lastMappedY = mapped.second
                    lastPressure = ev.pressure.coerceIn(0f, 1f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val tilt = ev.getAxisValue(MotionEvent.AXIS_TILT)
                        lastXTilt = tilt
                        lastYTilt = tilt
                    }
                    val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt)
                    onEnd(pe)
                } else {
                    // Fallback: if finger lifted outside, reuse last mapped coords if available
                    val lx = lastMappedX
                    val ly = lastMappedY
                    if (lx != null && ly != null) {
                        val pe = buildPointerEvent(ev, lx, ly, tNs, dt)
                        onEnd(pe)
                    } else {
                        // No valid prior point; ignore
                        reset()
                        return false
                    }
                }
                reset()
                return true
            }
            else -> return false
        }
    }

    private fun computeDt(tNs: Long): Float {
        return if (lastEventTimeNs == 0L) 1f / 60f else ((tNs - lastEventTimeNs) / 1_000_000_000.0f)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun buildPointerEvent(
        ev: MotionEvent,
        xMapped: Float,
        yMapped: Float,
        timestampNs: Long,
        dt: Float,
        historicalIndex: Int = -1,
    ): PointerEvent {
        val pressure = if (historicalIndex >= 0) ev.getHistoricalPressure(historicalIndex) else ev.pressure
        val toolType = ev.getToolType(0)
        val orientation = if (historicalIndex >= 0) ev.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, 0, historicalIndex) else ev.getAxisValue(MotionEvent.AXIS_ORIENTATION)
        val tilt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (historicalIndex >= 0) ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, historicalIndex) else ev.getAxisValue(MotionEvent.AXIS_TILT)
        } else Float.NaN
        val azimuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (historicalIndex >= 0) ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, 0, historicalIndex) else ev.getAxisValue(MotionEvent.AXIS_TILT)
        } else Float.NaN
        return PointerEvent(
            pointerId = ev.getPointerId(0),
            toolType = toolType,
            x = xMapped,
            y = yMapped,
            pressure = pressure.coerceIn(0f, 1f),
            xtilt = tilt,
            ytilt = tilt,
            orientation = orientation,
            azimuth = azimuth,
            timestampNs = timestampNs,
            deltaTimeSec = dt
        )
    }
}
