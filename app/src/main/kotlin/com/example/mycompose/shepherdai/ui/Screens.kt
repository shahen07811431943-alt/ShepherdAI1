package com.example.mycompose.shepherdai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mycompose.shepherdai.ai.EmbeddingEngine
import com.example.mycompose.shepherdai.ai.IdResult
import com.example.mycompose.shepherdai.ai.ModelManager
import com.example.mycompose.shepherdai.ai.Similarity
import com.example.mycompose.shepherdai.data.Animal
import com.example.mycompose.shepherdai.data.AnimalDao
import com.example.mycompose.shepherdai.data.AnimalEmbedding
import com.example.mycompose.shepherdai.data.AnimalImage
import com.example.mycompose.shepherdai.data.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(t))

@Composable
private fun rememberEngine(mm: ModelManager): EmbeddingEngine? {
    val e = remember { if (mm.isInstalled()) EmbeddingEngine.load(mm.identifierFile) else null }
    DisposableEffect(Unit) { onDispose { e?.close() } }
    return e
}

@Composable
fun HomeScreen(dao: AnimalDao, mm: ModelManager, go: (Screen) -> Unit) {
    val animals by dao.animals().collectAsState(emptyList())
    Page("الراعي الذكي", null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("إجمالي الحيوانات: ${animals.size}", style = MaterialTheme.typography.titleMedium)
                Text("الأغنام: ${animals.count { it.type == "SHEEP" }}   الماعز: ${animals.count { it.type == "GOAT" }}")
                Text(
                    if (mm.isInstalled()) "نموذج التعرف: مثبت" else "نموذج التعرف: غير مثبت — التعرف على الأفراد غير متاح",
                    color = if (mm.isInstalled()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        Button({ go(Screen.Scan) }, Modifier.fillMaxWidth()) { Text("فحص القطيع (كاميرا حية)") }
        Button({ go(Screen.Identify) }, Modifier.fillMaxWidth()) { Text("تعرّف على حيوان واحد") }
        Button({ go(Screen.Add) }, Modifier.fillMaxWidth()) { Text("تسجيل حيوان") }
        Button({ go(Screen.Animals) }, Modifier.fillMaxWidth()) { Text("الحيوانات") }
        OutlinedButton({ go(Screen.Settings) }, Modifier.fillMaxWidth()) { Text("الإعدادات والنماذج") }
    }
}

@Composable
fun AnimalsScreen(dao: AnimalDao, go: (Screen) -> Unit, back: () -> Unit) {
    val animals by dao.animals().collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    val shown = animals.filter {
        (filter.isEmpty() || it.type == filter) &&
            (q.isBlank() || it.name.contains(q, true) || it.tag.contains(q, true))
    }
    Page("الحيوانات", back, scroll = false) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("بحث بالرقم أو الاسم") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == "", { filter = "" }, { Text("الكل") })
            FilterChip(filter == "SHEEP", { filter = "SHEEP" }, { Text("أغنام") })
            FilterChip(filter == "GOAT", { filter = "GOAT" }, { Text("ماعز") })
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().clickable { go(Screen.Profile(a.id)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${a.tag}  ${a.name}", style = MaterialTheme.typography.titleMedium)
                        Text("${typeLabel(a.type)} • ${genderLabel(a.gender)}")
                    }
                }
            }
        }
        if (shown.isEmpty()) Text("لا توجد حيوانات مطابقة.")
    }
}

