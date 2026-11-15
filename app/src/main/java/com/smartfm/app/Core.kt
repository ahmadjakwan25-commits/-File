package com.smartfm.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

var LANG by mutableStateOf("bn")
fun tr(bn: String, en: String) = if (LANG == "bn") bn else en
fun num(s: String): String =
    if (LANG == "bn") s.map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("") else s

fun sizeStr(n: Long): String {
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var d = n.toDouble(); var i = 0
    while (d >= 1024 && i < 4) { d /= 1024; i++ }
    return num(if (i == 0) "$n B" else String.format(Locale.US, "%.1f %s", d, u[i]))
}
fun dateStr(t: Long) = num(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(t)))

enum class Cat(val bn: String, val en: String, val icon: String, val exts: Set<String>) {
    IMG("ছবি", "Images", "🖼️", setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic")),
    VID("ভিডিও", "Videos", "🎬", setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")),
    AUD("অডিও", "Audio", "🎵", setOf("mp3", "wav", "ogg", "m4a", "aac", "opus", "amr")),
    DOC("ডকুমেন্ট", "Documents", "📄", setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "rtf", "odt")),
    ARC("আর্কাইভ", "Archives", "🗜️", setOf("zip", "rar", "7z", "tar", "gz")),
    APK("APK", "APK", "🤖", setOf("apk")),
    OTH("অন্যান্য", "Other", "📎", emptySet());
    val label get() = tr(bn, en)
}
class Rec(val f: File, val size: Long, val mod: Long, val cat: Cat)
fun catOf(f: File): Cat { val e = f.extension.lowercase(); return Cat.values().firstOrNull { e in it.exts } ?: Cat.OTH }

object P {
    val root: File get() = Environment.getExternalStorageDirectory()
    val hidden: File get() = File(root, ".SmartFM").apply { mkdirs(); File(this, ".nomedia").createNewFile() }
    val bin: File get() = File(hidden, "bin").apply { mkdirs() }
    val priv: File get() = File(hidden, "private").apply { mkdirs() }
    val quar: File get() = File(root, "Virus_Quarantine").apply { mkdirs() }
}

private val RISKY = setOf("exe", "bat", "cmd", "scr", "vbs", "js", "jar", "msi", "com", "pif", "ps1")
private val DOUBLE = Regex(".*\\.(pdf|jpe?g|png|docx?|xlsx?|mp4|mp3|txt|zip)\\.(exe|bat|scr|vbs|cmd|apk|js|jar)$")
private val BADNAME = Regex("crack|keygen|hack|patch|loader|mod.?menu|cheat")
fun why(f: File): List<String> {
    val n = f.name.lowercase(); val e = f.extension.lowercase(); val r = ArrayList<String>()
    if (DOUBLE.matches(n)) r += tr("দ্বৈত এক্সটেনশন", "Double extension")
    else if (e in RISKY) r += tr("ঝুঁকিপূর্ণ এক্সটেনশন (.$e)", "Risky extension (.$e)")
    if (e == "apk") r += tr("APK ফাইল (অজানা উৎস হলে ঝুঁকিপূর্ণ)", "APK file (risky if from unknown source)")
    if (BADNAME.containsMatchIn(n)) r += tr("ঝুঁকিপূর্ণ নাম", "Risky name")
    return r
}

fun uriFor(c: Context, f: File): Uri = FileProvider.getUriForFile(c, "${c.packageName}.fp", f)
fun openFile(c: Context, f: File) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    try {
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(c, f), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { Toast.makeText(c, tr("খোলার মতো অ্যাপ পাওয়া যায়নি", "No app can open this"), Toast.LENGTH_SHORT).show() }
}
fun shareFiles(c: Context, fs: List<File>) {
    val files = fs.filter { it.isFile }
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { uriFor(c, it) })
    val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    i.type = "*/*"; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, tr("শেয়ার করুন", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
fun fileHash(f: File): String {
    val md = MessageDigest.getInstance("SHA-256"); val b = ByteArray(65536)
    f.inputStream().use { s -> while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) }
}

sealed class Dlg {
    class Info(val f: File) : Dlg()
    class Ask(val title: String, val def: String, val pw: Boolean, val ok: (String) -> Unit) : Dlg()
    class Sure(val msg: String, val ok: () -> Unit) : Dlg()
    class Pick(val ok: (File) -> Unit) : Dlg()
    class Lines(val title: String, val lines: List<String>) : Dlg()
}

class VM(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    private val p = ctx.getSharedPreferences("sfm", 0)
    var screen by mutableStateOf("home")
    val stack = ArrayList<String>()
    var cwd by mutableStateOf(P.root)
    var dark by mutableStateOf(p.getBoolean("dark", false))
    var grid by mutableStateOf(p.getBoolean("grid", false))
    var showSize by mutableStateOf(p.getBoolean("size", true))
    var showDate by mutableStateOf(p.getBoolean("date", true))
    var notif by mutableStateOf(p.getBoolean("notif", true))
    var bio by mutableStateOf(p.getBoolean("bio", false))
    var pin by mutableStateOf(p.getString("pin", "") ?: "")
    var locked by mutableStateOf(pin.isNotEmpty())
    var privOK by mutableStateOf(false)
    var sort by mutableStateOf("name")
    var asc by mutableStateOf(true)
    var flt by mutableStateOf<Cat?>(null)
    var selMode by mutableStateOf(false)
    val sel = mutableStateListOf<String>()
    val favs = mutableStateListOf<String>().apply { addAll(p.getStringSet("favs", emptySet()) ?: emptySet()) }
    var all by mutableStateOf<List<File>>(emptyList())
    var scanning by mutableStateOf(false)
    var recs by mutableStateOf<List<Rec>>(emptyList())
    var catCount by mutableStateOf<Map<Cat, Int>>(emptyMap())
    var catSize by mutableStateOf<Map<Cat, Long>>(emptyMap())
    var recent by mutableStateOf<List<File>>(emptyList())
    var largest by mutableStateOf<List<File>>(emptyList())
    var susList by mutableStateOf<List<File>>(emptyList())
    var apkList by mutableStateOf<List<File>>(emptyList())
    var arcList by mutableStateOf<List<File>>(emptyList())
    var tick by mutableStateOf(0)
    var dlg by mutableStateOf<Dlg?>(null)
    private var job: Job? = null

    init { LANG = p.getString("lang", "bn") ?: "bn" }

    fun setPref(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }
    fun setLang(l: String) { LANG = l; p.edit().putString("lang", l).apply() }
    fun savePin(s: String) { pin = if (s.isEmpty()) "" else sha(s); p.edit().putString("pin", pin).apply() }
    fun toast(s: String) { if (notif) viewModelScope.launch(Dispatchers.Main) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() } }

