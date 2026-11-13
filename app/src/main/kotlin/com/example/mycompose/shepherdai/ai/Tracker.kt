package com.example.mycompose.shepherdai.ai

import kotlin.math.hypot
import kotlin.math.sqrt

data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val area get() = maxOf(0f, r - l) * maxOf(0f, b - t)
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f
    fun shifted(dx: Float, dy: Float) = Box(l + dx, t + dy, r + dx, b + dy)
    fun iou(o: Box): Float {
        val inter = maxOf(0f, minOf(r, o.r) - maxOf(l, o.l)) * maxOf(0f, minOf(b, o.b) - maxOf(t, o.t))
        val u = area + o.area - inter
        return if (u <= 0f) 0f else inter / u
    }
}

class Track(val id: Int, var box: Box, val born: Int = 0) {
    var hits = 1
    var missed = 0
    var lastSeen = born
    var vx = 0f // smoothed centre velocity per frame (includes camera motion)
    var vy = 0f
    var animalId: Long? = null
    var bestScore = 0f
    var lastTry = 0
    val embs = ArrayList<FloatArray>() // good-quality fingerprints collected for this track

    private var meanCache: FloatArray? = null
    private var meanN = 0

    /** Average fingerprint, or null until at least 2 good ones were collected. */
    fun mean(): FloatArray? {
        if (embs.size < 2) return null
        if (meanN != embs.size) { meanCache = Similarity.mean(embs); meanN = embs.size }
        return meanCache
    }

    /** Where the box should be in the next frame. Lost tracks stay where the camera pan left them. */
    fun predicted(): Box = if (missed == 0) box.shifted(vx, vy) else box
}

/**
 * IoU tracker with motion prediction and camera-pan compensation.
 *  - matching uses the predicted position, so a fast pan does not break the track;
 *  - tracks that were not matched follow the median motion of the matched ones (the camera moved, not the sheep);
 *  - when the count is requested, separate tracks with near-identical fingerprints that never overlapped in time
 *    are merged (the same sheep that left the view and came back is counted once).
 * Track ids are TEMPORARY (per scan session); permanent identity comes only from ReID.
 */
class SimpleTracker(
    private val iouMin: Float = 0.2f,
    private val maxMissed: Int = 45,
    val minHits: Int = 6,
    private val mergeSim: Float = 0.93f
) {
    private var nextId = 1
    private var frame = 0
    private val active = mutableListOf<Track>()
    val history = LinkedHashMap<Int, Track>()

    private var cachedCount = 0
    private var cachedAt = -1000

    /** Weak match for fast motion: centres close relative to the box size and similar box area. Returns 0..1. */
    private fun proximity(p: Box, d: Box): Float {
        if (p.area <= 0f || d.area <= 0f) return 0f
        val lim = 0.7f * sqrt(d.area)
        val dist = hypot(p.cx - d.cx, p.cy - d.cy)
        val ratio = d.area / p.area
        if (dist >= lim || ratio < 0.4f || ratio > 2.5f) return 0f
        return maxOf(0.01f, 1f - dist / lim)
    }

    private fun median(v: List<Float>): Float {
        if (v.isEmpty()) return 0f
        val s = v.sorted()
        return s[s.size / 2]
    }

    fun update(dets: List<Box>): List<Track> {
        frame++
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        active.forEachIndexed { ti, t ->
            val p = t.predicted()
            dets.forEachIndexed { di, d ->
                val v = p.iou(d)
                if (v >= iouMin) pairs.add(Triple(v, ti, di))
                else {
                    val s = proximity(p, d)
                    if (s > 0f) pairs.add(Triple(s * iouMin * 0.5f, ti, di)) // always ranks below a real IoU match
                }
            }
        }
        pairs.sortByDescending { it.first }
        val usedT = HashSet<Int>(); val usedD = HashSet<Int>(); val visible = mutableListOf<Track>()
        val dxs = ArrayList<Float>(); val dys = ArrayList<Float>()
        for ((_, ti, di) in pairs) {
            if (ti in usedT || di in usedD) continue
            usedT.add(ti); usedD.add(di)
            val t = active[ti]; val d = dets[di]
            val gap = (t.missed + 1).toFloat()
            val dx = d.cx - t.box.cx; val dy = d.cy - t.box.cy
            t.vx = 0.5f * t.vx + 0.5f * (dx / gap)
            t.vy = 0.5f * t.vy + 0.5f * (dy / gap)
            if (t.missed == 0) { dxs.add(dx); dys.add(dy) }
            t.box = d; t.hits++; t.missed = 0; t.lastSeen = frame; visible.add(t)
        }
        val panX = median(dxs); val panY = median(dys)
        active.forEachIndexed { ti, t ->
            if (ti !in usedT) {
                t.missed++
                if (dxs.isNotEmpty()) t.box = t.box.shifted(panX, panY)
            }
        }
        dets.forEachIndexed { di, d ->
            if (di !in usedD) { val t = Track(nextId++, d, frame); active.add(t); history[t.id] = t; visible.add(t) }
        }
        active.removeAll { it.missed > maxMissed }
        return visible
    }

    /**
     * Distinct identified animals + confirmed tracks still without identity, after merging tracks of the same
     * animal. The merge is O(n^2), so the result is cached for a few frames unless [force] is set.
     */
    fun uniqueCount(force: Boolean = false): Int {
        if (!force && frame - cachedAt < 5) return cachedCount
        val c = history.values.filter { it.hits >= minHits }
        val identified = c.mapNotNull { it.animalId }.toSet().size
        cachedCount = identified + mergedGroups(c.filter { it.animalId == null })
        cachedAt = frame
        return cachedCount
    }

    private fun mergedGroups(ts: List<Track>): Int {
        val parent = IntArray(ts.size) { it }
        fun find(x: Int): Int { var a = x; while (parent[a] != a) { parent[a] = parent[parent[a]]; a = parent[a] }; return a }
        val means = ts.map { it.mean() }
        for (i in ts.indices) {
            val mi = means[i] ?: continue
            for (j in i + 1 until ts.size) {
                val mj = means[j] ?: continue
                val a = ts[i]; val b = ts[j]
                val disjoint = a.lastSeen < b.born || b.lastSeen < a.born // never on screen together
                if (disjoint && Similarity.cosine(mi, mj) >= mergeSim) parent[find(i)] = find(j)
            }
        }
        return ts.indices.count { find(it) == it }
    }

    fun identifiedBest(): Map<Long, Float> {
        val m = HashMap<Long, Float>()
        for (t in history.values) { val id = t.animalId ?: continue; if (t.bestScore > (m[id] ?: -1f)) m[id] = t.bestScore }
        return m
    }
}
