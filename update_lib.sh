#!/bin/sh
# Rebuild TouchQuell's vendored input helper for every packaged Android ABI.

set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
source_file="$project_dir/app/src/main/cpp/blockevent.c"
output_root="$project_dir/app/src/main/jniLibs"
ndk_root=${ANDROID_NDK_HOME:-}

if [ -z "$ndk_root" ]; then
    echo "Set ANDROID_NDK_HOME to Android NDK r28 or newer." >&2
    exit 1
fi

if [ ! -f "$source_file" ]; then
    echo "Missing native source: $source_file" >&2
    exit 1
fi

toolchain=""
for candidate in "$ndk_root"/toolchains/llvm/prebuilt/*; do
    if [ -d "$candidate/bin" ]; then
        toolchain=$candidate
        break
    fi
done

if [ -z "$toolchain" ]; then
    echo "Android NDK toolchain not found under: $ndk_root" >&2
    echo "Set ANDROID_NDK_HOME to the installed NDK directory." >&2
    exit 1
fi

build_dir=$(mktemp -d "${TMPDIR:-/tmp}/touchquell-native.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM

build_one() {
    compiler_name=$1
    abi=$2
    compiler="$toolchain/bin/$compiler_name"
    built_binary="$build_dir/$abi-touchquell-input.so"

    if [ ! -x "$compiler" ]; then
        echo "Missing NDK compiler: $compiler" >&2
        exit 1
    fi

    "$compiler" \
        -std=gnu11 \
        -O2 \
        -fPIE \
        -pie \
        -fstack-protector-strong \
        -D_FORTIFY_SOURCE=2 \
        -Wall \
        -Wextra \
        -Werror \
        -Wl,-z,max-page-size=16384 \
        -Wl,--build-id=sha1 \
        -o "$built_binary" \
        "$source_file"

    "$toolchain/bin/llvm-strip" "$built_binary"
    mkdir -p "$output_root/$abi"
    cp "$built_binary" "$output_root/$abi/touchquell-input.so"
    chmod 0755 "$output_root/$abi/touchquell-input.so"
    echo "Built $abi"
}

build_one armv7a-linux-androideabi24-clang armeabi-v7a
build_one aarch64-linux-android24-clang arm64-v8a
build_one i686-linux-android24-clang x86
build_one x86_64-linux-android24-clang x86_64
