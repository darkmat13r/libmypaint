# Beginner’s Tutorial: Building C/C++ Projects with make, Autotools (autoconf/automake), and CMake

This tutorial explains, from a beginner’s perspective, how common C/C++ build tools work and how they apply to libmypaint. You’ll learn:
- What make is and how to use it
- What Autotools (autoconf, automake, libtool) are and why libmypaint uses them
- How to use CMake (and how to consume libmypaint from your own CMake project)
- Practical, copy/paste examples

If you only need quick commands to build libmypaint, see BUILDING.md. If you are curious about how things fit together, read on.

---

## 1) make: the build runner

- make is a program that reads a file named Makefile. The Makefile lists "targets" (things to build), their dependencies, and the commands to build them.
- You run it from a terminal: `make` (build default target) or `make <target>`.

A tiny Makefile example:
```
# file: Makefile
CC = cc
CFLAGS = -O2 -Wall

# Target to build an executable from one C file
hello: hello.c
	$(CC) $(CFLAGS) -o hello hello.c

# A convenience target that cleans build artifacts
clean:
	rm -f hello
```
Usage:
```
make       # builds ./hello
./hello    # run it
make clean
```

Key ideas:
- Targets depend on files; if sources change, make knows what to rebuild.
- Variables (like CC, CFLAGS) let you customize commands.
- There’s no magic – the Makefile’s rules are just shell commands.

How this applies to libmypaint:
- This project uses Autotools (see next section) to generate the final Makefiles automatically. After `./configure`, you get portable Makefiles and can simply run `make`, `make install`, `make check`, etc.

---

## 2) Autotools (autoconf, automake, libtool)

Autotools is a classic build system on Unix-like OSes.
- autoconf turns `configure.ac` into a `configure` shell script that probes your system (compilers, libraries, headers).
- automake turns `Makefile.am` into portable `Makefile.in`, and finally into `Makefile` when `configure` runs.
- libtool abstracts differences when building shared/static libraries on many platforms.

Why use Autotools?
- Portability: the generated `configure` + Makefiles work on many Unix-like systems.
- Integration: common commands like `make`, `make install`, `make uninstall`, `make check`, `make dist` are standardized.

Typical workflow in this repository:
1) If you built from a release tarball (it already contains `configure`):
```
./configure       # checks your system and generates Makefiles
make              # compiles
sudo make install # installs to /usr/local by default
```
2) If you built from a git clone (no configure script yet):
```
./autogen.sh      # generates configure and related files
./configure
make
sudo make install
```

Useful options for configure:
- `--prefix=/some/path` to choose where `make install` will put files (headers, libraries, pkg-config files). Example:
  `./configure --prefix="$HOME/.local"`
- `--disable-introspection` to skip GObject introspection
- `--without-glib` to build the pure C core without GLib
- `--enable-gegl` to enable optional GEGL/BABL support
- Show all options: `./configure --help`

Out-of-tree builds (keep sources clean):
```
mkdir build && cd build
../autogen.sh   # or: ../configure if using a release tarball
../configure --prefix=$HOME/.local
make
make install
```

Common make targets (after configure):
- `make` – build
- `make -j$(nproc)` – build with parallel jobs
- `make check` – run unit tests
- `make install` – install the library and headers
- `make uninstall` – uninstall what was installed
- `make dist` – create a release tarball
- `make distcheck` – build a tarball and verify it builds/installs cleanly in a temp directory

Troubleshooting tips:
- "C compiler cannot create executables": often caused by invalid CFLAGS/CXXFLAGS. Try `unset CFLAGS CXXFLAGS LDFLAGS` and re-run configure.
- Missing dependencies: install development packages for json-c, glib, gobject-introspection as needed (see README.md / BUILDING.md per platform).

---

## 3) CMake: a cross-platform build system

CMake generates native build files (Unix Makefiles, Ninja, Visual Studio solutions, Xcode projects). It is very popular for C/C++.

Two ways you’ll use CMake in the context of libmypaint:
1) To build your own small program that links against an installed libmypaint.
2) Optionally, to build your own JNI or add-on libraries that depend on libmypaint (e.g., in Android or desktop apps).

Note: libmypaint itself is maintained with Autotools. You’ll still use `./configure && make` to build and install it. CMake is for your consumer projects.

### 3.1 Minimal CMake project using libmypaint

Assume you installed libmypaint and pkg-config can find it (`pkg-config --cflags --libs libmypaint-2.0`). Create this structure:
```
myclient/
  CMakeLists.txt
  main.c
```

main.c (draws a simple line using libmypaint types):
```c
#include <stdio.h>
#include <mypaint-brush.h>
#include <mypaint-fixed-tiled-surface.h>

static void stroke_to(MyPaintBrush *brush, MyPaintSurface *surf, float x, float y) {
    float viewzoom = 1.0f, viewrotation = 0.0f, barrel_rotation = 0.0f;
    float pressure = 1.0f, ytilt = 0.0f, xtilt = 0.0f, dtime = 1.0f/10.0f;
    gboolean linear = FALSE;
    mypaint_brush_stroke_to(brush, surf, x, y, pressure, xtilt, ytilt, dtime,
                            viewzoom, viewrotation, barrel_rotation, linear);
}

int main(void) {
    int w = 300, h = 150;
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

    printf("Rendered demo.\n");
    mypaint_brush_unref(brush);
    mypaint_surface_unref((MyPaintSurface *)surface);
    return 0;
}
```

