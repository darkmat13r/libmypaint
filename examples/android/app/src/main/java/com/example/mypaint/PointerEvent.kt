package com.example.mypaint

/**
 * Immutable, rich pointer sample used by the drawing pipeline.
 * Values not provided by the device will be Float.NaN to avoid allocations.
 */
data class PointerEvent(
    val pointerId: Int,
    val toolType: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float,         // radians, or NaN if not available
    val orientation: Float,  // radians, or NaN if not available
    val azimuth: Float,      // radians, or NaN if not available
    val timestampNs: Long,
    val deltaTimeSec: Float
)