    fun go(s: String) { stack.add(screen); screen = s; clearSel() }
    fun nav(s: String) { stack.clear(); screen = s; clearSel() }
    fun back() { screen = if (stack.isEmpty()) "home" else stack.removeAt(stack.size - 1); clearSel() }
    fun clearSel() { sel.clear(); selMode = false }
    fun toggleSel(f: File) { val k = f.absolutePath; if (k in sel) sel.remove(k) else sel.add(k); if (sel.isEmpty()) selMode = false }
    fun selFiles() = sel.map { File(it) }
    fun tap(f: File) { if (f.isDirectory) { if (screen != "files") { stack.clear(); screen = "files" }; cwd = f } else dlg = Dlg.Info(f) }
    fun toggleFav(f: File) { val k = f.absolutePath; if (k in favs) favs.remove(k) else favs.add(k); p.edit().putStringSet("favs", favs.toSet()).apply() }

    fun list(): List<File> {
        val a = (cwd.listFiles()?.toList() ?: emptyList()).filter { !it.name.startsWith(".") && (flt == null || it.isDirectory || catOf(it) == flt) }
        val e = a.map { Rec(it, if (it.isDirectory) 0L else it.length(), it.lastModified(), Cat.OTH) }
        val cmp: Comparator<Rec> = when (sort) {
            "size" -> compareBy<Rec> { it.size }
            "date" -> compareBy<Rec> { it.mod }
            else -> Comparator<Rec> { x, y -> x.f.name.compareTo(y.f.name, true) }
        }
        val s = e.sortedWith(if (asc) cmp else cmp.reversed()).map { it.f }
        return s.filter { it.isDirectory } + s.filter { !it.isDirectory }
    }

