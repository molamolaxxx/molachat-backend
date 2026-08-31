#!/usr/bin/env bash
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
default_root=$(CDPATH= cd -- "$script_dir/../../../.." && pwd)
project_root=${1:-$default_root}

if [ ! -f "$project_root/pom.xml" ] || [ ! -d "$project_root/src/main" ]; then
  echo "Not a MolaChat repository: $project_root" >&2
  exit 2
fi

cd "$project_root"

echo "MolaChat project snapshot"
echo "root: $project_root"
echo "branch: $(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
echo "head: $(git rev-parse --short HEAD 2>/dev/null || true)"

echo
echo "Worktree"
git status --short

echo
echo "Top-level source packages"
find src/main/java/com/mola/molachat -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort

echo
echo "Tests by area"
find src/test -type f \( -name '*Test.java' -o -name '*.test.js' \) -print \
  | sed -E 's#^src/test/java/com/mola/molachat/##; s#^src/test/js/#js/#' \
  | awk -F/ '{count[$1]++} END {for (area in count) print area ": " count[area]}' \
  | sort

echo
echo "Relevant recent log errors (file names and line numbers only)"
if [ -d logs ]; then
  recent_logs=$(find logs -maxdepth 1 -type f -name '*.log' -printf '%T@ %p\n' | sort -nr | head -3 | cut -d' ' -f2-)
  if [ -n "$recent_logs" ]; then
    # Avoid printing message bodies, tokens, or user content in a general snapshot.
    printf '%s\n' "$recent_logs" | while IFS= read -r log_file; do
      error_count=$(rg -i -c '(^|[^a-z])(error|exception|timeout|rejected)([^a-z]|$)' "$log_file" 2>/dev/null || true)
      echo "$log_file: ${error_count:-0} matching lines"
    done
  else
    echo "no log files"
  fi
else
  echo "no logs directory"
fi

echo
echo "Reminder: pom.xml defaults skipTests=true; use -DskipTests=false to execute tests."
