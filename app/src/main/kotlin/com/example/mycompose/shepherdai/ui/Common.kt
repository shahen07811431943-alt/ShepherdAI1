package com.example.mycompose.shepherdai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun loadBitmap(path: String, max: Int = 1024): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var s = 1
    while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
    val bm = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val deg = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90; 3 -> 180; 8 -> 270; else -> 0
        }
    }.getOrDefault(0)
    return if (deg == 0) bm else Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
}

@Composable
fun PhotoThumb(path: String, size: Dp = 96.dp) {
    val img by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { loadBitmap(path, 400)?.asImageBitmap() }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var threshold: Float
        get() = sp.getFloat("threshold", 0.80f)
        set(v) { sp.edit().putFloat("threshold", v).apply() }
    var detScore: Float
        get() = sp.getFloat("detScore", 0.4f)
        set(v) { sp.edit().putFloat("detScore", v).apply() }
    /** Class index of "sheep" in the detector label map; -1 accepts every class. */
    var sheepClass: Int
        get() = sp.getInt("sheepClass", 19)
        set(v) { sp.edit().putInt("sheepClass", v).apply() }
}

fun typeLabel(t: String) = if (t == "SHEEP") "غنم" else "ماعز"
fun genderLabel(g: String) = if (g == "FEMALE") "أنثى" else "ذكر"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(title: String, onBack: (() -> Unit)?, scroll: Boolean = true, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            }
        )
    }) { pad: PaddingValues ->
        val m = Modifier.padding(pad).padding(16.dp).fillMaxSize()
        Column(if (scroll) m.verticalScroll(rememberScrollState()) else m, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
