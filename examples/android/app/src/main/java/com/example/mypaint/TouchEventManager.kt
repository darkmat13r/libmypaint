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

    fun reset() {
        lastEventTimeNs = 0L
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
                    val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt, historicalIndex = i)
                    onMove(pe)
                    lastEventTimeNs = tNs
                }
                val mapped = mapper(ev.x, ev.y) ?: return false
                val tNs = ev.eventTimeNanos
                val dt = computeDt(tNs)
                val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt)
                onMove(pe)
                lastEventTimeNs = tNs
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mapped = mapper(ev.x, ev.y)
                val tNs = ev.eventTimeNanos
                val dt = computeDt(tNs)
                if (mapped != null) {
                    val pe = buildPointerEvent(ev, mapped.first, mapped.second, tNs, dt)
                    onEnd(pe)
                } else {
                    // still signal end with last known metrics
                    val pe = buildPointerEvent(ev, ev.x, ev.y, tNs, dt)
                    onEnd(pe)
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
            tilt = tilt,
            orientation = orientation,
            azimuth = azimuth,
            timestampNs = timestampNs,
            deltaTimeSec = dt
        )
    }
}
