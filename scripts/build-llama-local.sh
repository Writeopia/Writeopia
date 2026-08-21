#!/bin/bash
set -e

# Build llama.cpp for local development
# Usage: ./scripts/build-llama-local.sh [version]

VERSION="${1:-b4239}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/llama-cpp"
NATIVES_DIR="$PROJECT_DIR/application/core/localai/src/jvmMain/resources/natives"

echo "Building llama.cpp $VERSION for local development..."

# Detect OS and architecture
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Darwin)
        OS_NAME="macos"
        LIB_EXT="dylib"
        ;;
    Linux)
        OS_NAME="linux"
        LIB_EXT="so"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        OS_NAME="windows"
        LIB_EXT="dll"
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

case "$ARCH" in
    arm64|aarch64)
        ARCH_NAME="arm64"
        ;;
    x86_64|amd64)
        ARCH_NAME="x64"
        ;;
    *)
        echo "Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

TARGET_DIR="$NATIVES_DIR/$OS_NAME-$ARCH_NAME"
echo "Target: $OS_NAME-$ARCH_NAME"

# Clone or update llama.cpp
if [ -d "$BUILD_DIR/llama.cpp" ]; then
    echo "Updating existing llama.cpp..."
    cd "$BUILD_DIR/llama.cpp"
    git fetch --tags
    git checkout "$VERSION"
else
    echo "Cloning llama.cpp..."
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    git clone --depth 1 --branch "$VERSION" https://github.com/ggerganov/llama.cpp.git
    cd llama.cpp
fi

# Build
echo "Building..."
CMAKE_ARGS="-DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DGGML_NATIVE=OFF"

if [ "$OS_NAME" = "macos" ]; then
    CMAKE_ARGS="$CMAKE_ARGS -DGGML_METAL=ON -DGGML_ACCELERATE=ON"
fi

cmake -B build $CMAKE_ARGS
cmake --build build --config Release -j "$(nproc 2>/dev/null || sysctl -n hw.ncpu)"

# Copy to project
echo "Copying library to $TARGET_DIR..."
mkdir -p "$TARGET_DIR"

if [ "$OS_NAME" = "windows" ]; then
    cp build/bin/Release/llama.dll "$TARGET_DIR/"
    cp build/bin/Release/ggml*.dll "$TARGET_DIR/" 2>/dev/null || true
else
    cp build/src/libllama.$LIB_EXT "$TARGET_DIR/"
    cp build/ggml/src/libggml*.$LIB_EXT "$TARGET_DIR/" 2>/dev/null || true
fi

echo ""
echo "Done! Library installed to: $TARGET_DIR"
echo ""
echo "Files:"
ls -la "$TARGET_DIR"
echo ""
echo "You can now run the desktop app and test Local AI."
