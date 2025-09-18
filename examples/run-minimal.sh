#!/usr/bin/env sh
set -eu

# This script builds and runs the minimal C example in examples/minimal.c
# It compiles the example as a single translation unit that includes
# libmypaint sources via examples/libmypaint.c, so you do not need an
# installed libmypaint for this option.
#
# Prerequisites (from the project root):
#   - If building from git: ./autogen.sh
#   - ./configure
#   - make (to generate headers and objects)
#
# Dependencies required at compile time:
#   - pkg-config entries for glib-2.0 and json-c
#
# Usage:
#   sh examples/run-minimal.sh

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

# Quick sanity checks
if [ ! -f "config.h" ] || [ ! -f "mypaint-config.h" ]; then
  echo "Error: build tree not prepared."
  echo "Run these first from the project root:" >&2
  echo "  ./autogen.sh   # only if building from git" >&2
  echo "  ./configure" >&2
  echo "  make" >&2
  exit 1
fi

# Ensure pkg-config can find required deps
if ! pkg-config --exists glib-2.0 json-c; then
  echo "Error: pkg-config cannot find glib-2.0 and/or json-c." >&2
  echo "Install the development packages and ensure PKG_CONFIG_PATH is set if needed." >&2
  echo "See README.md (Check availability) for details." >&2
  exit 1
fi

# Compile the example
CC=${CC:-cc}
CFLAGS_EXTRA=${CFLAGS_EXTRA:-}

echo "Compiling examples/minimal.c ..."
$CC -I. -I./fastapprox $CFLAGS_EXTRA \
  -o examples/minimal examples/minimal.c \
  $(pkg-config --cflags --libs gobject-2.0 glib-2.0 json-c) -lm

# Run it
echo "Running examples/minimal ..."
./examples/minimal

# Show output
if [ -f examples/output.ppm ]; then
  echo "Wrote examples/output.ppm"
  echo "You can convert it with ImageMagick, e.g.:"
  echo "  convert examples/output.ppm examples/output.png"
else
  echo "Expected examples/output.ppm to be created, but it was not."
  exit 1
fi
