package com.example.mycompose.shepherdai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mycompose.shepherdai.ai.AnimalDetector
import com.example.mycompose.shepherdai.ai.Box
import com.example.mycompose.shepherdai.ai.Detection
import com.example.mycompose.shepherdai.ai.EmbeddingEngine
import com.example.mycompose.shepherdai.ai.ModelManager
import com.example.mycompose.shepherdai.ai.Quality
import com.example.mycompose.shepherdai.ai.Similarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Feeds real photos (downloaded by scripts/fetch_test_images.sh into androidTest/assets/images/{sheep,goat,other})
 * through the app's real detector + fingerprint models on the emulator, and writes annotated images and
 * markdown reports to <externalFilesDir>/report (pulled and uploaded by CI).
 */
@RunWith(AndroidJUnit4::class)
class SheepSimulationTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val testAssets = inst.context.assets
    private val out = File(ctx.getExternalFilesDir(null), "report").apply { mkdirs() }

    private val sheepClass = 19
    private val minScore = 0.4f

    private fun images(dir: String): List<String> =
        (testAssets.list("images/$dir") ?: emptyArray()).filter { it.endsWith(".jpg") }.sorted()

    private fun load(dir: String, name: String): Bitmap =
        testAssets.open("images/$dir/$name").use { BitmapFactory.decodeStream(it) }

    private fun pct(f: Float) = String.format(Locale.US, "%.0f%%", f * 100)

    private fun className(c: Int) = when (c) {
        17 -> "dog"; 18 -> "horse"; 19 -> "sheep"; 20 -> "cow"; else -> "cls$c"
    }

    private fun detector(): AnimalDetector {
        val mm = ModelManager(ctx)
        assertTrue("sheep_goat_detector.tflite is missing from the APK assets", mm.isDetectorInstalled())
        return AnimalDetector.load(mm.detectorFile)
            ?: throw AssertionError("Detector failed to load (unexpected input/output tensor shapes)")
    }

    private fun savePic(name: String, bm: Bitmap) =
        FileOutputStream(File(out, name)).use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }

    private fun annotate(src: Bitmap, dets: List<Detection>): Bitmap {
        val bm = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(bm)
        val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = bm.width / 140f + 2f }
        val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true; textSize = bm.width / 24f }
        for (d in dets) {
            val sheep = d.cls == sheepClass && d.score >= minScore
            stroke.color = if (sheep) Color.GREEN else Color.YELLOW
            fill.color = stroke.color
            val l = d.box.l * bm.width; val t = d.box.t * bm.height
            c.drawRect(l, t, d.box.r * bm.width, d.box.b * bm.height, stroke)
            c.drawText("${className(d.cls)} ${pct(d.score)}", l + 4f, t + fill.textSize, fill)
        }
        return bm
    }

    private fun crop(b: Bitmap, bx: Box): Bitmap? {
        val padX = (bx.r - bx.l) * 0.05f; val padY = (bx.b - bx.t) * 0.05f
        val l = ((bx.l - padX) * b.width).toInt().coerceIn(0, b.width - 1)
        val t = ((bx.t - padY) * b.height).toInt().coerceIn(0, b.height - 1)
        val r = ((bx.r + padX) * b.width).toInt().coerceIn(l + 1, b.width)
        val bt = ((bx.b + padY) * b.height).toInt().coerceIn(t + 1, b.height)
        if (r - l < 8 || bt - t < 8) return null
        return Bitmap.createBitmap(b, l, t, r - l, bt - t)
    }

    /** Simulates a second sighting of the same animal: tighter framing and brighter light. */
    private fun augment(src: Bitmap): Bitmap {
        val cw = (src.width * 0.85f).toInt().coerceAtLeast(8)
        val ch = (src.height * 0.85f).toInt().coerceAtLeast(8)
        val c = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val o = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val g = 1.15f
        val p = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                g, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, g, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
        }
        Canvas(o).drawBitmap(c, 0f, 0f, p)
        return o
    }

    @Test
    fun detectorOnRealPhotos() {
        val det = detector()
        val sb = StringBuilder("## Detector on real photos\n\n")
        sb.append("| image | group | sheep found | best score | other classes |\n|---|---|---|---|---|\n")
        val stats = HashMap<String, Pair<Int, Int>>()
        for (group in listOf("sheep", "goat", "other")) {
            val names = images(group)
            var with = 0
            for (n in names) {
                val bmp = load(group, n)
                val dets = det.detect(bmp).filter { it.score >= 0.3f }
                val sheep = dets.filter { it.cls == sheepClass && it.score >= minScore }
                if (sheep.isNotEmpty()) with++
                val best = sheep.maxOfOrNull { it.score }
                val others = dets.filter { it.cls != sheepClass }.joinToString(", ") { "${className(it.cls)} ${pct(it.score)}" }
                sb.append("| $n | $group | ${sheep.size} | ${best?.let { pct(it) } ?: "-"} | $others |\n")
                savePic("det_${group}_$n", annotate(bmp, dets))
            }
            stats[group] = with to names.size
        }
        sb.append("\n### Summary (images where at least one sheep was detected)\n\n")
        for ((g, v) in stats) sb.append("- **$g**: ${v.first} of ${v.second}\n")
        sb.append("\nExpected: `sheep` high, `other` near 0. `goat` is informational (COCO has no goat class).\n")
        File(out, "detection_report.md").writeText(sb.toString())

        val (hit, total) = stats.getValue("sheep")
        assertTrue("No sheep test images were downloaded", total > 0)
        assertTrue("Detector found sheep in only $hit of $total sheep photos", hit * 2 >= total)
    }

    @Test
    fun fingerprintsSeparateAnimals() {
        val det = detector()
        val mm = ModelManager(ctx)
        assertTrue("animal_identifier.tflite is missing from the APK assets", mm.isInstalled())
        val emb = EmbeddingEngine.load(mm.identifierFile)
            ?: throw AssertionError("Fingerprint model failed to load")

        val crops = ArrayList<Pair<String, Bitmap>>()
        for (n in images("sheep")) {
            val bmp = load("sheep", n)
            val d = det.detect(bmp).filter { it.cls == sheepClass && it.score >= minScore }.maxByOrNull { it.score } ?: continue
            crop(bmp, d.box)?.let { crops.add(n to it) }
            if (crops.size == 8) break
        }
        assertTrue("Fewer than 3 sheep crops available (${crops.size})", crops.size >= 3)

        val e1 = crops.map { emb.embed(it.second) }
        val e2 = crops.map { emb.embed(augment(it.second)) }
        for (v in e1) {
            assertTrue("Unexpected embedding size ${v.size}", v.size > 16)
            assertTrue("Embedding has NaN/Inf", v.all { it.isFinite() })
            val norm = sqrt(v.fold(0f) { a, x -> a + x * x })
            assertTrue("Embedding is not normalized (norm=$norm)", abs(norm - 1f) < 1e-3f)
        }

        val same = e1.indices.map { Similarity.cosine(e1[it], e2[it]) }
        val diff = ArrayList<Float>()
        for (i in e1.indices) for (j in e1.indices) if (i != j) diff.add(Similarity.cosine(e1[i], e1[j]))
        var top1 = 0
        for (i in e2.indices) {
            val best = e1.indices.maxByOrNull { j -> Similarity.cosine(e2[i], e1[j]) }
            if (best == i) top1++
        }

        val sb = StringBuilder("## Fingerprints (Re-ID)\n\n")
        sb.append("Embedding size: ${e1.first().size}. Sheep crops used: ${crops.size}.\n\n")
        sb.append("| crop | sharpness (min ${Quality.MIN_SHARPNESS}) | same animal, 2nd sighting |\n|---|---|---|\n")
        crops.forEachIndexed { i, c ->
            sb.append("| ${c.first} | ${String.format(Locale.US, "%.0f", Quality.sharpness(c.second))} | ${String.format(Locale.US, "%.3f", same[i])} |\n")
        }
        sb.append("\n- mean cosine, same animal (augmented): **${String.format(Locale.US, "%.3f", same.average())}**\n")
        sb.append("- mean cosine, different animals: **${String.format(Locale.US, "%.3f", diff.average())}**\n")
        sb.append("- re-identification top-1: **$top1 of ${crops.size}**\n")
        sb.append("\nNote: 'same animal' here is the same photo reframed and brightened, so this checks the pipeline and normalization, not real-world accuracy on your flock.\n")
        File(out, "reid_report.md").writeText(sb.toString())

        assertTrue("Same-animal similarity (${same.average()}) is not above different-animal similarity (${diff.average()})", same.average() > diff.average())
        assertTrue("Top-1 re-identification only $top1 of ${crops.size}", top1 * 10 >= crops.size * 6)
        assertEquals(e1.size, e2.size)
    }
}