CMakeLists.txt using pkg-config:
```
cmake_minimum_required(VERSION 3.16)
project(myclient C)
set(CMAKE_C_STANDARD 11)

find_package(PkgConfig REQUIRED)
pkg_check_modules(MYPAINT REQUIRED libmypaint-2.0)

add_executable(myclient main.c)

target_include_directories(myclient PRIVATE ${MYPAINT_INCLUDE_DIRS})
# Some pkg-config files also provide Cflags/Libs; use the imported variables:
target_compile_options(myclient PRIVATE ${MYPAINT_CFLAGS_OTHER})

target_link_directories(myclient PRIVATE ${MYPAINT_LIBRARY_DIRS})
target_link_libraries(myclient PRIVATE ${MYPAINT_LIBRARIES})
```

Build it:
```
cd myclient
cmake -S . -B build
cmake --build build -j
./build/myclient
```

If CMake can’t find pkg-config or the .pc file, point CMake to your prefix, e.g.:
```
export PKG_CONFIG_PATH="$HOME/.local/lib/pkgconfig:$PKG_CONFIG_PATH"
cmake -S . -B build -DPKG_CONFIG_EXECUTABLE=/usr/bin/pkg-config
```

### 3.2 Without pkg-config (manual include/lib paths)
```
# Replace paths with your actual install prefix
set(MYPAINT_PREFIX $ENV{HOME}/.local)
include_directories(${MYPAINT_PREFIX}/include)
link_directories(${MYPAINT_PREFIX}/lib)
add_executable(myclient main.c)
target_link_libraries(myclient PRIVATE mypaint)
```

---

## 4) Building the libmypaint examples in different ways

The repository includes a minimal example under `examples/`. Here are three ways to build something similar:

1) One-shot command (no Makefile/CMake):
```
# After running ./configure && make in the libmypaint tree to generate headers
cc -I. -I./fastapprox -o examples/minimal examples/minimal.c \
   $(pkg-config --cflags --libs glib-2.0 json-c) -lm
```

2) Simple Makefile in examples/ (conceptual illustration):
```
# file: examples/Makefile (not installed by default; create if you want to play)
CC = cc
CFLAGS = -I.. -I../fastapprox $(shell pkg-config --cflags glib-2.0 json-c)
LDFLAGS = $(shell pkg-config --libs glib-2.0 json-c) -lm

minimal: minimal.c
	$(CC) $(CFLAGS) -o minimal minimal.c $(LDFLAGS)

clean:
	rm -f minimal
```
Run: `make -C examples`.

3) Tiny CMakeLists.txt in examples/ (again, for learning):
```
# file: examples/CMakeLists.txt
cmake_minimum_required(VERSION 3.16)
project(examples C)
set(CMAKE_C_STANDARD 11)

find_package(PkgConfig REQUIRED)
pkg_check_modules(DEPS REQUIRED glib-2.0 json-c)

add_executable(minimal minimal.c)
target_include_directories(minimal PRIVATE ${DEPS_INCLUDE_DIRS} ${CMAKE_SOURCE_DIR}/.. ${CMAKE_SOURCE_DIR}/../fastapprox)
target_compile_options(minimal PRIVATE ${DEPS_CFLAGS_OTHER})
target_link_directories(minimal PRIVATE ${DEPS_LIBRARY_DIRS})
target_link_libraries(minimal PRIVATE ${DEPS_LIBRARIES} m)
```
Build:
```
cd examples
cmake -S . -B build
cmake --build build -j
./build/minimal
```

Note: These toy files are for learning; the official way to build this repository remains Autotools (`./configure && make`).

---

## 5) Glossary
- Compiler: translates C/C++ code to machine code (e.g., gcc, clang, MSVC).
- Linker: combines object files and libraries into an executable or shared library.
- Static library (.a, .lib): linked into your executable; no separate runtime file needed.
- Shared library (.so, .dylib, .dll): loaded at runtime; multiple programs can share it.
- Header (.h): contains declarations for functions/types; included by source files.
- pkg-config: a tool that tells compilers/linkers which flags to use for libraries.
- Prefix: the installation root (e.g., /usr, /usr/local, $HOME/.local). Headers go under prefix/include, libraries under prefix/lib, and pkg-config files under prefix/lib/pkgconfig.

---

## 6) Quick decision guide
- I only want to build libmypaint from source: use Autotools as shown in README.md/BUILDING.md.
- I want to learn how make works: write a tiny Makefile and run `make`.
- My app already uses CMake and I want to use libmypaint: install libmypaint, then link to it using CMake + pkg-config.

If you’re stuck, include your OS, how you got the sources (git vs release), and the exact commands + error outputs when asking for help.