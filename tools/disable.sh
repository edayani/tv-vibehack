#!/usr/bin/env bash
# Preview a debloat list, preserving connectivity and unreviewed system packages.
# Pass --apply to disable eligible packages; use compatibility.py --restore for exact rollback.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
cd "$(dirname "$0")/.."
LIST="${1:?usage: disable.sh <debloat list> [--apply] [compatibility options]}"
shift
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
# -I excludes the script directory; explicitly add our own tools directory for the local tv module.
exec "$PY" -I -c 'import runpy,sys; sys.path.insert(0,"tools"); runpy.run_path("tools/compatibility.py",run_name="__main__")' "$LIST" "$@"
