# Building libmypaint (Quick Start)

This document gives you a concise, copy/paste‑friendly guide to build and install libmypaint from source on common platforms. For full details, see README.md.

For Android cross-compilation and integration in Android Studio, see doc/ANDROID.md.
If you are new to C/C++ build tools (make, Autotools, CMake), see doc/BUILD_SYSTEMS_TUTORIAL.md for a step-by-step introduction.

## Contents
- Linux (Debian/Ubuntu, Fedora/RHEL/CentOS, openSUSE)
- macOS (Homebrew/MacPorts)
- Windows (MSYS2/MinGW, Visual Studio via MSYS2 shell)
- Build options
- Troubleshooting

---

## Linux

All builds use Autotools. If you cloned from git you must run `./autogen.sh` once before `./configure`.

### Debian/Ubuntu and derivatives
```
sudo apt update
sudo apt install -y build-essential libjson-c-dev libgirepository1.0-dev libglib2.0-dev \
    python3 autoconf automake libtool intltool gettext

# From a release tarball:
./configure

# From a git clone:
./autogen.sh
./configure

make -j$(nproc)
sudo make install
sudo ldconfig
```

### Fedora/RHEL/CentOS
```
sudo dnf install -y gcc gobject-introspection-devel json-c-devel glib2-devel \
    git python3 autoconf automake libtool intltool gettext

# Release tarball:
./configure
# or from git:
./autogen.sh && ./configure

make -j$(nproc)
sudo make install
sudo ldconfig
```

### openSUSE (Tumbleweed/Leap)
```
sudo zypper install -y gcc gobject-introspection-devel libjson-c-devel glib2-devel \
    git autoconf automake libtool gettext-tools intltool python3

# Release tarball:
./configure
# or from git:
./autogen.sh && ./configure

make -j$(nproc)
sudo make install
sudo ldconfig
```

## macOS

Use Homebrew (recommended) or MacPorts to install dependencies, then build with Autotools.

### Homebrew
```
# Install dependencies
brew install json-c glib gobject-introspection gettext autoconf automake libtool intltool pkg-config

# Ensure gettext is available to the build
export PATH="$(brew --prefix gettext)/bin:$PATH"
export PKG_CONFIG_PATH="$(brew --prefix gettext)/lib/pkgconfig:$PKG_CONFIG_PATH"

# In the libmypaint source directory:
# From a release tarball:
./configure
# or from git:
./autogen.sh && ./configure

make -j"$(sysctl -n hw.ncpu)"
sudo make install  # or: make install PREFIX="$HOME/.local"
```

### MacPorts
```
sudo port install json-c glib2 gobject-introspection autoconf automake libtool intltool pkgconfig

# From release tarball:
./configure
# or from git:
./autogen.sh && ./configure

make -j"$(sysctl -n hw.ncpu)"
sudo make install
```

Notes:
- macOS does not use ldconfig. If a consumer can’t find the library, set:
  - export PKG_CONFIG_PATH="/usr/local/lib/pkgconfig:$PKG_CONFIG_PATH" (or your chosen prefix)
  - export DYLD_LIBRARY_PATH="/usr/local/lib:$DYLD_LIBRARY_PATH" if needed.

## Windows (MSYS2/MinGW)

The simplest way is MSYS2 with MinGW shells.

1) Install MSYS2 from https://www.msys2.org/ and open the “MSYS2 MSYS” shell once to update:
```
pacman -Syu
# Restart MSYS2 shell if prompted, then:
pacman -Syu
```
2) Open the “MSYS2 MinGW 64-bit” shell (or 32-bit if you want i686) and install deps:
```
pacman -S --needed \
  mingw-w64-x86_64-toolchain \
  mingw-w64-x86_64-json-c \
  mingw-w64-x86_64-glib2 \
  mingw-w64-x86_64-gobject-introspection \
  mingw-w64-x86_64-intltool \
  mingw-w64-x86_64-gettext \
  mingw-w64-x86_64-autotools \
  git
```
3) Build in the same MinGW shell:
```
# From release tarball:
./configure --prefix=/mingw64
# or from git:
./autogen.sh && ./configure --prefix=/mingw64

make -j$(nproc)
make install
```

Note: pkg-config files will be under /mingw64/lib/pkgconfig.

## Build options

Show all options:
```
./configure --help
```
Common toggles:
- --disable-introspection: build without GObject Introspection
- --without-glib: build the pure-C core library only
- --disable-i18n: disable internationalization (skip gettext/intltool, no translations)
- --enable-gegl: enable GEGL/BABL support (not required by GIMP)
- --prefix=PATH: install under PATH instead of the default

You can also set CFLAGS to enable stronger optimizations, e.g.:
```
export CFLAGS='-O3 -march=native -mtune=native -ffast-math'
```

## Verify installation
```
pkg-config --list-all | grep -i mypaint || true
# If not listed, ensure PKG_CONFIG_PATH includes your prefix’s pkgconfig dir

# On Linux:
sudo ldconfig -p | grep -i libmypaint || true
```

## Troubleshooting
- configure: error: json-c not found
  - Ensure json-c development package is installed (libjson-c-dev/json-c-devel) and PKG_CONFIG_PATH is set if installed in a non-standard prefix.
- gobject-introspection issues on macOS
  - Ensure you’re building in an environment where GI can find glib and python; with Homebrew, export PATH/PKG_CONFIG_PATH for gettext as shown above.
- After install, consumers can’t find the library
  - Linux: run `sudo ldconfig` or add your prefix lib dir to /etc/ld.so.conf.d
  - macOS: set DYLD_LIBRARY_PATH and PKG_CONFIG_PATH for your prefix
  - Windows (MSYS2): ensure you’re in the MinGW shell corresponding to the installed prefix (/mingw64) and PKG_CONFIG_PATH includes /mingw64/lib/pkgconfig

If you get stuck, see README.md for more details or open an issue with your OS, how you obtained the source (git vs release tarball), and the exact error output.

- aclocal: error: cannot find glib-2.0.m4 in aclocal's search path
  - Ensure GLib and pkg-config development files are installed (e.g., libglib2.0-dev/glib2-devel). On macOS: `brew install glib gettext pkg-config` or with MacPorts: `sudo port install glib2 pkgconfig`.
  - Set the search path for aclocal macros if installed in a non-standard prefix:
    - Linux example: export ACLOCAL_PATH="/usr/local/share/aclocal:$ACLOCAL_PATH"
    - Homebrew: export ACLOCAL_PATH="$(brew --prefix)/share/aclocal:$(brew --prefix gettext)/share/aclocal:$(brew --prefix glib)/share/aclocal:$ACLOCAL_PATH"
    - MacPorts: export ACLOCAL_PATH="/opt/local/share/aclocal:$ACLOCAL_PATH"
  - Alternatively, set ACLOCAL_FLAGS when running autogen.sh: ACLOCAL_FLAGS="-I /path/to/share/aclocal" ./autogen.sh
