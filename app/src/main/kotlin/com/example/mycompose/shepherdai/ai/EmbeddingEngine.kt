package com.example.mycompose.shepherdai.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Manages the single real model slot: animal_identifier.tflite (Re-ID embeddings). */
class ModelManager(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    val identifierFile = File(dir, "animal_identifier.tflite")

    val detectorFile = File(dir, "sheep_goat_detector.tflite")

    private fun bundle(name: String, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        runCatching { ctx.assets.open("models/$name").use { i -> dest.outputStream().use { o -> i.copyTo(o) } } }
            .onFailure { dest.delete() }
    }

    /** Models fetched at build time live in assets; a model imported by the user overrides them. */
    init {
        bundle("animal_identifier.tflite", identifierFile)
        bundle("sheep_goat_detector.tflite", detectorFile)
    }

    fun isInstalled() = identifierFile.exists() && identifierFile.length() > 0
    fun isDetectorInstalled() = detectorFile.exists() && detectorFile.length() > 0

    fun importDetector(uri: android.net.Uri): Boolean {
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> detectorFile.outputStream().use { o -> i.copyTo(o) } }
            true
        }.getOrDefault(false)
        val d = if (ok) AnimalDetector.load(detectorFile) else null
        if (d == null) { detectorFile.delete(); return false }
        d.close()
        return true
    }

    fun sha256(): String? = if (!isInstalled()) null else
        MessageDigest.getInstance("SHA-256").digest(identifierFile.readBytes())
            .joinToString("") { "%02x".format(it) }.take(16)

    /** Copies the picked file, then verifies LiteRT can load it; removes it otherwise. */
    fun import(uri: android.net.Uri): Boolean {
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { i ->
                identifierFile.outputStream().use { o -> i.copyTo(o) }
            }
            true
        }.getOrDefault(false)
        val engine = if (ok) EmbeddingEngine.load(identifierFile) else null
        if (engine == null) {
            identifierFile.delete()
            return false
        }
        engine.close()
        return true
    }
}

class EmbeddingEngine private constructor(
    private val interp: Interpreter,
    private val w: Int,
    private val h: Int,
    private val dim: Int
) {
    companion object {
        const val VERSION = "reid-1"
        // Input scaling (x - MEAN) / STD = x/255 -> [0,1], per the model metadata (min 0.0, max 1.0).
        const val MEAN = 0f
        const val STD = 255f
        fun load(f: File): EmbeddingEngine? = runCatching {
            val it = Interpreter(f)
            val s = it.getInputTensor(0).shape() // expects [1, H, W, 3] float32
            require(s.size == 4 && s[3] == 3)
            EmbeddingEngine(it, s[2], s[1], it.getOutputTensor(0).shape().last())
        }.getOrNull()
    }

    /** Input: RGB float32 scaled by MEAN/STD; see MODEL_SETUP.md. */
    fun embed(src: Bitmap): FloatArray {
        val bmp = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(4 * w * h * 3).order(ByteOrder.nativeOrder())
        for (p in px) {
            buf.putFloat((((p shr 16) and 255) - MEAN) / STD)
            buf.putFloat((((p shr 8) and 255) - MEAN) / STD)
            buf.putFloat(((p and 255) - MEAN) / STD)
        }
        buf.rewind()
        val out = Array(1) { FloatArray(dim) }
        interp.run(buf, out)
        return Similarity.normalize(out[0])
    }

    fun close() = interp.close()
}
