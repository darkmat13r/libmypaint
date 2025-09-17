# GObject Introspection (GI) and libmypaint

This page answers “What is GObject Introspection?” and explains how it relates to libmypaint: how to build it, what artifacts are generated, and how to consume libmypaint from dynamic languages like Python or JavaScript via GI.

## What is GObject Introspection?
GObject Introspection (GI) is a system in the GNOME/GLib ecosystem that:
- Scans C libraries that use the GObject/GLib type system,
- Generates a machine‐readable description of their API (.gir, then .typelib),
- Lets dynamic language bindings (Python via PyGObject, JavaScript via GJS, etc.) call into those C libraries at runtime without writing manual bindings.

In short: GI makes C libraries “self‑describing” so higher‑level languages can use them.

Resources:
- Project page: https://gi.readthedocs.io/
- GLib and GObject: https://docs.gtk.org/glib/

## How libmypaint uses (or can use) GI
libmypaint is a C library. It can be built in two modes:
- With GLib + GObject Introspection enabled (default on desktop): produces .gir and .typelib for the libmypaint API that is based on GLib types where applicable.
- Without GLib and without GI (flags: `--without-glib --disable-introspection`): smaller, pure‑C build (useful for constrained targets like Android). No .gir/.typelib is produced.

Note: Some libmypaint APIs are plain C and do not rely on GObject classes, but GI still helps expose the available functions/structs that have GI‑friendly annotations.

## Build options
Show all options: `./configure --help`

Common toggles:
- `--disable-introspection` — do not generate GI metadata.
- `--without-glib` — build a minimal core without GLib (typically also implies no GI).
- `--enable-gegl` — optional GEGL support (not required by GIMP).

Typical desktop build (GI enabled):
```
# Install deps first (see BUILDING.md for your platform)
./autogen.sh    # only when building from git
./configure     # detects gobject-introspection and glib
make -j$(nproc)
# make install   # optional, installs .gir/.typelib alongside the library
```

Disable GI explicitly:
```
./configure --disable-introspection
```

Minimal build without GLib and GI (e.g., for Android):
```
./configure --without-glib --disable-introspection
```

## What artifacts are produced?
When GI is enabled and found at configure time, the build generates:
- A GIR file: `MyPaint-2.0.gir` (XML description of the API)
- A typelib: `MyPaint-2.0.typelib` (binary, consumed at runtime by GI loaders)

You may see these in the build or install prefix. Some distros ship them in:
- `/usr/share/gir-1.0/` (GIR)
- `/usr/lib/girepository-1.0/` or `/usr/lib64/girepository-1.0/` (typelib)

This repository also includes these files in the root for convenience in some setups.

## Using libmypaint via GI
### Python (PyGObject)
PyGObject uses GI to load typelibs at runtime.

Prerequisites:
- Install PyGObject and GObject Introspection runtime for your OS.
- Ensure `MyPaint-2.0.typelib` is in GI’s search path (e.g., `/usr/lib/girepository-1.0/`). If using a custom prefix, set `GI_TYPELIB_PATH`.

Minimal Python example:
```python
# Requires GI typelibs for GLib and MyPaint to be discoverable
import gi
# The namespace and version come from MyPaint-2.0.typelib
gi.require_version('MyPaint', '2.0')
from gi.repository import MyPaint

# Create brush and surface; names follow the GI-exposed API
brush = MyPaint.Brush.new()
MyPaint.brush_from_defaults(brush)
MyPaint.brush_set_base_value(brush, MyPaint.BrushSetting.COLOR_H, 0.0)
MyPaint.brush_set_base_value(brush, MyPaint.BrushSetting.COLOR_S, 1.0)
MyPaint.brush_set_base_value(brush, MyPaint.BrushSetting.COLOR_V, 1.0)

surf = MyPaint.FixedTiledSurface.new(300, 150)

MyPaint.surface_begin_atomic(surf)
# call stroke_to helper in your own Python, or directly bind the C function if exposed
MyPaint.brush_stroke_to(brush, surf, 60.0, 30.0, 1.0, 0.0, 0.0, 0.1, 1.0, 0.0, 0.0, False)
MyPaint.surface_end_atomic(surf, None)

# Depending on the API available via GI, you can extract pixel data similarly to the C example.
```
Notes:
- Exact names depend on GI annotations and how the symbols are exported. Use `gi.inspect` tools (`from gi.repository import MyPaint; dir(MyPaint)`) to explore available members.
- Not all C helpers used by examples/minimal.c will appear identically; in some cases you may need to wrap convenience functions in your app.

### JavaScript (GJS)
GJS (the GNOME JavaScript engine) can also load the typelib.
```js
const MyPaint = imports.gi.MyPaint;

let brush = MyPaint.Brush.new();
MyPaint.brush_from_defaults(brush);
let surface = MyPaint.FixedTiledSurface.new(300, 150);

MyPaint.surface_begin_atomic(surface);
MyPaint.brush_stroke_to(brush, surface, 60.0, 30.0, 1.0, 0.0, 0.0, 0.1, 1.0, 0.0, 0.0, false);
MyPaint.surface_end_atomic(surface, null);
```

## Runtime environment variables
- `GI_TYPELIB_PATH` — additional directories to search for `.typelib` files.
- `GI_GIR_PATH` — additional directories to search for `.gir` files (mainly for development; loaders use typelibs).

Example (custom prefix):
```
export GI_TYPELIB_PATH="/opt/libmypaint/lib/girepository-1.0:$GI_TYPELIB_PATH"
```

## Troubleshooting
- configure: “GObject Introspection not found”
  - Install `gobject-introspection` development packages (see BUILDING.md for distro‑specific commands).
  - On macOS/Homebrew: `brew install gobject-introspection` and ensure gettext/glib are on PATH/PKG_CONFIG_PATH as in BUILDING.md.
- Python cannot import MyPaint
  - Ensure the `MyPaint-2.0.typelib` is in GI_TYPELIB_PATH or installed in the standard directory.
  - Verify versions: `python3 -c "import gi, sys; gi.require_version('MyPaint','2.0'); from gi.repository import MyPaint; print('OK')"`
- Symbol or API missing via GI
  - Not every C symbol is automatically introspectable; only those exported and annotated as GI‑friendly will show. Consider contributing annotations or wrapper functions if needed.
- Cross‑compiling/Android
  - We recommend disabling GI for Android (`--disable-introspection --without-glib`), as typical Android apps use JNI bindings instead. See doc/ANDROID.md.

## Summary
- GObject Introspection lets dynamic languages call libmypaint without writing bindings.
- Enable it during configure to generate MyPaint-2.0.gir/typelib.
- Use PyGObject/GJS to consume the API; ensure typelibs are discoverable at runtime.
- For constrained platforms or where GI isn’t available, disable introspection and use the C API directly (or JNI on Android).