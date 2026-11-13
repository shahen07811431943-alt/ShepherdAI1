package com.example.mycompose.shepherdai.ai

import com.example.mycompose.shepherdai.data.Animal
import com.example.mycompose.shepherdai.data.AnimalEmbedding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

sealed interface IdResult {
    data class Match(val animal: Animal, val score: Float) : IdResult
    data class Ambiguous(val first: Animal, val second: Animal) : IdResult
    data object Unknown : IdResult
}

object Similarity {
    fun normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        return if (n == 0f) v else FloatArray(v.size) { v[it] / n }
    }

    fun mean(vs: List<FloatArray>): FloatArray {
        val out = FloatArray(vs.first().size)
        for (v in vs) for (i in out.indices) out[i] += v[i]
        return normalize(out)
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val d = sqrt(na) * sqrt(nb)
        return if (d == 0f) -1f else dot / d
    }

    fun toBytes(v: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { b.putFloat(it) }
        return b.array()
    }

    fun fromBytes(d: ByteArray): FloatArray {
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(d.size / 4) { b.getFloat() }
    }

    /** Best score per animal; refuses below [threshold] and refuses when two animals are too close. */
    fun identify(
        query: FloatArray,
        embeddings: List<AnimalEmbedding>,
        animals: Map<Long, Animal>,
        threshold: Float,
        margin: Float = 0.03f
    ): IdResult {
        val best = HashMap<Long, Float>()
        for (e in embeddings) {
            val s = cosine(query, fromBytes(e.data))
            if (s > (best[e.animalId] ?: -2f)) best[e.animalId] = s
        }
        val ranked = best.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return IdResult.Unknown
        if (top.value < threshold) return IdResult.Unknown
        val a = animals[top.key] ?: return IdResult.Unknown
        val second = ranked.getOrNull(1)
        if (second != null && second.value >= threshold && top.value - second.value < margin) {
            val b = animals[second.key]
            if (b != null) return IdResult.Ambiguous(a, b)
        }
        return IdResult.Match(a, top.value)
    }
}
