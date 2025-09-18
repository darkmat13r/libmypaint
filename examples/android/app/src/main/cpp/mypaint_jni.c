#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "mypaint-brush.h"
#include "mypaint-fixed-tiled-surface.h"
#include "mypaint_log.h"

static void stroke_to(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
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
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, 4*w/5.0f, h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, 4*w/5.0f, 4*h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, 4*h/5.0f);
    stroke_to(brush, (MyPaintSurface*)surface, w/5.0f, h/5.0f);
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


