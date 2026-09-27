#!/usr/bin/env bash
set -euo pipefail

instance_dir="${CHUSTATSU_INSTANCE_DIR:-/mnt/a400/Games/PrismLauncher/instances/OneClient 1.8.9-alpha.5-Chustatsu-dev}"
if [[ ! -f "$instance_dir/mmc-pack.json" ]]; then
    echo "Set CHUSTATSU_INSTANCE_DIR to the OneClient instance directory." >&2
    exit 1
fi
instance_dir="$(cd "$instance_dir" && pwd)"
launcher_dir="$(dirname "$(dirname "$instance_dir")")"
exec prismlauncher --dir "$launcher_dir" --launch "$(basename "$instance_dir")" "$@"
