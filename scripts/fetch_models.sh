#!/usr/bin/env bash
# Downloads the small on-device models into the APK assets. Used by CI and locally.
set -uo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/models"
mkdir -p "$DIR"
fetch() {
  local name="$1" url="$2"
  if [ -s "$DIR/$name" ]; then echo "exists: $name"; return; fi
  echo "downloading $name"
  if curl -fL --retry 3 -o "$DIR/$name" "$url"; then
    sha256sum "$DIR/$name"
  else
    rm -f "$DIR/$name"
    echo "::warning::could not download $name from $url - the APK will build WITHOUT it (model shows as not installed)"
  fi
}
# 1) Detector: EfficientDet-Lite0 uint8 (COCO, includes 'sheep')
fetch sheep_goat_detector.tflite "https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite0_uint8.tflite"
# 2) Embedder used as the Re-ID fingerprint: MobileNetV3-small image embedder
fetch animal_identifier.tflite "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite"
exit 0