@Composable
fun AddScreen(dao: AnimalDao, mm: ModelManager, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = rememberEngine(mm)
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("SHEEP") }
    var gender by remember { mutableStateOf("FEMALE") }
    var age by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var camera by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<File>() }

    if (camera) {
        CameraCapture(onPhoto = { photos.add(it); camera = false }, onClose = { camera = false })
        return
    }
    Page("تسجيل حيوان", back) {
        OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("الرقم التعريفي *") }, singleLine = true)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("الاسم") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == "SHEEP", { type = "SHEEP" }, { Text("غنم") })
            FilterChip(type == "GOAT", { type = "GOAT" }, { Text("ماعز") })
            FilterChip(gender == "FEMALE", { gender = "FEMALE" }, { Text("أنثى") })
            FilterChip(gender == "MALE", { gender = "MALE" }, { Text("ذكر") })
        }
        OutlinedTextField(age, { age = it }, Modifier.fillMaxWidth(), label = { Text("العمر التقريبي") }, singleLine = true)
        OutlinedTextField(color, { color = it }, Modifier.fillMaxWidth(), label = { Text("اللون") }, singleLine = true)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("العلامات المميزة / ملاحظات") })
        OutlinedButton({ camera = true }, Modifier.fillMaxWidth()) { Text("التقاط صورة مرجعية (${photos.size})") }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(photos.toList()) { PhotoThumb(it.path) } }
        if (engine == null) Text("نموذج التعرف غير مثبت: ستُحفظ الصور فقط دون بصمة رقمية.", style = MaterialTheme.typography.bodySmall)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button({
            scope.launch {
                val t = tag.trim()
                if (t.isEmpty()) { error = "الرقم التعريفي مطلوب"; return@launch }
                if (dao.tagCount(t) > 0) { error = "هذا الرقم مستخدم لحيوان آخر"; return@launch }
                val now = System.currentTimeMillis()
                val id = dao.insert(Animal(tag = t, name = name.trim(), type = type, gender = gender,
                    approxAge = age.trim(), color = color.trim(), notes = notes.trim(), registeredAt = now))
                for (f in photos) {
                    dao.insertImage(AnimalImage(animalId = id, path = f.absolutePath, capturedAt = now))
                    if (engine != null) {
                        val v = withContext(Dispatchers.Default) { loadBitmap(f.path)?.let { engine.embed(it) } }
                        if (v != null) dao.insertEmbedding(AnimalEmbedding(animalId = id,
                            modelVersion = EmbeddingEngine.VERSION, data = Similarity.toBytes(v), createdAt = now))
                    }
                }
                back()
            }
        }, Modifier.fillMaxWidth()) { Text("حفظ") }
    }
}

