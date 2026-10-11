#!/usr/bin/env bash
# Prints the CHANGELOG.md section of a version (default: appVersion from gradle.properties) and checks
# that the version also has its F-Droid changelogs. Fails when anything is missing, so a release can
# never go out with empty notes. Used by the CI and Release workflows; see RELEASING.md.
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:-$(grep '^appVersion=' gradle.properties | cut -d= -f2)}"

notes=$(awk -v v="[$version]" 'index($0, "## " v) == 1 && length($0) == length("## " v) {found=1; next} found && /^## \[/ {exit} found {print}' CHANGELOG.md \
  | sed -e '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
if [ -z "$notes" ]; then
  echo "error: CHANGELOG.md has no '## [$version]' section with notes" >&2
  exit 1
fi

# Version code as in app/build.gradle.kts: (MAJOR*10000 + MINOR*100 + PATCH) * 100 + (rc number, or 99).
if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.?([0-9]+))?$ ]]; then
  echo "error: '$version' is not MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rcN" >&2
  exit 1
fi
code=$(( (BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]) * 100 + ${BASH_REMATCH[5]:-99} ))
for locale in en-US es-ES; do
  file="fastlane/metadata/android/$locale/changelogs/$code.txt"
  if [ ! -s "$file" ]; then
    echo "error: missing $file" >&2
    exit 1
  fi
  if [ "$(wc -c < "$file")" -gt 500 ]; then
    echo "error: $file is longer than 500 bytes" >&2
    exit 1
  fi
done

printf '%s\n' "$notes"
