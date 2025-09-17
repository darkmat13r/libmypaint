# Step-by-Step: Build libmypaint From Scratch

This page gives you copy/paste commands to build libmypaint from a clean machine. It covers Linux, macOS, and Windows (MSYS2). For Android, see doc/ANDROID.md.

If any command fails, copy the full output and open an issue with your OS, how you obtained the source (git vs release tarball), and what you ran.

---

## 0) Get the sources

From a release tarball (already contains `configure`):
```
# Download a release tarball from https://github.com/mypaint/libmypaint/releases
# Then extract and cd into it
# Example:
wget https://github.com/mypaint/libmypaint/archive/refs/tags/v2.0.0.tar.gz -O libmypaint-2.0.0.tar.gz
 tar xf libmypaint-2.0.0.tar.gz
 cd libmypaint-2.0.0
```

From git (you must run ./autogen.sh once):
```
git clone https://github.com/mypaint/libmypaint.git
cd libmypaint
```

---

## 1) Linux: Debian/Ubuntu
```
sudo apt update
sudo apt install -y build-essential libjson-c-dev libgirepository1.0-dev libglib2.0-dev \
    python3 autoconf automake libtool intltool gettext pkg-config

# If you cloned from git, generate configure first:
[ -f configure ] || ./autogen.sh

# Configure, build, install
./configure
make -j"$(nproc)"
sudo make install
sudo ldconfig

# Quick verification
pkg-config --list-all | grep -i libmypaint || true
ldconfig -p | grep -i libmypaint || true

# Run example that writes examples/output.ppm
make check || true
./examples/minimal 2>/dev/null || true
```

## 2) Linux: Fedora/RHEL/CentOS
```
sudo dnf install -y gcc gobject-introspection-devel json-c-devel glib2-devel \
    git python3 autoconf automake libtool intltool gettext pkgconf-pkg-config

[ -f configure ] || ./autogen.sh
./configure
make -j"$(nproc)"
sudo make install
sudo ldconfig

pkg-config --list-all | grep -i libmypaint || true
ldconfig -p | grep -i libmypaint || true
```

## 3) Linux: openSUSE (Tumbleweed/Leap)
```
sudo zypper install -y gcc gobject-introspection-devel libjson-c-devel glib2-devel \
    git autoconf automake libtool gettext-tools intltool pkg-config

[ -f configure ] || ./autogen.sh
./configure
make -j"$(nproc)"
sudo make install
sudo ldconfig
```

---

## 4) macOS (Homebrew)
```
# Install dependencies
eval "$(/bin/bash -c 'command -v brew >/dev/null || /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"; echo')" >/dev/null
brew update
brew install json-c glib gobject-introspection gettext autoconf automake libtool intltool pkg-config

# Ensure gettext tools are visible (intltool/gettext)
export PATH="$(brew --prefix gettext)/bin:$PATH"
export PKG_CONFIG_PATH="$(brew --prefix gettext)/lib/pkgconfig:$PKG_CONFIG_PATH"

# If from git:
[ -f configure ] || ./autogen.sh

# Configure & build
./configure
make -j"$(sysctl -n hw.ncpu)"
# Install to default (/usr/local or /opt/homebrew prefix), or to user prefix
sudo make install || make install PREFIX="$HOME/.local"

# macOS note: no ldconfig. If consumers can’t find the lib, set:
export PKG_CONFIG_PATH="/usr/local/lib/pkgconfig:$PKG_CONFIG_PATH"
export DYLD_LIBRARY_PATH="/usr/local/lib:$DYLD_LIBRARY_PATH"
```

---

## 5) Windows (MSYS2/MinGW64)
```
# Install MSYS2 from https://www.msys2.org/
# Open the "MSYS2 MSYS" shell and update once:
pacman -Syu
# Restart shell if prompted, then:
pacman -Syu

# Open the "MSYS2 MinGW 64-bit" shell and install deps:
pacman -S --needed \
  mingw-w64-x86_64-toolchain \
  mingw-w64-x86_64-json-c \
  mingw-w64-x86_64-glib2 \
  mingw-w64-x86_64-gobject-introspection \
  mingw-w64-x86_64-intltool \
  mingw-w64-x86_64-gettext \
  mingw-w64-x86_64-autotools \
  git

# In the same MinGW64 shell, from the libmypaint source dir:
[ -f configure ] || ./autogen.sh
./configure --prefix=/mingw64
make -j"$(nproc)"
make install

# pkg-config files end up in /mingw64/lib/pkgconfig
```

---

## 6) Build options (common)
Show all:
```
./configure --help
```
Useful toggles:
- --disable-introspection  (skip GObject Introspection)
- --without-glib           (pure-C core only)
- --enable-gegl            (optional GEGL/BABL)
- --prefix=PATH            (install under PATH)

Optional optimizations:
```
export CFLAGS='-O3 -march=native -mtune=native -ffast-math'
```
If configure says "C compiler cannot create executables", unset custom flags:
```
unset CFLAGS CXXFLAGS LDFLAGS
```

---

## 7) Run the minimal example
The examples write a PPM image at examples/output.ppm.
```
# From the repo root after building with make
./examples/minimal || true
ls -l examples/output.ppm || true
```
If you only want to compile the example as a single file (no install needed):
```
# After ./configure && make to generate headers like config.h
cc -I. -I./fastapprox -o examples/minimal examples/minimal.c \
   $(pkg-config --cflags --libs glib-2.0 json-c) -lm
./examples/minimal
```

---

## 8) Android
For Android step-by-step (NDK, ABIs, packaging into an Android Studio app), see:
- doc/ANDROID.md — full cross-compilation guide
- scripts/build-android-so-libs.sh — automated builder for common ABIs

---

## 9) Troubleshooting quick refs
- json-c not found: install -dev package and ensure PKG_CONFIG_PATH includes your prefix.
- GI/GLib issues on macOS: ensure Homebrew gettext/glib are on PATH/PKG_CONFIG_PATH as above.
- After install, consumer cannot find lib:
  - Linux: sudo ldconfig, or add your prefix lib to /etc/ld.so.conf.d
  - macOS: set DYLD_LIBRARY_PATH and PKG_CONFIG_PATH
  - MSYS2: use the MinGW shell that matches your prefix and ensure /mingw64/lib/pkgconfig is in PKG_CONFIG_PATH
