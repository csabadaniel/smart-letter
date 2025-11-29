#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: ./scripts/ascii-scan.sh [path ...]
Scans documentation files for non-ASCII characters.
When no paths are provided, defaults to scanning:
  - specs
  - .specify
  - docs
  - README.md
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
DEFAULT_TARGETS=("specs" ".specify" "docs" "README.md")
TARGETS=("$@")
if [[ ${#TARGETS[@]} -eq 0 ]]; then
  TARGETS=("${DEFAULT_TARGETS[@]}")
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "[FAIL] python3 is required but not installed"
  exit 1
fi

is_text_file() {
  case "$1" in
    *.png|*.jpg|*.jpeg|*.gif|*.ico|*.pdf|*.zip|*.jar|*.class|*.bin)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

scan_file() {
  local file="$1"
  python3 - "$file" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
issues = []
with path.open('rb') as fh:
    data = fh.read()

line = 1
col = 1
for byte in data:
  if byte == 13:
    # Ignore carriage return; column should not advance
    continue
  if byte == 10:
    line += 1
    col = 1
    continue
  if byte > 127:
    issues.append((line, col, byte))
    if len(issues) >= 5:
      break
  col += 1

if issues:
  print(f"{path}")
  for line, col, byte in issues:
    display = f"0x{byte:02X}"
    print(f"  line {line}, column {col}: {display}")
    sys.exit(1)
PY
}

collect_files() {
  local target="$1"
  local abs_path="$ROOT_DIR/$target"
  if [[ -f "$abs_path" ]]; then
    FILES_TO_SCAN+=("$abs_path")
  elif [[ -d "$abs_path" ]]; then
    while IFS= read -r -d '' file; do
      FILES_TO_SCAN+=("$file")
    done < <(find "$abs_path" -type f -not -path "$ROOT_DIR/.git/*" -print0)
  else
    echo "[WARN] Skipping missing target: $target"
  fi
}

FILES_TO_SCAN=()
for target in "${TARGETS[@]}"; do
  collect_files "$target"
done

if [[ ${#FILES_TO_SCAN[@]} -eq 0 ]]; then
  echo "[OK] No files to scan"
  exit 0
fi

FAILED=0
for file in "${FILES_TO_SCAN[@]}"; do
  if ! is_text_file "$file"; then
    continue
  fi
  if ! scan_file "$file"; then
    FAILED=1
  fi
done

if [[ $FAILED -eq 1 ]]; then
  echo "[FAIL] Non-ASCII characters detected"
  exit 1
fi

echo "[OK] ASCII scan passed"
