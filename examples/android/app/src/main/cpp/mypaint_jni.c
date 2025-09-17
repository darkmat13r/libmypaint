#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "mypaint-brush.h"
#include "mypaint-fixed-tiled-surface.h"

static void stroke_to(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
    float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
    float pressure = 1.0f, ytilt = 0.0f, xtilt = 0.0f, dtime = 1.0f/10.0f;
    gboolean linear = FALSE;
    mypaint_brush_stroke_to(brush, surf, x, y, pressure, xtilt, ytilt, dtime,
                            viewzoom, viewrotation, barrel_rotation, linear);
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_mypaint_MyPaintBridge_renderDemo(JNIEnv* env, jobject thiz,
                                                  jint width, jint height) {
    int w = width, h = height;

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
        mypaint_brush_unref(brush);
        mypaint_surface_unref((MyPaintSurface *)surface);
        return NULL;
    }
    jboolean isCopy = JNI_FALSE;
    jbyte* outBytes = (*env)->GetByteArrayElements(env, out, &isCopy);
    if (!outBytes) {
        mypaint_brush_unref(brush);
        mypaint_surface_unref((MyPaintSurface *)surface);
        return NULL;
    }

    // Fill the output buffer directly from the tiled surface as RGBA8
    mypaint_fixed_tiled_surface_read_rgba8(surface, (unsigned char*)outBytes);

    (*env)->ReleaseByteArrayElements(env, out, outBytes, 0);

    mypaint_brush_unref(brush);
    mypaint_surface_unref((MyPaintSurface *)surface);

    return out;
}
