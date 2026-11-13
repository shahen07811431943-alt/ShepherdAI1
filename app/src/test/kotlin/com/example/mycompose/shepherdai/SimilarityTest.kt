package com.example.mycompose.shepherdai

import com.example.mycompose.shepherdai.ai.IdResult
import com.example.mycompose.shepherdai.ai.Similarity
import com.example.mycompose.shepherdai.data.Animal
import com.example.mycompose.shepherdai.data.AnimalEmbedding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityTest {
    private fun animal(id: Long) = Animal(id, "$id", "a$id", "SHEEP", "FEMALE", "", "", "", 0)
    private fun emb(id: Long, v: FloatArray) = AnimalEmbedding(animalId = id, modelVersion = "t", data = Similarity.toBytes(v), createdAt = 0)
    private val animals = mapOf(1L to animal(1), 2L to animal(2))

    @Test fun bytesRoundTrip() {
        val v = floatArrayOf(0.5f, -1f, 2f)
        assertEquals(v.toList(), Similarity.fromBytes(Similarity.toBytes(v)).toList())
    }

    @Test fun matchesClosest() {
        val r = Similarity.identify(floatArrayOf(1f, 0f), listOf(emb(1, floatArrayOf(1f, 0.01f)), emb(2, floatArrayOf(0f, 1f))), animals, 0.8f)
        assertTrue(r is IdResult.Match && r.animal.id == 1L)
    }

    @Test fun rejectsBelowThreshold() {
        val r = Similarity.identify(floatArrayOf(1f, 0f), listOf(emb(2, floatArrayOf(0f, 1f))), animals, 0.8f)
        assertEquals(IdResult.Unknown, r)
    }

    @Test fun flagsAmbiguous() {
        val r = Similarity.identify(floatArrayOf(1f, 0f), listOf(emb(1, floatArrayOf(1f, 0.1f)), emb(2, floatArrayOf(1f, 0.11f))), animals, 0.8f)
        assertTrue(r is IdResult.Ambiguous)
    }
}

class FusionTest {
    @Test fun meanIsUnitLength() {
        val m = Similarity.mean(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
        assertEquals(1f, Math.sqrt((m[0] * m[0] + m[1] * m[1]).toDouble()).toFloat(), 1e-5f)
    }
}
