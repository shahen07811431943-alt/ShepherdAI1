package com.example.mycompose.shepherdai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.mycompose.shepherdai.ai.AnimalDetector
import com.example.mycompose.shepherdai.ai.EmbeddingEngine
import com.example.mycompose.shepherdai.ai.FrameResult
import com.example.mycompose.shepherdai.ai.ModelManager
import com.example.mycompose.shepherdai.ai.ScanEngine
import com.example.mycompose.shepherdai.data.AnimalDao
import com.example.mycompose.shepherdai.data.Sighting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private data class Report(val counted: Int, val registered: Int, val notSeen: List<String>?, val seen: Int)

@Composable
fun ScanScreen(dao: AnimalDao, mm: ModelManager, prefs: Prefs, back: () -> Unit) {
    if (!mm.isDetectorInstalled()) {
        Page("فحص القطيع", back) {
            Text("الكاشف (sheep_goat_detector.tflite) غير مثبت. ثبّته من الإعدادات؛ لا يوجد كشف دون نموذج حقيقي.")
        }
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Page("فحص القطيع", back) { Text("إذن الكاميرا مطلوب."); Button({ launcher.launch(Manifest.permission.CAMERA) }) { Text("منح الإذن") } }
        return
    }

    val detector = remember { AnimalDetector.load(mm.detectorFile) }
    if (detector == null) { Page("فحص القطيع", back) { Text("تعذر تحميل الكاشف. أعد تثبيته من الإعدادات.") }; return }
    val reid = remember { if (mm.isInstalled()) EmbeddingEngine.load(mm.identifierFile) else null }
    val engineRef = remember { AtomicReference<ScanEngine?>(null) }
    val frames = remember { MutableStateFlow(FrameResult()) }
    val frame by frames.collectAsState()
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Report?>(null) }

    LaunchedEffect(Unit) {
        val animals = dao.animalsOnce().associateBy { it.id }
        engineRef.set(ScanEngine(detector, reid, animals, dao.allEmbeddings(), prefs.threshold, prefs.detScore, prefs.sheepClass))
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
            executor.shutdown()
            executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
            detector.close(); reid?.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(3f / 4f)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                PreviewView(c).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                    val fut = ProcessCameraProvider.getInstance(c)
                    fut.addListener({
                        val provider = fut.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            try {
                                val e = engineRef.get()
                                if (e != null) {
                                    val raw = proxy.toBitmap()
                                    val rot = proxy.imageInfo.rotationDegrees
                                    val bmp: Bitmap = if (rot == 0) raw else
                                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                                    frames.value = e.process(bmp)
                                }
                            } catch (_: Exception) {
                            } finally { proxy.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(c as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(c))
                }
            })
            Canvas(Modifier.fillMaxSize()) {
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 34f; isAntiAlias = true }
                frame.boxes.forEach { b ->
                    val x = b.box.l * size.width; val y = b.box.t * size.height
                    drawRect(Color(0xFF66BB6A), Offset(x, y), Size((b.box.r - b.box.l) * size.width, (b.box.b - b.box.t) * size.height), style = Stroke(4f))
                    drawContext.canvas.nativeCanvas.drawText(b.label, x + 6f, y + 36f, paint)
                }
            }
        }
        Card(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("ظاهر الآن: ${frame.visible}    المرصود منذ البدء: ${frame.counted}", style = MaterialTheme.typography.titleMedium)
                if (reid == null) Text("نموذج التعرف غير مثبت: العدّ فقط دون هوية.", style = MaterialTheme.typography.bodySmall)
                Text("فئات مكتشفة: ${frame.classes.ifEmpty { "—" }}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!scanning) Button({ engineRef.get()?.reset(); scanning = true }, Modifier.fillMaxWidth()) { Text("بدء الفحص") }
            else Button({
                scanning = false
                val e = engineRef.get()
                if (e != null) scope.launch {
                    val best = e.identifiedBest()
                    best.forEach { (id, sc) -> dao.insertSighting(Sighting(animalId = id, timestamp = System.currentTimeMillis(), confidence = sc, imagePath = "")) }
                    val reg = dao.animalsOnce().filter { it.active }
                    report = Report(e.count(), reg.size,
                        if (reid != null) reg.filter { it.id !in best }.map { "#${it.tag} ${it.name}" } else null, best.size)
                }
            }, Modifier.fillMaxWidth()) { Text("إيقاف وعرض التقرير") }
            Button(back, Modifier.fillMaxWidth()) { Text("رجوع") }
        }
    }
    report?.let { r ->
        val diff = r.counted - r.registered
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("تقرير الجولة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المرصود: ${r.counted}   المسجّل: ${r.registered}")
                    Text(when {
                        diff < 0 -> "المرصود أقل بـ ${-diff}: قد يكون بعضها خارج مجال الكاميرا أو بعيداً."
                        diff > 0 -> "المرصود أكثر بـ $diff: حيوان جديد غير مسجل، أو تتبع كرر العدّ."
                        else -> "العدد المرصود يساوي المسجّل."
                    })
                    if (r.notSeen == null) Text("لا يمكن تحديد الناقص بالاسم دون نموذج التعرف.")
                    else if (r.notSeen.isEmpty()) Text("ظهرت كل الحيوانات المسجلة.")
                    else Text("لم تظهر في هذه الجولة: ${r.notSeen.joinToString("، ")}. غياب الظهور لا يعني أنها مفقودة.")
                }
            },
            confirmButton = { TextButton({ report = null }) { Text("حسناً") } }
        )
    }
}