    fun scan() {
        job?.cancel()
        scanning = true
        job = viewModelScope.launch(Dispatchers.IO) {
            val q = P.quar
            val r = P.root.walkTopDown().onEnter { isActive && it.name != "Android" && !it.name.startsWith(".") }
                .filter { it.isFile && !it.name.startsWith(".") && !it.startsWith(q) }.toList()
            val rc = r.map { Rec(it, it.length(), it.lastModified(), catOf(it)) }
            val cc = rc.groupingBy { it.cat }.eachCount()
            val cs = rc.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.size } }
            val rec = rc.sortedByDescending { it.mod }.take(6).map { it.f }
            val big = rc.sortedByDescending { it.size }.take(15).map { it.f }
            val sl = r.filter { why(it).isNotEmpty() }
            val ap = r.filter { it.extension.equals("apk", true) }
            val ar = rc.filter { it.cat == Cat.ARC }.map { it.f }
            withContext(Dispatchers.Main) {
                all = r; recs = rc; catCount = cc; catSize = cs; recent = rec; largest = big; susList = sl; apkList = ap; arcList = ar; scanning = false
            }
        }
    }
    fun refresh() { tick++; scan() }
    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) { toast(tr("ত্রুটি: ", "Error: ") + (e.message ?: "")) }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun uniq(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        val b = f.nameWithoutExtension; val e = f.extension.let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) { f = File(dir, "$b ($i)$e"); i++ }
        return f
    }
    fun mkdir(n: String) { val d = File(cwd, n.trim().replace("/", "")); if (n.isNotBlank() && !d.exists()) d.mkdirs(); refresh() }
    fun rename(f: File, n: String) { if (n.isNotBlank()) { f.renameTo(uniq(f.parentFile!!, n.trim().replace("/", ""))); favs.remove(f.absolutePath) }; refresh() }
    fun copy(fs: List<File>, dst: File) = io {
        fs.forEach { if (!dst.startsWith(it) || !it.isDirectory) it.copyRecursively(uniq(dst, it.name)) }
        toast(tr("কপি হয়েছে", "Copied"))
    }
    fun move(fs: List<File>, dst: File) = io {
        fs.forEach { val t = uniq(dst, it.name); if (!dst.startsWith(it) && !it.renameTo(t)) { it.copyRecursively(t); it.deleteRecursively() } }
        toast(tr("সরানো হয়েছে", "Moved"))
    }

    // ---- meta (original paths) ----
    private fun meta(k: String): MutableMap<String, String> {
        val j = JSONObject(p.getString("m_$k", "{}") ?: "{}"); val m = LinkedHashMap<String, String>()
        j.keys().forEach { m[it] = j.getString(it) }; return m
    }
    private fun saveMeta(k: String, m: Map<String, String>) { p.edit().putString("m_$k", JSONObject(m).toString()).apply() }
    fun origOf(kind: String, f: File) = meta(kind)[f.name]?.substringBefore("|") ?: ""
    fun delTime(f: File) = meta("bin")[f.name]?.substringAfter("|")?.toLongOrNull() ?: f.lastModified()

    // ---- recycle bin ----
    fun trash(fs: List<File>) = io {
        val m = meta("bin")
        fs.forEach {
            val t = File(P.bin, "${System.currentTimeMillis()}_${it.name}")
            if (it.renameTo(t) || (it.copyRecursively(t) && it.deleteRecursively())) m[t.name] = it.absolutePath + "|" + System.currentTimeMillis()
        }
        saveMeta("bin", m); toast(tr("রিসাইকেল বিনে পাঠানো হয়েছে", "Moved to recycle bin"))
    }
    fun binFiles() = (P.bin.listFiles()?.toList() ?: emptyList()).sortedByDescending { delTime(it) }
    fun restore(f: File) = io {
        val m = meta("bin"); val o = File(m[f.name]?.substringBefore("|") ?: (P.root.path + "/" + f.name))
        val d = o.parentFile ?: P.root; d.mkdirs()
        val t = uniq(d, o.name); if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() }
        m.remove(f.name); saveMeta("bin", m); toast(tr("পুনরুদ্ধার হয়েছে", "Restored"))
    }
    fun purge(fs: List<File>) = io {
        val m = meta("bin"); fs.forEach { it.deleteRecursively(); m.remove(it.name) }; saveMeta("bin", m)
    }

    // ---- private folder ----
    fun toPrivate(f: File) = io {
        val m = meta("priv"); val t = uniq(P.priv, f.name)
        if (f.renameTo(t) || (f.copyRecursively(t) && f.deleteRecursively())) m[t.name] = f.absolutePath
        saveMeta("priv", m); toast(tr("প্রাইভেট ফোল্ডারে নেওয়া হয়েছে", "Moved to private folder"))
    }
    fun privFiles() = (P.priv.listFiles()?.toList() ?: emptyList()).sortedBy { it.name.lowercase() }
    fun fromPrivate(f: File) = io {
        val m = meta("priv"); val o = File(m[f.name] ?: (P.root.path + "/" + f.name)); val d = o.parentFile ?: P.root; d.mkdirs()
        val t = uniq(d, o.name); if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() }
        m.remove(f.name); saveMeta("priv", m)
    }

    // ---- quarantine ----
    fun suspicious() = susList
    fun quarantine(fs: List<File>) = io {
        val m = meta("quar")
        fs.forEach { val t = uniq(P.quar, it.name); it.copyTo(t); m[t.name] = it.absolutePath }
        saveMeta("quar", m); toast(tr("কোয়ারেন্টাইনে কপি হয়েছে (মূল ফাইল অক্ষত)", "Copied to quarantine (original kept)"))
    }
    fun quarFiles() = (P.quar.listFiles()?.toList() ?: emptyList()).sortedBy { it.name.lowercase() }
    fun quarRestore(f: File) = io {
        val m = meta("quar"); val o = File(m[f.name] ?: (P.root.path + "/" + f.name)); val d = o.parentFile ?: P.root; d.mkdirs()
        f.copyTo(uniq(d, o.name)); toast(tr("পুনরুদ্ধার হয়েছে", "Restored"))
    }
    fun quarDelete(f: File) = io { f.delete(); val m = meta("quar"); m.remove(f.name); saveMeta("quar", m) }

    // ---- zip ----
    private fun addZip(z: ZipOutputStream, f: File, base: File) {
        if (f.isDirectory) f.listFiles()?.forEach { addZip(z, it, base) }
        else { z.putNextEntry(ZipEntry(f.relativeTo(base).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
    }
    fun zip(fs: List<File>) = io {
        val out = uniq(cwd, "archive-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip")
        ZipOutputStream(FileOutputStream(out).buffered()).use { z -> fs.forEach { addZip(z, it, it.parentFile ?: P.root) } }
        toast(tr("ZIP তৈরি হয়েছে", "ZIP created"))
    }
    fun unzip(f: File) = io {
        val base = uniq(f.parentFile ?: cwd, f.nameWithoutExtension); base.mkdirs()
        ZipInputStream(f.inputStream().buffered()).use { z ->
            var e: ZipEntry? = z.nextEntry
            while (e != null) {
                val t = File(base, e.name)
                if (!t.canonicalPath.startsWith(base.canonicalPath)) throw SecurityException("unsafe path")
                if (e.isDirectory) t.mkdirs() else { t.parentFile?.mkdirs(); FileOutputStream(t).use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
        toast(tr("Extract সম্পন্ন", "Extracted"))
    }
    fun zipList(f: File): List<String> = try {
        ZipFile(f).use { z -> z.entries().asSequence().map { (if (it.isDirectory) "📁 " else "📄 ") + it.name + if (it.isDirectory) "" else "  (" + sizeStr(it.size) + ")" }.toList() }
    } catch (e: Exception) { listOf(tr("তালিকা পড়া যায়নি", "Cannot read archive")) }

    // ---- misc ----
    suspend fun dups(): List<List<File>> = withContext(Dispatchers.IO) {
        all.filter { it.length() > 0 }.groupBy { it.length() }.filter { it.value.size > 1 }
            .flatMap { (_, l) -> l.groupBy { fileHash(it) }.values.filter { it.size > 1 } }
    }
    fun apkInfo(f: File): List<String> {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(f.path, 0) ?: return listOf(tr("APK তথ্য পড়া যায়নি", "Cannot read APK"))
        val ai = pi.applicationInfo; ai?.sourceDir = f.path; ai?.publicSourceDir = f.path
        return listOf(tr("নাম: ", "Name: ") + (ai?.loadLabel(pm) ?: "-"), tr("প্যাকেজ: ", "Package: ") + pi.packageName,
            tr("ভার্সন: ", "Version: ") + (pi.versionName ?: "-"), tr("আকার: ", "Size: ") + sizeStr(f.length()))
    }
    fun csv(fs: List<File>, name: String) = io {
        val d = File(P.root, "SmartFM_Reports").apply { mkdirs() }; val o = uniq(d, name)
        val q = { s: String -> "\"" + s.replace("\"", "\"\"") + "\"" }
        o.writeText("\uFEFF" + "নাম,ফোল্ডার,ধরন,আকার (বাইট),পরিবর্তনের তারিখ\n" + fs.joinToString("\n") {
            listOf(it.name, it.parent ?: "", catOf(it).bn, it.length().toString(), SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.lastModified()))).joinToString(",") { c -> q(c) }
        })
        toast(tr("CSV সংরক্ষিত: ", "CSV saved: ") + o.path)
    }
    fun importUris(uris: List<Uri>) = io {
        uris.forEach { u ->
            var n = "file"
            ctx.contentResolver.query(u, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) n = c.getString(i) } }
            ctx.contentResolver.openInputStream(u)?.use { s -> FileOutputStream(uniq(cwd, n)).use { s.copyTo(it) } }
        }
        toast(tr("ক্লাউড থেকে ফাইল এসেছে", "Imported from cloud picker"))
    }
    fun storage(): Pair<Long, Long> { val s = StatFs(P.root.path); return Pair(s.totalBytes, s.availableBytes) }
    fun wipeBin() = io { binFiles().forEach { it.deleteRecursively() }; saveMeta("bin", emptyMap()) }
}
