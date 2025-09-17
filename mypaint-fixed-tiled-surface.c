#include <stdlib.h>
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#include "config.h"

#if MYPAINT_CONFIG_USE_GLIB
#include <glib.h>
#endif

#include "mypaint-fixed-tiled-surface.h"


struct MyPaintFixedTiledSurface {
    MyPaintTiledSurface parent;

    size_t tile_size; // Size (in bytes) of single tile
    uint16_t *tile_buffer; // Stores tiles in a linear chunk of memory (16bpc RGBA)
    uint16_t *null_tile; // Single tile that we hand out and ignore writes to
    int tiles_width; // width in tiles
    int tiles_height; // height in tiles
    int width; // width in pixels
    int height; // height in pixels

};

void free_simple_tiledsurf(MyPaintSurface *surface);

void reset_null_tile(MyPaintFixedTiledSurface *self)
{
    memset(self->null_tile, 0, self->tile_size);
}

static void
tile_request_start(MyPaintTiledSurface *tiled_surface, MyPaintTileRequest *request)
{
    MyPaintFixedTiledSurface *self = (MyPaintFixedTiledSurface *)tiled_surface;

    const int tx = request->tx;
    const int ty = request->ty;

    uint16_t *tile_pointer = NULL;

    if (tx >= self->tiles_width || ty >= self->tiles_height || tx < 0 || ty < 0) {
        // Give it a tile which we will ignore writes to
        tile_pointer = self->null_tile;

    } else {
        // Compute the offset for the tile into our linear memory buffer of tiles
        size_t rowstride = self->tiles_width * self->tile_size;
        size_t x_offset = tx * self->tile_size;
        size_t tile_offset = (rowstride * ty) + x_offset;

        tile_pointer = self->tile_buffer + tile_offset/sizeof(uint16_t);
    }

    request->buffer = tile_pointer;
}

static void
tile_request_end(MyPaintTiledSurface *tiled_surface, MyPaintTileRequest *request)
{
    MyPaintFixedTiledSurface *self = (MyPaintFixedTiledSurface *)tiled_surface;

    const int tx = request->tx;
    const int ty = request->ty;

    if (tx >= self->tiles_width || ty >= self->tiles_height || tx < 0 || ty < 0) {
        // Wipe any changes done to the null tile
        reset_null_tile(self);
    } else {
        // We hand out direct pointers to our buffer, so for the normal case nothing needs to be done
    }
}

MyPaintSurface *
mypaint_fixed_tiled_surface_interface(MyPaintFixedTiledSurface *self)
{
    return (MyPaintSurface *)self;
}

int
mypaint_fixed_tiled_surface_get_width(MyPaintFixedTiledSurface *self)
{
    return self->width;
}

int
mypaint_fixed_tiled_surface_get_height(MyPaintFixedTiledSurface *self)
{
    return self->height;
}

MyPaintFixedTiledSurface *
mypaint_fixed_tiled_surface_new(int width, int height)
{
    assert(width > 0);
    assert(height > 0);

    MyPaintFixedTiledSurface *self = (MyPaintFixedTiledSurface *)malloc(sizeof(MyPaintFixedTiledSurface));

    mypaint_tiled_surface_init(&self->parent, tile_request_start, tile_request_end);

    const int tile_size_pixels = self->parent.tile_size;

    // MyPaintSurface vfuncs
    self->parent.parent.destroy = free_simple_tiledsurf;

    const int tiles_width = ceil((float)width / tile_size_pixels);
    const int tiles_height = ceil((float)height / tile_size_pixels);
    const size_t tile_size = tile_size_pixels * tile_size_pixels * 4 * sizeof(uint16_t);
    const size_t buffer_size = tiles_width * tiles_height * tile_size;

    assert(tile_size_pixels*tiles_width >= width);
    assert(tile_size_pixels*tiles_height >= height);
    assert(buffer_size >= width*height*4*sizeof(uint16_t));

    uint16_t * buffer = (uint16_t *)malloc(buffer_size);
    if (!buffer) {
        fprintf(stderr, "CRITICAL: unable to allocate enough memory: %zu bytes", buffer_size);
        free(self);
        return NULL;
    }
    memset(buffer, 255, buffer_size);

    self->tile_buffer = buffer;
    self->tile_size = tile_size;
    self->null_tile = (uint16_t *)malloc(tile_size);
    self->tiles_width = tiles_width;
    self->tiles_height = tiles_height;
    self->height = height;
    self->width = width;

    reset_null_tile(self);

    return self;
}

void free_simple_tiledsurf(MyPaintSurface *surface)
{
    MyPaintFixedTiledSurface *self = (MyPaintFixedTiledSurface *)surface;

    mypaint_tiled_surface_destroy(&self->parent);

    free(self->tile_buffer);
    free(self->null_tile);

    free(self);
}



void
mypaint_fixed_tiled_surface_read_rgba8(MyPaintFixedTiledSurface *self, unsigned char *out_rgba8)
{
    if (!self || !out_rgba8) {
        return;
    }
    const int width = self->width;
    const int height = self->height;
    const int tile_size_pixels = self->parent.tile_size;

    const size_t rowstride_bytes = (size_t)self->tiles_width * self->tile_size; // bytes per tile row

    for (int y = 0; y < height; ++y) {
        const int ty = y / tile_size_pixels;
        const int y_in_tile = y % tile_size_pixels;
        for (int x = 0; x < width; ++x) {
            const int tx = x / tile_size_pixels;
            const int x_in_tile = x % tile_size_pixels;

            const size_t x_offset = (size_t)tx * self->tile_size; // bytes
            const size_t tile_offset_bytes = (size_t)ty * rowstride_bytes + x_offset; // bytes
            const uint16_t *tile = self->tile_buffer + tile_offset_bytes / sizeof(uint16_t);

            const size_t pixel_index_in_tile = ((size_t)y_in_tile * (size_t)tile_size_pixels + (size_t)x_in_tile) * 4u;

            const uint16_t r16 = tile[pixel_index_in_tile + 0];
            const uint16_t g16 = tile[pixel_index_in_tile + 1];
            const uint16_t b16 = tile[pixel_index_in_tile + 2];
            const uint16_t a16 = tile[pixel_index_in_tile + 3];

            const size_t out_idx = ((size_t)y * (size_t)width + (size_t)x) * 4u;
            out_rgba8[out_idx + 0] = (unsigned char)(r16 >> 8);
            out_rgba8[out_idx + 1] = (unsigned char)(g16 >> 8);
            out_rgba8[out_idx + 2] = (unsigned char)(b16 >> 8);
            out_rgba8[out_idx + 3] = (unsigned char)(a16 >> 8);
        }
    }
}
