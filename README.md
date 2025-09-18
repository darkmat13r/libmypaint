# libmypaint - MyPaint brush engine library

[![Translation status](https://hosted.weblate.org/widgets/mypaint/-/libmypaint/svg-badge.svg)](https://hosted.weblate.org/engage/mypaint/?utm_source=widget)
[![Travis Build Status](https://travis-ci.org/mypaint/libmypaint.svg?branch=master)](https://travis-ci.org/mypaint/libmypaint)
[![Appveyor Build Status](https://ci.appveyor.com/api/projects/status/github/mypaint/libmypaint?branch=master&svg=true)](https://ci.appveyor.com/project/jonnor/libmypaint)

This is the brush library used by MyPaint. A number of other painting
programs use it too.

License: ISC, see [COPYING](./COPYING) for details.

If you just want concise, copy/paste-ready build instructions for common platforms (Linux, macOS, Windows), see BUILDING.md.

If you prefer a literal, step-by-step "from scratch" walkthrough with copy/paste commands, see doc/STEP_BY_STEP_FROM_SCRATCH.md.

For Android cross-compilation and app integration via JNI, see doc/ANDROID.md.

If you are new to C/C++ build systems (make, Autotools, CMake), read the beginner-friendly tutorial: doc/BUILD_SYSTEMS_TUTORIAL.md.

## Dependencies

* All configurations and builds:
  - [json-c](https://github.com/json-c/json-c/wiki) (>= 0.11)
  - C compiler, `make` etc.
* Most configurations (all except `--disable-introspection --without-glib`):
  - [GObject-Introspection](https://live.gnome.org/GObjectIntrospection)
  - [GLib](https://wiki.gnome.org/Projects/GLib)
* When building from `git` (developer package names vary by distribution):
  - [Python](http://python.org/)
  - [autotools](https://en.wikipedia.org/wiki/GNU_Build_System)
  - [intltool](https://freedesktop.org/wiki/Software/intltool/)
  - [gettext](https://www.gnu.org/software/gettext/gettext.html)
* For `--enable-gegl` (GIMP *does not* require this):
  - [GEGL + BABL](http://gegl.org/)

For an overview of GObject Introspection and how it applies to libmypaint, see doc/GOBJECT_INTROSPECTION.md.

### Install dependencies (Debian and derivatives)

On recent Debian-like systems, you can type the following
to get started with a standard configuration:

    # apt install -y build-essential
    # apt install -y libjson-c-dev libgirepository1.0-dev libglib2.0-dev

When building from git:

    # apt install -y python autotools-dev intltool gettext libtool
    
You might also try using your package manager:

    # apt build-dep mypaint # will get additional deps for MyPaint (GUI)
    # apt build-dep libmypaint  # may not exist; included in mypaint

### Install dependencies (Red Hat and derivatives)

The following works on a minimal CentOS 7 installation:

    # yum install -y gcc gobject-introspection-devel json-c-devel glib2-devel

When building from git, you'll want to add:

    # yum install -y git python autoconf intltool gettext libtool
    
You might also try your package manager:

    # yum builddep libmypaint

### Install dependencies (OpenSUSE)

Works with a fresh OpenSUSE Tumbleweed Docker image:

    # zypper install gcc13 gobject-introspection-devel libjson-c-devel glib2-devel

When building from git:

    # zypper install git python311 autoconf intltool gettext-tools libtool

Package manager:

    # zypper install libmypaint0

## Build and install

MyPaint and libmypaint benefit from autovectorization and other compiler optimizations.
If you want to set custom CFLAGS, prefer portable flags unless you know your compiler supports vendor‑specific options.

    # Safe example (GCC or Clang):
    $ export CFLAGS='-O3 -march=native -mtune=native -ffast-math'
    
    # GCC‑only extras (do NOT use with Apple Clang):
    # export CFLAGS='-O3 -march=native -mtune=native -ffast-math -ftree-vectorize'

Note: Passing unsupported flags (for example, GCC‑specific flags like -fopt-info-vec-optimized or -funsafe-loop-optimizations to Apple Clang on macOS) can cause ./configure to fail with "C compiler cannot create executables". If that happens, unset custom flags and try again:

    $ unset CFLAGS CXXFLAGS LDFLAGS

The traditional setup works just fine.

    $ ./autogen.sh    # Only needed when building from git.
    $ ./configure
    # make install
    # ldconfig

### Maintainer mode

We don't ship a `configure` script in our git repository. If you're
building from git, you have to kickstart the build environment with:

    $ git clone https://github.com/mypaint/libmypaint.git
    $ cd libmypaint
    $ ./autogen.sh

This script generates `configure` from `configure.ac`, after running a
few checks to make sure your build environment is broadly OK. It also
regenerates certain important generated headers if they need it.

Folks building from a release tarball don't need to do this: they will
have a `configure` script from the start.

### Configure

    $ ./configure
    $ ./configure --prefix=/tmp/junk/example

There are several MyPaint-specific options.
These can be shown by running

    $ ./configure --help

### Build

    $ make

Once MyPaint is built, you can run the test suite and/or install it.

### Test

    $ make check

This runs all the unit tests.

### Install

    # make install

Uninstall libmypaint with `make uninstall`.

### Check availability

Make sure that pkg-config can see libmypaint before trying to build with it.

    $ pkg-config --list-all | grep -i mypaint

If it's not found, you'll need to add the relevant pkgconfig directory to
the `pkg-config` search path. For example, on CentOS, with a default install:

    # sh -c "echo 'PKG_CONFIG_PATH=/usr/local/lib/pkgconfig' >>/etc/environment"

Make sure ldconfig can see libmypaint as well

    # ldconfig -p |grep -i libmypaint

If it's not found, you'll need to add the relevant lib directory to
the LD_LIBRARY_PATH:
    
    $ export LD_LIBRARY_PATH=/usr/local/lib
    # sh -c "echo 'LD_LIBRARY_PATH=/usr/local/lib' >>/etc/environment

Alternatively, you may want to enable /usr/local for libraries.  Arch and Redhat derivatives:

    # sh -c "echo '/usr/local/lib' > /etc/ld.so.conf.d/usrlocal.conf"
    # ldconfig

### Run the examples

There are a couple of small, self-contained examples in the examples/ directory. They render to a PPM file (output.ppm).

Quick way (no install required):

    $ ./autogen.sh   # only when building from git
    $ ./configure
    $ make
    $ sh examples/run-minimal.sh

Option A: Build and run directly from the source tree (manual compile, no install required)

1) Prepare the build tree (needed to generate headers like config.h and mypaint-config.h):

    $ ./autogen.sh   # only when building from git
    $ ./configure
    $ make

2) Compile the minimal example as a single translation unit that pulls in libmypaint sources (fast and simple):

- Linux/macOS (Clang or GCC):

    $ cc -I. -I./fastapprox -o examples/minimal examples/minimal.c $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

3) Run it:

    $ ./examples/minimal
    Writing output
    $ ls examples/output.ppm

Then open output.ppm with an image viewer, or convert it with ImageMagick:

    $ convert examples/output.ppm examples/output.png

Notes:
- The examples include the file examples/libmypaint.c, which compiles the library sources directly; you do not need to link against an installed libmypaint for Option A.
- Ensure pkg-config finds glib-2.0 and json-c; see "Check availability" above if it does not.

Option B: Use an installed libmypaint via pkg-config

1) Install libmypaint (system-wide or to a prefix) and ensure pkg-config can find it (see above).
2) Compile your own program that includes libmypaint headers and links to the installed library. For example, for a file myprog.c:

    $ cc -o myprog myprog.c $(pkg-config --cflags --libs libmypaint-2.0)

On Windows (MSYS2/MinGW):

- Install dependencies:

    pacman -S --needed mingw-w64-x86_64-toolchain mingw-w64-x86_64-json-c mingw-w64-x86_64-glib2

- Then from the MSYS2 MinGW64 shell, inside the source tree after configure && make:

    cc -I. -I./fastapprox -o examples/minimal examples/minimal.c $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

If you hit compiler flag errors, unset custom CFLAGS/CXXFLAGS as noted earlier in this README.

## Contributing

The MyPaint project welcomes and encourages participation by everyone.
We want our community to be skilled and diverse,
and we want it to be a community that anybody can feel good about joining.
No matter who you are or what your background is, we welcome you.

Please note that MyPaint is released with a
[Contributor Code of Conduct](CODE_OF_CONDUCT.md).
By participating in this project you agree to abide by its terms.

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)
for details of how you can begin contributing.

## Making releases

The distribution release can be generated with:

    $ make dist

And it should be checked before public release with:

    $ make distcheck

## Localization

Contribute translations here: <https://hosted.weblate.org/engage/mypaint/>.

The list of languages is maintained in [po/LINGUAS](po/LINGUAS).
Currently this file lists all the languages we have translations for.
It can be regenerated with:

    $ ls po/*.po | sed 's$^.*po/\([^.]*\).po$\1$' | sort > po/LINGUAS

You can also disable languages by removing them from the list if needed.

A list of files where localizable strings can be found is maintained
in `po/POTFILES.in`.

### Strings update

You can update the .po files when translated strings in the code change
using:

    $ cd po && make update-po

When the results of this are pushed, Weblate translators will see the
new strings immediately.

## Documentation

Further documentation can be found in the libmypaint wiki:
<https://github.com/mypaint/libmypaint/wiki>.


## Software using libmypaint

* [MyPaint](https://github.com/mypaint/mypaint)
* [GIMP](https://gitlab.gnome.org/GNOME/gimp/)
* [OpenToonz](https://github.com/opentoonz/opentoonz/)
* [enve](https://github.com/MaurycyLiebner/enve/)


### Building with a local libs/ folder

If all required third‑party libraries are bundled locally in a libs/ directory at the repository root, you can build and run the examples without installing system packages.

Expected layout (typical):
- libs/include        # headers (glib-2.0, gobject, json-c, etc.)
- libs/lib            # libraries (.so/.dylib/.a) and pkgconfig dir
- libs/lib/pkgconfig  # *.pc files
- libs/bin            # Windows DLLs (if any)

Linux/macOS (Clang or GCC):

    # From the repository root
    $ export PKG_CONFIG_PATH="$PWD/libs/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
    $ export CPPFLAGS="-I$PWD/libs/include ${CPPFLAGS}"
    $ export LDFLAGS="-L$PWD/libs/lib ${LDFLAGS}"
    # For running (pick the one that applies):
    $ export LD_LIBRARY_PATH="$PWD/libs/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"      # Linux
    $ export DYLD_LIBRARY_PATH="$PWD/libs/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"  # macOS

    # Build tree prep (if not done yet):
    $ ./autogen.sh   # only when building from git
    $ ./configure
    $ make

    # Compile minimal example using the libs/ folder
    $ cc -I. -I./fastapprox -o examples/minimal examples/minimal.c \
         $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

    # Optional: embed an rpath so the binary finds libs/ at runtime
    # Linux:
    # $ cc -I. -I./fastapprox -Wl,-rpath,'$ORIGIN/../libs/lib' -o examples/minimal examples/minimal.c \
    #      $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm
    # macOS:
    # $ cc -I. -I./fastapprox -Wl,-rpath,@loader_path/../libs/lib -o examples/minimal examples/minimal.c \
    #      $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

Explanation of the cc command above (and the variant shown in the issue):

- cc: the system C compiler driver (Clang or GCC). You can also use clang or gcc explicitly.
- -I. -I./fastapprox: add the current source tree and the fastapprox/ directory to the header search path so includes in libmypaint.c and others resolve.
- -Wl,-rpath,'$ORIGIN/../libs/lib' (Linux example): pass an option to the linker (via -Wl,) to embed a runtime library search path (rpath) into the binary. $ORIGIN expands at runtime to the directory of the executable, so this makes ./examples/minimal look for shared libraries in ../libs/lib next to the repo. This lets you run without setting LD_LIBRARY_PATH. On macOS use -Wl,-rpath,@loader_path/../libs/lib instead. On Windows you typically add libs/bin to PATH.
- -o examples/minimal: write the output executable to examples/minimal.
- examples/minimal.c: the example program source. It includes examples/libmypaint.c, which in turn #includes the library sources, so you don’t need a separately installed libmypaint for this in-tree build.
- $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c): pkg-config prints the correct compiler and linker flags for GLib, GObject, and json-c (e.g., -I, -D, -L, and -l options). These are required because libmypaint uses GLib/GObject types and json-c. If you’re using a local libs/ directory, ensure PKG_CONFIG_PATH includes libs/lib/pkgconfig so pkg-config can find these .pc files.
- -lm: link the math library, used by libmypaint.

Notes:
- In the README, the rpath variants are commented (#) to show them as optional examples. To actually use rpath, remove the leading # characters before the rpath lines.
- If you don’t embed an rpath, set LD_LIBRARY_PATH (Linux) or DYLD_LIBRARY_PATH (macOS) so the program can find the shared libraries at runtime.

Beginner cheat sheet: what each segment means

- cc: the system C compiler driver. Usually points to clang or gcc. You can also call clang or gcc directly.
- Source files (e.g., examples/minimal.c): the C files to compile. In our example minimal.c includes examples/libmypaint.c, which pulls in the libmypaint sources.
- -Ipath: add a directory to the header include search path. Example: -I. searches the current directory; -I./fastapprox searches the fastapprox directory.
- -o output: name of the output file. For a final program, this is the executable path (e.g., -o examples/minimal). When used with -c, it names the object file (e.g., foo.o).
- -c: compile only; do not link. Produces an object file (.o). Not used in the one‑line example because we compile and link in one step.
- -Lpath: add a directory to the library search path for the linker (where to look for lib*.so/.dylib/.a at link time).
- -lname: link with library libname (e.g., -lm links libm, the math library; -ljson-c links libjson-c). The linker searches -L paths and system defaults.
- -DNAME[=VALUE]: define a preprocessor symbol (e.g., -DNDEBUG). pkg-config may emit -D flags when needed.
- -O2/-O3: enable compiler optimizations (higher number usually means more aggressive optimization). Controlled via CFLAGS.
- -g: include debug information in the output for debuggers like gdb or lldb.
- -Wall -Wextra: enable common and extra compiler warnings to catch mistakes early.
- $(pkg-config --cflags --libs ...): runs pkg-config to print the right compiler (-I, -D) and linker (-L, -l) flags for listed packages (e.g., gobject-2.0 glib-2.0 json-c). You can echo this separately to see what it expands to.
- -Wl,<option>: pass an option directly to the linker (ld). Example: -Wl,-rpath,... below.
- -Wl,-rpath,<dir>: embed a runtime library search path (“rpath”) in the executable so it can find shared libraries at run time without setting environment variables. Use $ORIGIN on Linux (expands to the executable’s directory) and @loader_path on macOS.
- $ORIGIN / @loader_path: special placeholders expanded at run time to the directory of the executable (Linux) or the loader path (macOS). Lets you point rpath relative to the binary, e.g., $ORIGIN/../libs/lib.
- Environment variables you may see:
  - CPPFLAGS: preprocessor and general include flags (e.g., -I... -D...).
  - CFLAGS: compiler flags (e.g., -O3 -g -Wall).
  - LDFLAGS: linker flags (e.g., -L... -Wl,-rpath,...).
  - PKG_CONFIG_PATH: where pkg-config should look for .pc files.
  - LD_LIBRARY_PATH (Linux), DYLD_LIBRARY_PATH (macOS), PATH (Windows): where the OS looks for shared libraries/DLLs at run time if rpath is not set.

Tips for beginners
- To see what pkg-config prints, run: pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c
- To see the compile and link steps verbosely, add -v to cc.
- If you get “undefined reference” errors, you’re likely missing a -l flag or the corresponding -L path.
- If headers aren’t found, you’re missing -I flags or the packages themselves.

 Windows (MSYS2/MinGW):

    # In MSYS2 MinGW64 shell, from repo root
    $ export PKG_CONFIG_PATH="/c/path/to/your/repo/libs/lib/pkgconfig:$PKG_CONFIG_PATH"
    $ export CPPFLAGS="-I/c/path/to/your/repo/libs/include ${CPPFLAGS}"
    $ export LDFLAGS="-L/c/path/to/your/repo/libs/lib ${LDFLAGS}"
    # Ensure DLLs are discoverable when running:
    $ export PATH="/c/path/to/your/repo/libs/bin:$PATH"

    # After ./configure && make in the source tree:
    $ cc -I. -I./fastapprox -o examples/minimal examples/minimal.c \
         $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

Notes:
- Using PKG_CONFIG_PATH is preferred if your libs/lib/pkgconfig contains glib-2.0, gobject-2.0, and json-c .pc files; pkg-config will output the correct include and link flags.
- If you don’t have .pc files, replace the pkg-config part with explicit flags, for example:

      $ cc -I. -I./fastapprox -I$PWD/libs/include \
           -L$PWD/libs/lib -o examples/minimal examples/minimal.c \
           -lgobject-2.0 -lglib-2.0 -ljson-c -lm

- When not embedding rpath, remember to set LD_LIBRARY_PATH (Linux), DYLD_LIBRARY_PATH (macOS), or PATH (Windows) before running ./examples/minimal.



## Android demo app (CMake + JNI)
An Android Studio sample project is included under examples/android that demonstrates using libmypaint from a small JNI library and rendering a simple brush stroke to a Bitmap.

- Build libmypaint for Android as described in doc/ANDROID.md and place the resulting libmypaint.so into examples/android/app/src/main/jniLibs/<ABI>/.
- Open examples/android in Android Studio and build/run on a device or emulator.

This is a minimal example intended for learning and quick validation that your cross‑compiled libmypaint works on Android.


## Creating a custom brush

There are two common ways to create a custom brush with libmypaint:

1) Programmatically, by setting base values and input mappings
- Create a MyPaintBrush, initialize defaults, then tweak base values and add mappings for inputs like pressure, speed, tilt, etc.
- Core APIs:
  - mypaint_brush_from_defaults(), mypaint_brush_set_base_value(), mypaint_brush_set_mapping_n(), mypaint_brush_set_mapping_point()
  - Settings and inputs enums: see mypaint-brush-settings.h (generated header lists MYPAINT_BRUSH_SETTING_* and MYPAINT_BRUSH_INPUT_*)

Minimal example (also see examples/custom_brush.c):

    MyPaintBrush *b = mypaint_brush_new();
    mypaint_brush_from_defaults(b);
    // Base parameters
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_RADIUS_LOGARITHMIC, -1.0f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_OPAQUE, 0.9f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_HARDNESS, 0.7f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_COLOR_H, 0.6f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_COLOR_S, 0.9f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_COLOR_V, 0.95f);
    // Pressure → opacity mapping (2-point linear)
    mypaint_brush_set_mapping_n(b, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 2);
    mypaint_brush_set_mapping_point(b, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 0, 0.0f, 0.05f);
    mypaint_brush_set_mapping_point(b, MYPAINT_BRUSH_SETTING_OPAQUE, MYPAINT_BRUSH_INPUT_PRESSURE, 1, 1.0f, 1.0f);

2) From a JSON preset (.myb file)
- Brush presets are JSON with a top-level "version" and a "settings" object. Each setting has a base_value and optional inputs mapping.
- Load from a string with mypaint_brush_from_string(). To load from a file, read it into memory first.

Minimal preset example (save as my_custom.myb):

    {
      "settings": {
        "radius_logarithmic": { "base_value": -1.0, "inputs": {} },
        "opaque": {
          "base_value": 0.9,
          "inputs": { "pressure": [[0.0, 0.05], [1.0, 1.0]] }
        },
        "hardness": { "base_value": 0.7, "inputs": {} },
        "color_h": { "base_value": 0.6, "inputs": {} },
        "color_s": { "base_value": 0.9, "inputs": {} },
        "color_v": { "base_value": 0.95, "inputs": {} }
      },
      "version": 3
    }

Then in C:

    char *json = read_file("my_custom.myb");
    MyPaintBrush *b = mypaint_brush_new();
    if (!mypaint_brush_from_string(b, json)) { /* handle error */ }

Tips
- All setting and input canonical names (as used in JSON) are listed in brushsettings.json. The corresponding C enums and documentation are in mypaint-brush-settings.h.
- You can override preset values at runtime with mypaint_brush_set_base_value() or change curves with mypaint_brush_set_mapping_point().
- Start simple: adjust radius_logarithmic, opaque, hardness, and color_*; add a pressure curve. Then iterate.

See also
- examples/minimal.c for a tiny drawing program.
- examples/custom_brush.c for a focused custom-brush demo (programmatic and JSON-based).

## Paper grain (experimental)

You can enable a simple procedural “paper grain” that modulates per‑pixel dab opacity during blending. This is off by default and only intended for experiments and examples; host applications typically implement paper textures themselves.

- Enable: set environment variable MYPAINT_PAPER_NOISE=1
- Strength: optional MYPAINT_PAPER_STRENGTH in range [0..1], default 0.5
- How it works: a fast hash‑based noise of screen‑space pixel coordinates multiplies each dab’s opacity. This is applied inside the software renderer and does not change the public API. It is deterministic and thread‑safe. When disabled (default), behavior is identical to upstream.
- Try it quickly with the example:

    $ MYPAINT_PAPER_NOISE=1 MYPAINT_PAPER_STRENGTH=0.6 ./examples/minimal

Notes:
- This feature is experimental and primarily for demonstration. Production apps should provide their own paper/grain system so it can be configured per‑layer and rendered with the host’s pipeline.
- Tests and default behavior are unaffected when the env var is unset.

## Different tip shapes

Libmypaint supports non‑circular dabs via the elliptical dab settings. In code or presets, adjust:
- elliptical_dab_ratio (ratio > 1.0 makes an ellipse)
- elliptical_dab_angle (degrees)

In C (see examples/custom_brush.c):

    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_ELLIPTICAL_DAB_RATIO, 3.0f);
    mypaint_brush_set_base_value(b, MYPAINT_BRUSH_SETTING_ELLIPTICAL_DAB_ANGLE, 45.0f);

You can also map angle to direction for dynamic orientation.

## FAQ

- Does libmypaint use OpenGL (or the GPU) for brush rendering?
  - No. libmypaint is a CPU-based brush engine. It renders brush dabs and blends them into client-provided tile surfaces in software. There are no OpenGL/Direct3D/Vulkan calls in the library. The tiled software renderer lives in mypaint-tiled-surface.c and related files; integrations are expected to upload the resulting pixels to the screen using whatever technology they prefer (which may be GPU-based) in the host application.
  - An optional build flag `--enable-gegl` uses GEGL/BABL for some operations, but this is still CPU-side within libmypaint. GPU/display acceleration, if any, is handled by the embedding application (e.g., MyPaint, Krita, GIMP) rather than by libmypaint itself.
  - The PERFORMANCE document discusses possible future ideas involving OpenCL/OpenGL interoperability, but those are design notes and not implemented in libmypaint.
