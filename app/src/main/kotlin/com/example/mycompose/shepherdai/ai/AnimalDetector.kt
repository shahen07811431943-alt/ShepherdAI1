package com.example.mycompose.shepherdai.ai

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Detection(val box: Box, val score: Float, val cls: Int)

/**
 * Object detector for EfficientDet-Lite style models: uint8 input [1,H,W,3];
 * outputs boxes [1,N,4] (ymin,xmin,ymax,xmax normalized), classes [1,N], scores [1,N], count [1].
 */
class AnimalDetector private constructor(private val interp: Interpreter, private val w: Int, private val h: Int) {
    companion object {
        fun load(f: File): AnimalDetector? = runCatching {
            val it = Interpreter(f)
            val s = it.getInputTensor(0).shape()
            require(s.size == 4 && s[3] == 3 && it.getInputTensor(0).dataType() == DataType.UINT8)
            require((0 until it.outputTensorCount).any { i -> it.getOutputTensor(i).shape().size == 3 })
            AnimalDetector(it, s[2], s[1])
        }.getOrNull()
    }

    private fun integral(a: FloatArray) = a.all { it == Math.floor(it.toDouble()).toFloat() }

    fun detect(src: Bitmap): List<Detection> {
        val bmp = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder())
        for (p in px) { buf.put(((p shr 16) and 255).toByte()); buf.put(((p shr 8) and 255).toByte()); buf.put((p and 255).toByte()) }
        buf.rewind()
        val outs = HashMap<Int, Any>()
        for (i in 0 until interp.outputTensorCount) {
            val sh = interp.getOutputTensor(i).shape()
            outs[i] = when (sh.size) {
                3 -> Array(sh[0]) { Array(sh[1]) { FloatArray(sh[2]) } }
                2 -> Array(sh[0]) { FloatArray(sh[1]) }
                else -> FloatArray(sh[0])
            }
        }
        interp.runForMultipleInputsOutputs(arrayOf<Any>(buf), outs)
        var boxes: Array<FloatArray>? = null
        val lists = ArrayList<FloatArray>()
        for (i in 0 until interp.outputTensorCount) {
            val o = outs[i]
            @Suppress("UNCHECKED_CAST")
            when (interp.getOutputTensor(i).shape().size) {
                3 -> boxes = (o as Array<Array<FloatArray>>)[0]
                2 -> lists.add((o as Array<FloatArray>)[0])
            }
        }
        if (boxes == null || lists.size < 2) return emptyList()
        var cls = lists[0]; var sc = lists[1]
        if (!integral(cls) && integral(sc)) { val t = cls; cls = sc; sc = t }
        return boxes.indices.map { i ->
            val b = boxes[i]
            Detection(Box(b[1].coerceIn(0f, 1f), b[0].coerceIn(0f, 1f), b[3].coerceIn(0f, 1f), b[2].coerceIn(0f, 1f)), sc[i], cls[i].toInt())
        }
    }

    fun close() = interp.close()
}
