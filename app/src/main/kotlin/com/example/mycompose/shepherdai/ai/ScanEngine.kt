package com.example.mycompose.shepherdai.ai

import android.graphics.Bitmap
import com.example.mycompose.shepherdai.data.Animal
import com.example.mycompose.shepherdai.data.AnimalEmbedding

data class OverlayBox(val box: Box, val label: String)
data class FrameResult(val boxes: List<OverlayBox> = emptyList(), val visible: Int = 0, val counted: Int = 0, val classes: String = "")

object Quality {
    const val MIN_PIXELS = 64
    const val MIN_SHARPNESS = 60.0 // untuned starting value; calibrate on your phone

    /** Variance of the Laplacian on a 64x64 grayscale copy: low = blurry. */
    fun sharpness(src: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(src, 64, 64, true)
        val px = IntArray(64 * 64); s.getPixels(px, 0, 64, 0, 0, 64, 64)
        val g = IntArray(px.size) { val p = px[it]; (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10 }
        var sum = 0.0; var sq = 0.0; var n = 0
        for (y in 1 until 63) for (x in 1 until 63) {
            val l = (4 * g[y * 64 + x] - g[y * 64 + x - 1] - g[y * 64 + x + 1] - g[(y - 1) * 64 + x] - g[(y + 1) * 64 + x]).toDouble()
            sum += l; sq += l * l; n++
        }
        val m = sum / n
        return sq / n - m * m
    }
}

class ScanEngine(
    private val detector: AnimalDetector,
    private val reid: EmbeddingEngine?,
    private val animals: Map<Long, Animal>,
    private val embeddings: List<AnimalEmbedding>,
    private val threshold: Float,
    private val minScore: Float,
    private val classId: Int // -1 = accept every class (debugging)
) {
    private var tracker = SimpleTracker()
    private var frame = 0

    fun reset() { tracker = SimpleTracker(); frame = 0 }
    fun count() = tracker.uniqueCount()
    fun identifiedBest() = tracker.identifiedBest()

    /** Per frame: one detection, plus at most one fingerprint, and only from sharp, large-enough crops. */
    fun process(bmp: Bitmap): FrameResult {
        frame++
        val all = detector.detect(bmp).filter { it.score >= minScore }
        val dets = all.filter { classId < 0 || it.cls == classId }
        val visible = tracker.update(dets.map { it.box })
        if (reid != null) {
            val t = visible.firstOrNull { it.animalId == null && it.hits >= tracker.minHits && frame - it.lastTry >= 6 }
            if (t != null) {
                t.lastTry = frame
                val c = crop(bmp, t.box)
                if (c != null && Quality.sharpness(c) >= Quality.MIN_SHARPNESS) {
                    t.embs.add(reid.embed(c))
                    if (t.embs.size > 5) t.embs.removeAt(0)
                    // identity needs at least 2 good frames, fused by averaging
                    if (t.embs.size >= 2) {
                        val r = Similarity.identify(Similarity.mean(t.embs), embeddings, animals, threshold)
                        if (r is IdResult.Match) { t.animalId = r.animal.id; t.bestScore = r.score }
                    }
                }
            }
        }
        val shown = visible.filter { it.hits >= 2 }.map { t ->
            val a = t.animalId?.let { animals[it] }
            OverlayBox(t.box, when {
                a != null -> "#${a.tag} ${a.name}"
                reid == null -> "${t.id}"
                t.embs.size >= 2 -> "غير معروف"
                else -> "جارٍ التحقق"
            })
        }
        return FrameResult(shown, shown.size, tracker.uniqueCount(), all.map { it.cls }.distinct().joinToString(","))
    }

    private fun crop(b: Bitmap, bx: Box): Bitmap? {
        val l = (bx.l * b.width).toInt().coerceIn(0, b.width - 1)
        val t = (bx.t * b.height).toInt().coerceIn(0, b.height - 1)
        val w = minOf(((bx.r - bx.l) * b.width).toInt(), b.width - l)
        val h = minOf(((bx.b - bx.t) * b.height).toInt(), b.height - t)
        return if (w < Quality.MIN_PIXELS || h < Quality.MIN_PIXELS) null else Bitmap.createBitmap(b, l, t, w, h)
    }
}
