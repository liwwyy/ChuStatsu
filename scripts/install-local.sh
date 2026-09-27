#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_jar="$project_dir/build/libs/chustatsu-0.1.0+mc1.8.9.jar"
instance_dir="${CHUSTATSU_INSTANCE_DIR:-/mnt/a400/Games/PrismLauncher/instances/OneClient 1.8.9-alpha.5-Chustatsu-dev}"
mods_dir="$instance_dir/minecraft/mods"

if [[ ! -f "$source_jar" || ! -d "$mods_dir" ]]; then
    echo "Build ChuStatsu first and confirm CHUSTATSU_INSTANCE_DIR points to the OneClient instance." >&2
    exit 1
fi

cp "$source_jar" "$mods_dir/$(basename "$source_jar")"
echo "Installed $(basename "$source_jar") into $mods_dir."
