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
        repeat(10) { t.update(listOf(a, b)) }
        assertEquals(2, t.uniqueCount(true))
    }

    @Test fun ignoresShortNoise() {
        val t = SimpleTracker()
        repeat(10) { t.update(listOf(a)) }
        t.update(listOf(a, b)); t.update(listOf(a, b)) // b seen for only 2 frames
        assertEquals(1, t.uniqueCount(true))
    }

    @Test fun followsAFastCameraPan() {
        val t = SimpleTracker()
        val xs = listOf(0.10f, 0.22f, 0.34f, 0.46f, 0.66f) // the last step is much bigger than the earlier ones
        val ids = xs.map { x -> t.update(listOf(Box(x, 0.3f, x + 0.2f, 0.5f))).single().id }
        assertEquals(1, ids.toSet().size)
    }

    @Test fun lostTrackFollowsThePanAndIsRecovered() {
        val t = SimpleTracker()
        val still = Box(0.1f, 0.1f, 0.3f, 0.3f)
        val moving = Box(0.5f, 0.1f, 0.7f, 0.3f)
        t.update(listOf(still, moving)); t.update(listOf(still, moving))
        val idStill = t.history.values.first { it.box == still }.id
        // `moving` is hidden for 5 frames while `still` shifts 0.02 per frame (camera pan)
        var s = still
        repeat(5) { s = s.shifted(0.02f, 0f); t.update(listOf(s)) }
        // it comes back where the pan put it
        val back = moving.shifted(0.1f, 0f)
        val ids = t.update(listOf(s, back)).map { it.id }
        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size)
        assertEquals(true, idStill in ids)
        assertEquals(2, t.history.size) // no third track was created
    }

    private fun sheepPassesTwice(e1: FloatArray, e2: FloatArray): SimpleTracker {
        val t = SimpleTracker()
        repeat(8) { t.update(listOf(a)) }
        t.history.values.single().embs.apply { add(e1); add(e1) }
        repeat(60) { t.update(emptyList()) } // the first track expires
        repeat(8) { t.update(listOf(a)) }
        t.history.values.last().embs.apply { add(e2); add(e2) }
        return t
    }

    @Test fun sameSheepAfterLeavingViewIsCountedOnce() {
        val e = floatArrayOf(1f, 0f, 0f)
        assertEquals(1, sheepPassesTwice(e, e).uniqueCount(true))
    }

    @Test fun differentSheepAreNotMerged() {
        val t = sheepPassesTwice(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))
        assertEquals(2, t.uniqueCount(true))
    }
}
