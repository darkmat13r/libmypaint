#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "mypaint-brush.h"
#include "mypaint-fixed-tiled-surface.h"
#include "mypaint_log.h"

static void stroke_to_default(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
    float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
    float pressure = 1.0f, ytilt = 0.0f, xtilt = 0.0f, dtime = 1.0f/10.0f;
    gboolean linear = FALSE;
    mypaint_brush_stroke_to(brush, surf, x, y, pressure, xtilt, ytilt, dtime,
                            viewzoom, viewrotation, barrel_rotation, linear);
}

static void dump_surface_stats(MyPaintFixedTiledSurface *surface, int w, int h) {
    size_t n = (size_t)w * (size_t)h * 4;
    unsigned char *buf = (unsigned char*)malloc(n);
    if (!buf) return;
    mypaint_fixed_tiled_surface_read_rgba8(surface, buf);
    unsigned long long sumA = 0, nonzero = 0;
    unsigned char minA = 255, maxA = 0;
    for (size_t i = 0; i < n; i += 4) {
        unsigned char a = buf[i+3];
        sumA += a;
        if (a) {
            nonzero++;
            if (a < minA) minA = a;
            if (a > maxA) maxA = a;
        }
    }
    double avgA = (double)sumA / (double)(w*h);
    LOGI("Surface stats: pixels=%dx%d, nonzero_alpha_pixels=%llu, alpha_avg=%.2f, alpha_min=%u, alpha_max=%u",
         w, h, (unsigned long long)nonzero, avgA, (unsigned)minA, (unsigned)maxA);

    free(buf);
}

// Persistent canvas state for interactive drawing
static MyPaintFixedTiledSurface *g_surface = NULL;
static MyPaintBrush *g_brush = NULL;
static int g_w = 0, g_h = 0;
// Current stroke color (RGB in 0..1) - still tracked for convenience
static float g_color_r = 1.0f, g_color_g = 0.0f, g_color_b = 0.0f;
// Track whether we are currently inside an atomic block
static int g_in_atomic = 0;

static void free_canvas() {
    if (g_brush) { mypaint_brush_unref(g_brush); g_brush = NULL; }
    if (g_surface) { mypaint_surface_unref((MyPaintSurface*)g_surface); g_surface = NULL; }
    g_w = g_h = 0;
    g_in_atomic = 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_mypaint_MyPaintBridge_renderDemo(JNIEnv* env, jobject thiz,
                                                  jint width, jint height) {
    int w = width, h = height;
    LOGI("renderDemo start: %dx%d", w, h);

    MyPaintBrush *brush = mypaint_brush_new();
    mypaint_brush_from_defaults(brush);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_H, 0.0f);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_S, 1.0f);
    mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_V, 1.0f);

    MyPaintFixedTiledSurface *surface = mypaint_fixed_tiled_surface_new(w, h);

    mypaint_surface_begin_atomic((MyPaintSurface*)surface);
    stroke_to_default(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
    stroke_to_default(brush, (MyPaintSurface*)surface, 4*w/5.0f, h/5.0f);
    stroke_to_default(brush, (MyPaintSurface*)surface, 4*w/5.0f, 4*h/5.0f);
    stroke_to_default(brush, (MyPaintSurface*)surface, w/5.0f, 4*h/5.0f);
    stroke_to_default(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
    mypaint_surface_end_atomic((MyPaintSurface *)surface, NULL);

    size_t count = (size_t)w * (size_t)h;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)(count * 4));

    if (!out) {
        LOGE("Failed to allocate output byte array of size %zu", count * 4);
        mypaint_brush_unref(brush);
        mypaint_surface_unref((MyPaintSurface *)surface);
        return NULL;
    }
    jboolean isCopy = JNI_FALSE;
    jbyte* outBytes = (*env)->GetByteArrayElements(env, out, &isCopy);
    if (!outBytes) {
        LOGE("GetByteArrayElements returned NULL");
        mypaint_brush_unref(brush);
        mypaint_surface_unref((MyPaintSurface *)surface);
        return NULL;
    }

    // Fill the output buffer directly from the tiled surface as RGBA8
    mypaint_fixed_tiled_surface_read_rgba8(surface, (unsigned char*)outBytes);

    dump_surface_stats(surface, w, h);

    (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

    mypaint_brush_unref(brush);
    mypaint_surface_unref((MyPaintSurface *)surface);

    LOGI("renderDemo completed");
    return out;
}

// Initialize persistent canvas and brush
JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_initCanvas(JNIEnv* env, jobject thiz, jint width, jint height) {
    if (width <= 0 || height <= 0) return;
    free_canvas();
    g_w = width; g_h = height;
    g_surface = mypaint_fixed_tiled_surface_new(g_w, g_h);
    g_brush = mypaint_brush_new();
    mypaint_brush_from_defaults(g_brush);
    // Example color: red
    mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_H, 0.0f);
    mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_S, 1.0f);
    mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_V, 1.0f);
    g_in_atomic = 0;
}

JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_clearCanvas(JNIEnv* env, jobject thiz) {
    if (g_w <= 0 || g_h <= 0) return;
    if (g_surface) { mypaint_surface_unref((MyPaintSurface*)g_surface); }
    g_surface = mypaint_fixed_tiled_surface_new(g_w, g_h);
    g_in_atomic = 0;
}

// Load a MyPaint brush preset from a JSON string (.myb contents). Returns true on success.
JNIEXPORT jboolean JNICALL
Java_com_example_mypaint_MyPaintBridge_loadBrushFromString(JNIEnv* env, jobject thiz, jstring jsonStr) {
    if (!jsonStr) return JNI_FALSE;
    if (!g_brush) {
        g_brush = mypaint_brush_new();
        mypaint_brush_from_defaults(g_brush);
    }
    const char* cjson = (*env)->GetStringUTFChars(env, jsonStr, NULL);
    if (!cjson) return JNI_FALSE;
    gboolean ok = mypaint_brush_from_string(g_brush, cjson);
    (*env)->ReleaseStringUTFChars(env, jsonStr, cjson);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Set the current stroke color used by direct dabs (RGB components in 0..1)
JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_setColorRgb(JNIEnv* env, jobject thiz, jfloat r, jfloat g, jfloat b) {
    // Clamp inputs to [0,1]
    if (r < 0.f) r = 0.f; if (r > 1.f) r = 1.f;
    if (g < 0.f) g = 0.f; if (g > 1.f) g = 1.f;
    if (b < 0.f) b = 0.f; if (b > 1.f) b = 1.f;
    g_color_r = r; g_color_g = g; g_color_b = b;
    // Also set brush HSV so mypaint_brush_stroke_to uses this color
    if (g_brush) {
        // Convert RGB [0,1] to HSV
        float max = r; if (g > max) max = g; if (b > max) max = b;
        float min = r; if (g < min) min = g; if (b < min) min = b;
        float v = max;
        float s = (max <= 0.f) ? 0.f : (max - min) / max;
        float h = 0.f;
        if (s > 0.f) {
            float d = max - min;
            if (max == r) {
                h = (g - b) / d;
            } else if (max == g) {
                h = 2.f + (b - r) / d;
            } else {
                h = 4.f + (r - g) / d;
            }
            h /= 6.f;
            if (h < 0.f) h += 1.f;
        }
        mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_H, h);
        mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_S, s);
        mypaint_brush_set_base_value(g_brush, MYPAINT_BRUSH_SETTING_COLOR_V, v);
    }
}

JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_beginStroke(JNIEnv* env, jobject thiz) {
    if (!g_brush || !g_surface) return;
    mypaint_surface_begin_atomic((MyPaintSurface*)g_surface);
    g_in_atomic = 1;
    mypaint_brush_new_stroke(g_brush);

    LOGD("New Brush Stroke");
}

JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_strokeTo(JNIEnv* env, jobject thiz, jfloat x, jfloat y, jfloat pressure, jfloat dtime) {
    if (!g_brush || !g_surface) return;
    // Use brush engine stroke_to so preset (.myb) settings take effect
    float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
    float xtilt = 0.0f, ytilt = 0.0f;
    gboolean linear = FALSE;
    mypaint_brush_stroke_to(g_brush, (MyPaintSurface*)g_surface,
                            x, y,
                            pressure, xtilt, ytilt, dtime,
                            viewzoom, viewrotation, barrel_rotation,
                            linear);
}

JNIEXPORT void JNICALL
Java_com_example_mypaint_MyPaintBridge_endStroke(JNIEnv* env, jobject thiz) {
    if (!g_brush || !g_surface) return;
mypaint_surface_end_atomic((MyPaintSurface*)g_surface, NULL);
g_in_atomic = 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_mypaint_MyPaintBridge_readRgba(JNIEnv* env, jobject thiz) {
    if (!g_surface || g_w <= 0 || g_h <= 0) return NULL;

    // If we are inside an atomic block, temporarily end it so pending dabs are committed
    int reopened = 0;
    if (g_in_atomic) {
        mypaint_surface_end_atomic((MyPaintSurface*)g_surface, NULL);
        g_in_atomic = 0;
        reopened = 1;
    }

    size_t count = (size_t)g_w * (size_t)g_h * 4;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)count);
    if (!out) {
        // If we closed atomic above, reopen it before returning
        if (reopened) {
            mypaint_surface_begin_atomic((MyPaintSurface*)g_surface);
            g_in_atomic = 1;
        }
        return NULL;
    }
    jboolean isCopy = JNI_FALSE;
    jbyte* outBytes = (*env)->GetByteArrayElements(env, out, &isCopy);
    if (!outBytes) {
        if (reopened) {
            mypaint_surface_begin_atomic((MyPaintSurface*)g_surface);
            g_in_atomic = 1;
        }
        return NULL;
    }

    mypaint_fixed_tiled_surface_read_rgba8(g_surface, (unsigned char*)outBytes);
    (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

    // Optional: log stats occasionally
    // dump_surface_stats(g_surface, g_w, g_h);

    // Reopen atomic block if we had to close it to flush
    if (reopened) {
        mypaint_surface_begin_atomic((MyPaintSurface*)g_surface);
        g_in_atomic = 1;
    }

    return out;
}


