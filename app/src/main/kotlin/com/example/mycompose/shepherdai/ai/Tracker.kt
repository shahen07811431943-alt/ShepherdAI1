package com.example.mycompose.shepherdai.ai

data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val area get() = maxOf(0f, r - l) * maxOf(0f, b - t)
    fun iou(o: Box): Float {
        val inter = maxOf(0f, minOf(r, o.r) - maxOf(l, o.l)) * maxOf(0f, minOf(b, o.b) - maxOf(t, o.t))
        val u = area + o.area - inter
        return if (u <= 0f) 0f else inter / u
    }
}

class Track(val id: Int, var box: Box) {
    var hits = 1
    var missed = 0
    var animalId: Long? = null
    var bestScore = 0f
    var lastTry = 0
    val embs = ArrayList<FloatArray>() // good-quality fingerprints collected for this track
}

/**
 * IoU tracker. Track ids are TEMPORARY (per scan session); permanent identity comes only from ReID.
 * Without ReID, a sheep that leaves and returns gets a new id and can be counted twice.
 */
class SimpleTracker(private val iouMin: Float = 0.2f, private val maxMissed: Int = 20, val minHits: Int = 3) {
    private var nextId = 1
    private val active = mutableListOf<Track>()
    val history = LinkedHashMap<Int, Track>()

    fun update(dets: List<Box>): List<Track> {
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        active.forEachIndexed { ti, t -> dets.forEachIndexed { di, d -> val v = t.box.iou(d); if (v >= iouMin) pairs.add(Triple(v, ti, di)) } }
        pairs.sortByDescending { it.first }
        val usedT = HashSet<Int>(); val usedD = HashSet<Int>(); val visible = mutableListOf<Track>()
        for ((_, ti, di) in pairs) {
            if (ti in usedT || di in usedD) continue
            usedT.add(ti); usedD.add(di)
            val t = active[ti]; t.box = dets[di]; t.hits++; t.missed = 0; visible.add(t)
        }
        active.forEachIndexed { ti, t -> if (ti !in usedT) t.missed++ }
        dets.forEachIndexed { di, d ->
            if (di !in usedD) { val t = Track(nextId++, d); active.add(t); history[t.id] = t; visible.add(t) }
        }
        active.removeAll { it.missed > maxMissed }
        return visible
    }

    /** Distinct identified animals + confirmed tracks still without identity. */
    fun uniqueCount(): Int {
        val c = history.values.filter { it.hits >= minHits }
        return c.mapNotNull { it.animalId }.toSet().size + c.count { it.animalId == null }
    }

    fun identifiedBest(): Map<Long, Float> {
        val m = HashMap<Long, Float>()
        for (t in history.values) { val id = t.animalId ?: continue; if (t.bestScore > (m[id] ?: -1f)) m[id] = t.bestScore }
        return m
    }
}
