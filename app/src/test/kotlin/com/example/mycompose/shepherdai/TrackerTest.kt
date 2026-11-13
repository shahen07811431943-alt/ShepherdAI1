package com.example.mycompose.shepherdai

import com.example.mycompose.shepherdai.ai.Box
import com.example.mycompose.shepherdai.ai.SimpleTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerTest {
    private val a = Box(0.1f, 0.1f, 0.3f, 0.3f)
    private val aMoved = Box(0.12f, 0.1f, 0.32f, 0.3f)
    private val b = Box(0.6f, 0.6f, 0.8f, 0.8f)

    @Test fun keepsIdAcrossFrames() {
        val t = SimpleTracker()
        val id = t.update(listOf(a)).single().id
        assertEquals(id, t.update(listOf(aMoved)).single().id)
    }

    @Test fun countsEachAnimalOnce() {
        val t = SimpleTracker()
        repeat(5) { t.update(listOf(a, b)) }
        assertEquals(2, t.uniqueCount())
    }

    @Test fun ignoresOneFrameNoise() {
        val t = SimpleTracker()
        t.update(listOf(a)); t.update(listOf(a)); t.update(listOf(a))
        t.update(listOf(a, b))
        assertEquals(1, t.uniqueCount())
    }
}
