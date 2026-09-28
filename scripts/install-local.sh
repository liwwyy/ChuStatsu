#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mod_version="$(sed -n 's/^mod_version=//p' "$project_dir/gradle.properties" | head -n 1)"
source_jar="$project_dir/build/libs/chustatsu-${mod_version}+mc1.8.9.jar"
instance_dir="${CHUSTATSU_INSTANCE_DIR:-/mnt/a400/Games/PrismLauncher/instances/OneClient 1.8.9-alpha.5-Chustatsu-dev}"
mods_dir="$instance_dir/minecraft/mods"

if [[ ! -f "$source_jar" || ! -d "$mods_dir" ]]; then
    echo "Build ChuStatsu first and confirm CHUSTATSU_INSTANCE_DIR points to the OneClient instance." >&2
    exit 1
fi

for previous in "$mods_dir"/chustatsu-*.jar; do
    if [[ -f "$previous" && "$previous" != "$mods_dir/$(basename "$source_jar")" ]]; then
        backup_dir="$project_dir/backups/replaced-jars"
        mkdir -p "$backup_dir"
        mv "$previous" "$backup_dir/$(basename "$instance_dir")-$(date +%Y%m%d-%H%M%S)-$(basename "$previous")"
    fi
done

cp "$source_jar" "$mods_dir/$(basename "$source_jar")"
echo "Installed $(basename "$source_jar") into $mods_dir."
