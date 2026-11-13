#!/usr/bin/env bash
# Downloads real photos (Wikimedia Commons search) for the emulator test: sheep, goats, and unrelated "other" photos.
# Used only by CI; the images are never committed.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)/app/src/androidTest/assets/images"
mkdir -p "$ROOT/sheep" "$ROOT/goat" "$ROOT/other"
UA="ShepherdAI-CI/1.0 (emulator test image fetch)"
API="https://commons.wikimedia.org/w/api.php"

# fetch_set <dir> <file-prefix> <search query> <count>
fetch_set() {
  local dir="$1" prefix="$2" q="$3" n="$4" json urls i=0
  json=$(curl -fsSL --retry 3 -A "$UA" -G "$API" \
    --data-urlencode "action=query" --data-urlencode "generator=search" \
    --data-urlencode "gsrnamespace=6" --data-urlencode "gsrsearch=$q filetype:bitmap" \
    --data-urlencode "gsrlimit=30" --data-urlencode "prop=imageinfo" \
    --data-urlencode "iiprop=url|mime" --data-urlencode "iiurlwidth=640" \
    --data-urlencode "format=json") || { echo "::warning::search failed: $q"; return; }
  urls=$(echo "$json" | jq -r '.query.pages // {} | to_entries | sort_by(.value.index)
      | .[] | (.value.imageinfo // [])[0] | select(. != null and .mime == "image/jpeg") | .thumburl' | head -n "$n")
  while read -r url; do
    [ -z "$url" ] && continue
    i=$((i+1))
    if curl -fsSL --retry 3 -A "$UA" -o "$ROOT/$dir/${prefix}_$i.jpg" "$url"; then
      echo "ok  $dir/${prefix}_$i.jpg  <- $url"
    else
      rm -f "$ROOT/$dir/${prefix}_$i.jpg"; echo "::warning::download failed: $url"
    fi
    sleep 1
  done <<< "$urls"
}

fetch_set sheep s1 "sheep flock grazing pasture" 6
fetch_set sheep s2 "sheep standing in field" 4
fetch_set goat  g1 "goat standing" 4
fetch_set other o1 "city street" 2
fetch_set other o2 "dog" 2
fetch_set other o3 "cow cattle pasture" 2
echo "sheep: $(ls "$ROOT/sheep" | wc -l)  goat: $(ls "$ROOT/goat" | wc -l)  other: $(ls "$ROOT/other" | wc -l)"
exit 0
