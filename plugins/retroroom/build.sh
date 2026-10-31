#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build/plugins/retroroom"

mkdir -p "${BUILD_DIR}"
cmake -B "${BUILD_DIR}" -S "${SCRIPT_DIR}" -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build "${BUILD_DIR}" --parallel
ctest --test-dir "${BUILD_DIR}" --output-on-failure
