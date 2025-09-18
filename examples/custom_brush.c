#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libmypaint.c"
#include "mypaint-fixed-tiled-surface.h"

static void stroke(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
  float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
  float pressure = 1.0f, ytilt = 0.0f, xtilt = 0.0f, dtime = 1.0f/30.0f;
  gboolean linear = FALSE;
  mypaint_brush_stroke_to(brush, surf, x, y, pressure, xtilt, ytilt, dtime,
                          viewzoom, viewrotation, barrel_rotation, linear);
}

static char *read_file(const char *path) {
  FILE *f = fopen(path, "rb");
  if (!f) return NULL;
  fseek(f, 0, SEEK_END);
  long len = ftell(f);
  fseek(f, 0, SEEK_SET);
  char *buf = (char*)malloc(len + 1);
  if (!buf) { fclose(f); return NULL; }
  size_t n = fread(buf, 1, len, f);
  fclose(f);
  buf[n] = '\0';
  return buf;
}

static void print_roi(const char* tag, const MyPaintRectangles* rois) {
  if (!rois || rois->num_rectangles <= 0 || !rois->rectangles) {
    fprintf(stderr, "%s: no ROI rectangles (nothing invalidated?)\n", tag);
    return;
  }
  for (int i = 0; i < rois->num_rectangles; ++i) {
    const MyPaintRectangle *r = &rois->rectangles[i];
    fprintf(stderr, "%s: ROI[%d] = x=%d y=%d w=%d h=%d\n", tag, i, r->x, r->y, r->width, r->height);
  }
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
  fprintf(stderr, "Surface stats: pixels=%dx%d, nonzero_alpha_pixels=%llu, alpha_avg=%.2f, alpha_min=%u, alpha_max=%u\n",
          w, h, nonzero, avgA, (unsigned)minA, (unsigned)maxA);
  free(buf);
}

int main(int argc, char **argv) {
  int w = 400, h = 200;
  fprintf(stderr, "custom_brush: starting, surface %dx%d\n", w, h);
  MyPaintFixedTiledSurface *surface = mypaint_fixed_tiled_surface_new(w, h);
  MyPaintBrush *brush = mypaint_brush_new();

  const char* env_print = getenv("MYPAINT_PRINT_INPUTS");
  if (env_print && strcmp(env_print, "0") != 0) {
    mypaint_brush_set_print_inputs(brush, TRUE);
    fprintf(stderr, "Input logging enabled (MYPAINT_PRINT_INPUTS=%s)\n", env_print);
  }

  // Option A: create a custom brush programmatically
  mypaint_brush_from_defaults(brush);
  // Tweak base values
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_RADIUS_LOGARITHMIC, 4.0f); // smaller radius
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_OPAQUE, 0.9f);
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_HARDNESS, 0.7f);
  // Different tip shape: make the dab elliptical and rotated
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_ELLIPTICAL_DAB_RATIO, 3.0f);
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_ELLIPTICAL_DAB_ANGLE, 45.0f);
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_H, 0.0f); // cyan-ish
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_S, 0.9f);
  mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_V, 0.95f);

  // Add a simple pressure→opacity mapping curve
  mypaint_brush_set_mapping_n(brush, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 2);
  mypaint_brush_set_mapping_point(brush, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 0, 0.0f, 0.05f);
  mypaint_brush_set_mapping_point(brush, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 1, 1.0f, 1.0f);

  fprintf(stderr, "Programmatic brush: drawing top row line of dabs...\n");
  if (!getenv("MYPAINT_PAPER_NOISE") || strcmp(getenv("MYPAINT_PAPER_NOISE"), "0") == 0) {
    fprintf(stderr, "Tip: enable experimental paper grain by setting MYPAINT_PAPER_NOISE=1 (strength via MYPAINT_PAPER_STRENGTH, default 0.5)\n");
  }
  // Draw something with the programmatic brush
  mypaint_surface_begin_atomic((MyPaintSurface*)surface);
  mypaint_brush_new_stroke(brush);
  int count_a = 0;
  for (int x = 20; x <= w-20; x += 8) {
    stroke(brush, (MyPaintSurface*)surface, (float)x, h*0.25f);
    count_a++;
  }
  MyPaintRectangle roi_a; MyPaintRectangles rois_a; rois_a.num_rectangles = 1; rois_a.rectangles = &roi_a;
  mypaint_surface_end_atomic((MyPaintSurface*)surface, &rois_a);
  fprintf(stderr, "Programmatic brush: dab_count=%d\n", count_a);
  print_roi("Programmatic brush", &rois_a);

  // Option B: load a custom brush from a JSON preset (file path passed as argv[1])
  if (argc > 1) {
    fprintf(stderr, "Loading preset: %s\n", argv[1]);
    char *json = read_file(argv[1]);
    if (json) {
      if (mypaint_brush_from_string(brush, json)) {
        fprintf(stderr, "Preset loaded OK. Overriding HSV to red.\n");
        // override color at runtime (example)
        mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_H, 0.0f); // red
        mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_S, 1.0f);
        mypaint_brush_set_base_value(brush, MYPAINT_BRUSH_SETTING_COLOR_V, 1.0f);

        mypaint_surface_begin_atomic((MyPaintSurface*)surface);
        mypaint_brush_new_stroke(brush);
        int count_b = 0;
        for (int x = 20; x <= w-20; x += 8) {
          stroke(brush, (MyPaintSurface*)surface, (float)x, h*0.75f);
          count_b++;
        }
        MyPaintRectangle roi_b; MyPaintRectangles rois_b; rois_b.num_rectangles = 1; rois_b.rectangles = &roi_b;
        mypaint_surface_end_atomic((MyPaintSurface*)surface, &rois_b);
        fprintf(stderr, "Preset brush: dab_count=%d\n", count_b);
        print_roi("Preset brush", &rois_b);
      } else {
        fprintf(stderr, "Failed to load brush JSON from %s\n", argv[1]);
      }
      free(json);
    } else {
      fprintf(stderr, "Could not read file: %s\n", argv[1]);
    }
  }

  dump_surface_stats(surface, w, h);
  fprintf(stderr, "Writing output to output.ppm\n");
  write_ppm(surface, "output.ppm");

  mypaint_brush_unref(brush);
  mypaint_surface_unref((MyPaintSurface*)surface);
  fprintf(stderr, "Done.\n");
  return 0;
}