@Composable
fun ProfileScreen(dao: AnimalDao, id: Long, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var animal by remember { mutableStateOf<Animal?>(null) }
    LaunchedEffect(id) { animal = dao.animal(id) }
    val images by dao.images(id).collectAsState(emptyList())
    val sightings by dao.sightings(id).collectAsState(emptyList())
    var confirm by remember { mutableStateOf(false) }
    val a = animal
    Page(a?.let { "#${it.tag} ${it.name}" } ?: "ملف الحيوان", back) {
        if (a == null) { Text("جارٍ التحميل..."); return@Page }
        Text("النوع: ${typeLabel(a.type)}   الجنس: ${genderLabel(a.gender)}")
        Text("العمر: ${a.approxAge.ifBlank { "—" }}   اللون: ${a.color.ifBlank { "—" }}")
        Text("تاريخ التسجيل: ${fmt(a.registeredAt)}")
        if (a.notes.isNotBlank()) Text("ملاحظات: ${a.notes}")
        Text("الصور المرجعية (${images.size})", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(images) { PhotoThumb(it.path) } }
        Text("سجل المشاهدات (${sightings.size})", style = MaterialTheme.typography.titleMedium)
        sightings.take(10).forEach { Text("${fmt(it.timestamp)} — ثقة ${"%.2f".format(it.confidence)}") }
        OutlinedButton({ confirm = true }, Modifier.fillMaxWidth()) { Text("حذف الحيوان") }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("سيتم حذف الحيوان وصوره وبصماته وسجله نهائياً.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.imagePaths(id).forEach { File(it).delete() } }
                        dao.delete(a)
                        back()
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun IdentifyScreen(dao: AnimalDao, mm: ModelManager, prefs: Prefs, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = rememberEngine(mm)
    var camera by remember { mutableStateOf(true) }
    var photo by remember { mutableStateOf<File?>(null) }
    var result by remember { mutableStateOf<IdResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    if (engine == null) {
        Page("الكاميرا الذكية", back) {
            Text("نموذج التعرف على الأفراد (animal_identifier.tflite) غير مثبت.")
            Text("لن يدّعي التطبيق التعرف على أي حيوان دون نموذج حقيقي. ثبّت النموذج من الإعدادات.")
        }
        return
    }
    if (camera) {
        CameraCapture(onClose = back, onPhoto = { f ->
            camera = false; photo = f; result = null; saved = false; busy = true
            scope.launch {
                val animals = dao.animalsOnce().associateBy { it.id }
                result = withContext(Dispatchers.Default) {
                    val bmp = loadBitmap(f.path)
                    if (bmp == null) IdResult.Unknown
                    else Similarity.identify(engine.embed(bmp), dao.allEmbeddings(), animals, prefs.threshold)
                }
                busy = false
            }
        })
        return
    }
    Page("نتيجة التعرف", back) {
        photo?.let { PhotoThumb(it.path, 200.dp) }
        if (busy) Text("جارٍ التحليل...")
        when (val r = result) {
            is IdResult.Match -> {
                Text("الحيوان #${r.animal.tag} ${r.animal.name}", style = MaterialTheme.typography.titleLarge)
                Text("درجة التشابه: ${"%.2f".format(r.score)} (العتبة ${"%.2f".format(prefs.threshold)}). النتيجة تحتاج تأكيدك.")
                Button({
                    scope.launch {
                        dao.insertSighting(Sighting(animalId = r.animal.id, timestamp = System.currentTimeMillis(),
                            confidence = r.score, imagePath = photo?.path ?: ""))
                        saved = true
                    }
                }, enabled = !saved) { Text(if (saved) "تم تسجيل المشاهدة" else "تأكيد وتسجيل مشاهدة") }
            }
            is IdResult.Ambiguous -> Text("متشابه مع #${r.first.tag} و #${r.second.tag}. تحقق يدوياً ولا تعتمد على النتيجة.")
            IdResult.Unknown -> Text("حيوان غير معروف أو صورة غير كافية. أعد التصوير أو سجّله كحيوان جديد.")
            null -> {}
        }
        Button({ camera = true }, Modifier.fillMaxWidth()) { Text("تصوير حيوان آخر") }
    }
}

@Composable
fun SettingsScreen(mm: ModelManager, prefs: Prefs, back: () -> Unit) {
    var th by remember { mutableStateOf(prefs.threshold) }
    var ds by remember { mutableStateOf(prefs.detScore) }
    var cls by remember { mutableStateOf(prefs.sheepClass.toString()) }
    var installed by remember { mutableStateOf(mm.isInstalled()) }
    var detInstalled by remember { mutableStateOf(mm.isDetectorInstalled()) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { mm.import(uri) }
            installed = mm.isInstalled()
            msg = if (ok) "تم تثبيت نموذج التعرف والتحقق من تحميله" else "الملف غير صالح كنموذج ReID (المدخل [1,H,W,3])"
        }
    }
    val detPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { mm.importDetector(uri) }
            detInstalled = mm.isDetectorInstalled()
            msg = if (ok) "تم تثبيت الكاشف والتحقق من تحميله" else "الملف غير صالح ككاشف (مدخل uint8 [1,H,W,3] ومخرج صناديق)"
        }
    }
    Page("الإعدادات", back) {
        Text("الكاشف (sheep_goat_detector.tflite)", style = MaterialTheme.typography.titleMedium)
        Text(if (detInstalled) "مثبت" else "غير مثبت — فحص القطيع غير متاح")
        Button({ detPicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("اختيار ملف الكاشف .tflite") }
        Text("حد ثقة الاكتشاف: ${"%.2f".format(ds)}")
        Slider(ds, { ds = it; prefs.detScore = it }, valueRange = 0.2f..0.9f)
        OutlinedTextField(cls, { cls = it; it.toIntOrNull()?.let { v -> prefs.sheepClass = v } }, Modifier.fillMaxWidth(),
            label = { Text("رقم فئة الغنم في الكاشف (-1 = كل الفئات)") }, singleLine = true)
        Text("أثناء الفحص يظهر رقم الفئات المكتشفة؛ اضبط الرقم الصحيح من هناك.", style = MaterialTheme.typography.bodySmall)
        Text("نموذج التعرف (ReID)", style = MaterialTheme.typography.titleMedium)
        Text(if (installed) "مثبت — بصمة الملف: ${mm.sha256()}" else "غير مثبت")
        Button({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("اختيار ملف ReID .tflite") }
        Text("عتبة التعرف: ${"%.2f".format(th)}")
        Slider(th, { th = it; prefs.threshold = it }, valueRange = 0.5f..0.99f)
        Text("العتبة ليست نسبة دقة؛ عدّلها بعد الاختبار على حيوانات حقيقية.", style = MaterialTheme.typography.bodySmall)
        if (msg.isNotEmpty()) Text(msg)
    }
}
