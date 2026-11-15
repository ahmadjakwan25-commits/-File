@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smartfm.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

fun titleOf(s: String) = when (s) {
    "home" -> tr("স্মার্ট ফাইল ম্যানেজার", "Smart File Manager")
    "files" -> tr("ফাইল", "Files"); "search" -> tr("সার্চ", "Search"); "tools" -> tr("টুলস", "Tools")
    "settings" -> tr("সেটিংস", "Settings"); "cats" -> tr("ক্যাটাগরি", "Categories")
    "storage" -> tr("স্টোরেজ বিশ্লেষণ", "Storage analysis"); "bin" -> tr("রিসাইকেল বিন", "Recycle bin")
    "fav" -> tr("ফেভারিট", "Favorites"); "dups" -> tr("ডুপ্লিকেট ফাইল", "Duplicates")
    "sus" -> tr("সন্দেহজনক ফাইল", "Suspicious files"); "quar" -> "Virus_Quarantine"
    "priv" -> tr("প্রাইভেট ফোল্ডার", "Private folder"); "cloud" -> tr("ক্লাউড স্টোরেজ", "Cloud storage")
    "apk" -> "APK"; "zips" -> tr("ZIP ও আর্কাইভ", "ZIP & archives"); else -> s
}
val MAIN = listOf("home" to "🏠", "files" to "📁", "search" to "🔍", "tools" to "🧰", "settings" to "⚙️")

@Composable fun App(vm: VM, act: MainActivity) {
    val cs = if (vm.dark) darkColorScheme(primary = Color(0xFF38B3D9), secondary = Color(0xFFF2B632))
    else lightColorScheme(primary = Color(0xFF0D6A8A), secondary = Color(0xFFF2B632))
    MaterialTheme(colorScheme = cs) {
        Surface(Modifier.fillMaxSize()) {
            when {
                !act.granted -> PermScreen(act)
                vm.locked -> LockScreen(vm, act)
                else -> Main(vm, act)
            }
        }
    }
}

@Composable fun PermScreen(act: MainActivity) {
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("📁", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(tr("সব ফাইল দেখার অনুমতি দরকার", "All-files access is required"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(tr("ফোনের ফাইল দেখা, কপি, সরানো ও মোছার জন্য এই অনুমতি লাগে। ফাইল আপনার ফোনের বাইরে যায় না।", "Needed to browse, copy, move and delete files. Your files never leave the phone."), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { act.requestAccess() }) { Text(tr("অনুমতি দিন", "Grant access")) }
    }
}

@Composable fun LockScreen(vm: VM, act: MainActivity) {
    var t by remember { mutableStateOf("") }; var err by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.bio) act.biometric { vm.locked = false } }
    Column(Modifier.fillMaxSize().padding(28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("🔒", fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(t, { t = it; err = false }, singleLine = true, label = { Text(tr("পিন", "PIN")) }, isError = err,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (err) Text(tr("ভুল পিন", "Wrong PIN"), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (sha(t) == vm.pin) vm.locked = false else err = true }) { Text(tr("খুলুন", "Unlock")) }
        if (vm.bio) OutlinedButton(onClick = { act.biometric { vm.locked = false } }) { Text("👆 " + tr("ফিঙ্গারপ্রিন্ট", "Fingerprint")) }
    }
}

@Composable fun Main(vm: VM, act: MainActivity) {
    LaunchedEffect(Unit) { vm.scan() }
    LaunchedEffect(vm.screen) { if (vm.screen != "priv") vm.privOK = false }
    BackHandler(enabled = vm.screen != "home" || vm.selMode) {
        when {
            vm.selMode -> vm.clearSel()
            vm.screen == "files" && vm.cwd != P.root -> vm.cwd = vm.cwd.parentFile ?: P.root
            vm.stack.isNotEmpty() -> vm.back()
            else -> vm.nav("home")
        }
    }
    Scaffold(topBar = { TopBar(vm) }, bottomBar = {
        Column {
            if (vm.selMode && vm.sel.isNotEmpty()) BulkBar(vm, act)
            NavigationBar {
                MAIN.forEach { (k, i) ->
                    NavigationBarItem(selected = vm.screen == k, onClick = { vm.nav(k) }, icon = { Text(i, fontSize = 20.sp) },
                        label = { Text(if (k == "home") tr("হোম", "Home") else titleOf(k), fontSize = 11.sp) })
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                "home" -> HomeScreen(vm); "files" -> FilesScreen(vm); "cats" -> CatsScreen(vm); "search" -> SearchScreen(vm)
                "tools" -> ToolsScreen(vm); "settings" -> SettingsScreen(vm, act); "storage" -> StorageScreen(vm)
                "bin" -> BinScreen(vm); "fav" -> FavScreen(vm); "dups" -> DupsScreen(vm); "sus" -> SusScreen(vm)
                "quar" -> QuarScreen(vm); "priv" -> PrivScreen(vm, act); "cloud" -> CloudScreen(vm)
                "apk" -> ListScreen(vm, vm.all.filter { it.extension.equals("apk", true) })
                "zips" -> ListScreen(vm, vm.all.filter { catOf(it) == Cat.ARC })
            }
        }
    }
    DialogHost(vm)
}

@Composable fun TopBar(vm: VM) {
    val main = MAIN.any { it.first == vm.screen }
    TopAppBar(title = { Text(titleOf(vm.screen), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (!main) TextButton(onClick = { vm.back() }) { Text("←", fontSize = 22.sp) } },
        actions = { if (vm.screen != "search") TextButton(onClick = { vm.go("search") }) { Text("🔍", fontSize = 20.sp) } })
}

@Composable fun BulkBar(vm: VM, act: MainActivity) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(num("${vm.sel.size}"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { val f = vm.selFiles(); vm.dlg = Dlg.Pick { d -> vm.copy(f, d) }; vm.clearSel() }) { Text("📋 " + tr("কপি", "Copy")) }
        TextButton(onClick = { val f = vm.selFiles(); vm.dlg = Dlg.Pick { d -> vm.move(f, d) }; vm.clearSel() }) { Text("📂 " + tr("সরান", "Move")) }
        TextButton(onClick = { shareFiles(ctx, vm.selFiles()); vm.clearSel() }) { Text("📤 " + tr("শেয়ার", "Share")) }
        TextButton(onClick = { vm.zip(vm.selFiles()); vm.clearSel() }) { Text("🗜️ ZIP") }
        TextButton(onClick = { vm.selFiles().forEach { f -> if (f.absolutePath !in vm.favs) vm.toggleFav(f) }; vm.clearSel() }) { Text("⭐") }
        TextButton(onClick = { val f = vm.selFiles(); vm.dlg = Dlg.Sure(tr("${num("${f.size}")}টি আইটেম রিসাইকেল বিনে যাবে।", "${f.size} items go to the recycle bin.")) { vm.trash(f) }; vm.clearSel() }) { Text("🗑️") }
        TextButton(onClick = { vm.clearSel() }) { Text("✕") }
    }
}

// ---------- shared pieces ----------
@Composable fun Donut(p: Float, size: Dp, label: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant; val col = MaterialTheme.colorScheme.secondary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = 14.dp.toPx()
            drawArc(track, -90f, 360f, false, style = Stroke(w))
            drawArc(col, -90f, 360f * p.coerceIn(0.01f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable fun Thumb(f: File, sel: Boolean, mode: Boolean, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (f.isDirectory) Color(0xFFF2B632) else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (mode) Text(if (sel) "✅" else "⬜", fontSize = 22.sp)
        else if (f.isFile && catOf(f) == Cat.IMG) AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (f.isDirectory) "📁" else catOf(f).icon, fontSize = 22.sp)
    }
}

fun metaOf(vm: VM, f: File): String {
    if (f.isDirectory) return num("${f.list()?.size ?: 0}") + tr("টি আইটেম", " items")
    return listOfNotNull(if (vm.showSize) sizeStr(f.length()) else null, if (vm.showDate) dateStr(f.lastModified()) else null).joinToString(" · ")
}

@Composable fun FileItem(vm: VM, f: File, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val sel = f.absolutePath in vm.sel; val warn = f.isFile && why(f).isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
        .background(if (sel) MaterialTheme.colorScheme.primary.copy(.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(.5f))
        .combinedClickable(onClick = { if (vm.selMode) vm.toggleSel(f) else (onClick ?: { vm.tap(f) })() }, onLongClick = { vm.selMode = true; if (!sel) vm.toggleSel(f) })
        .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(f, sel, vm.selMode)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text((if (f.absolutePath in vm.favs) "⭐ " else "") + (if (warn) "⚠️ " else "") + f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub ?: metaOf(vm, f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable fun GridCell(vm: VM, f: File) {
    val sel = f.absolutePath in vm.sel
    Column(Modifier.padding(3.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) MaterialTheme.colorScheme.primary.copy(.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(.5f))
        .combinedClickable(onClick = { if (vm.selMode) vm.toggleSel(f) else vm.tap(f) }, onLongClick = { vm.selMode = true; if (!sel) vm.toggleSel(f) }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Thumb(f, sel, vm.selMode, 72.dp)
        Text(f.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (f.isFile && vm.showSize) Text(sizeStr(f.length()), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ItemRow(icon: String, title: String, sub: String? = null, onClick: () -> Unit = {}, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(.5f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable fun Empty(msg: String) { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) } }

@Composable fun SortBar(vm: VM) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { vm.sort = when (vm.sort) { "name" -> "size"; "size" -> "date"; else -> "name" } }) {
            Text(tr("সাজান: ", "Sort: ") + when (vm.sort) { "name" -> tr("নাম", "Name"); "size" -> tr("আকার", "Size"); else -> tr("তারিখ", "Date") })
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { vm.asc = !vm.asc }) { Text(if (vm.asc) tr("ছোট→বড় ↑", "Asc ↑") else tr("বড়→ছোট ↓", "Desc ↓")) }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { vm.grid = !vm.grid; vm.setPref("grid", vm.grid) }) { Text(if (vm.grid) "☰" else "▦") }
    }
}

@Composable fun CatChips(sel: Cat?, onSel: (Cat?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { FilterChip(sel == null, { onSel(null) }, { Text(tr("সব", "All")) }) }
        items(Cat.values().toList()) { c -> FilterChip(sel == c, { onSel(c) }, { Text(c.icon + " " + c.label) }) }
    }
}

@Composable fun ListScreen(vm: VM, fl: List<File>) {
    if (fl.isEmpty()) Empty(tr("কিছু পাওয়া যায়নি", "Nothing found"))
    else LazyColumn(contentPadding = PaddingValues(12.dp)) { items(fl, key = { it.path }) { FileItem(vm, it, it.parent) } }
}

// ---------- screens ----------
@Composable fun HomeScreen(vm: VM) {
    val st = remember(vm.tick) { vm.storage() }; val total = st.first; val free = st.second
    val p = if (total > 0) (total - free).toFloat() / total else 0f
    val tiles = listOf(Triple("cats", "🗂️", tr("ক্যাটাগরি", "Categories")), Triple("storage", "📊", tr("স্টোরেজ", "Storage")), Triple("fav", "⭐", tr("ফেভারিট", "Favorites")),
        Triple("bin", "♻️", tr("রিসাইকেল বিন", "Recycle bin")), Triple("dups", "👯", tr("ডুপ্লিকেট", "Duplicates")), Triple("priv", "🔐", tr("প্রাইভেট", "Private")),
        Triple("sus", "⚠️", tr("সন্দেহজনক", "Suspicious")), Triple("quar", "🧪", tr("কোয়ারেন্টাইন", "Quarantine")), Triple("cloud", "☁️", tr("ক্লাউড", "Cloud")))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(p, 96.dp, num("${(p * 100).toInt()}%"))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(tr("ফোনের স্টোরেজ", "Phone storage"), fontWeight = FontWeight.Bold)
                        Text(tr("মোট: ", "Total: ") + sizeStr(total)); Text(tr("ব্যবহৃত: ", "Used: ") + sizeStr(total - free)); Text(tr("খালি: ", "Free: ") + sizeStr(free))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(Cat.IMG, Cat.VID, Cat.AUD, Cat.DOC).forEach { c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(num("${vm.all.count { catOf(it) == c }}"), fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(c.icon + " " + c.label, fontSize = 12.sp)
                        }
                    }
                }
                if (vm.scanning) Text(tr("স্ক্যান চলছে…", "Scanning…"), fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(3).forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.forEach { (s, i, l) ->
                            Card(Modifier.weight(1f).clickable { vm.go(s) }) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(i, fontSize = 26.sp); Text(l, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        }
        val fd = vm.favs.map { File(it) }.filter { it.isDirectory }
        if (fd.isNotEmpty()) {
            item { Text(tr("গুরুত্বপূর্ণ ফোল্ডার", "Important folders"), fontWeight = FontWeight.Bold) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { fd.forEach { d -> AssistChip(onClick = { vm.tap(d) }, label = { Text("📁 " + d.name) }) } } }
        }
        item { Text(tr("সাম্প্রতিক ফাইল", "Recent files"), fontWeight = FontWeight.Bold) }
        items(vm.all.sortedByDescending { it.lastModified() }.take(6), key = { it.path }) { FileItem(vm, it) }
    }
}

@Composable fun FilesScreen(vm: VM) {
    val ctx = LocalContext.current
    val fl = remember(vm.cwd, vm.tick, vm.sort, vm.asc, vm.flt) { vm.list() }
    val parts = generateSequence(vm.cwd) { if (it == P.root) null else it.parentFile }.takeWhile { it.path.startsWith(P.root.path) }.toList().reversed()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            parts.forEachIndexed { i, d -> TextButton(onClick = { vm.cwd = d }) { Text(if (i == 0) tr("হোম", "Home") else d.name) }; if (i < parts.size - 1) Text("/") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.dlg = Dlg.Ask(tr("নতুন ফোল্ডার", "New folder"), "", false) { vm.mkdir(it) } }) { Text("📁＋") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { if (vm.selMode) vm.clearSel() else vm.selMode = true }) { Text(if (vm.selMode) tr("বাতিল", "Cancel") else "☑ " + tr("নির্বাচন", "Select")) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { vm.refresh() }) { Text("🔄") }
        }
        SortBar(vm)
        CatChips(vm.flt) { vm.flt = it }
        if (fl.isEmpty()) Empty(tr("ফোল্ডারটি খালি", "This folder is empty"))
        else if (vm.grid) LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp)) { gridItems(fl, key = { it.path }) { GridCell(vm, it) } }
        else LazyColumn(contentPadding = PaddingValues(12.dp)) { items(fl, key = { it.path }) { FileItem(vm, it) } }
    }
}

@Composable fun CatsScreen(vm: VM) {
    var open by remember { mutableStateOf<Cat?>(null) }
    if (open == null) {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(Cat.values().toList()) { c ->
                val l = vm.all.filter { catOf(it) == c }
                ItemRow(c.icon, c.label, num("${l.size}") + tr("টি ফাইল · ", " files · ") + sizeStr(l.sumOf { it.length() }), { open = c })
            }
        }
    } else {
        val c = open!!
        val l = vm.all.filter { catOf(it) == c }.let { a ->
            val s = when (vm.sort) { "size" -> a.sortedBy { it.length() }; "date" -> a.sortedBy { it.lastModified() }; else -> a.sortedBy { it.name.lowercase() } }
            if (vm.asc) s else s.reversed()
        }
        BackHandler { open = null }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { open = null }) { Text("← " + tr("সব ক্যাটাগরি", "All categories")) } }
            SortBar(vm)
            ListScreen(vm, l)
        }
    }
}

@Composable fun SearchScreen(vm: VM) {
    var q by remember { mutableStateOf("") }; var ex by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Cat?>(null) }; var here by remember { mutableStateOf(false) }
    val res = remember(q, ex, cat, here, vm.all) {
        vm.all.filter { (q.isBlank() || it.name.contains(q, true)) && (cat == null || catOf(it) == cat) && (ex.isBlank() || it.extension.equals(ex.trim('.', ' '), true)) && (!here || it.startsWith(vm.cwd)) }.take(300)
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("ফাইলের নাম লিখুন", "File name")) }) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(ex, { ex = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("এক্সটেনশন (pdf)", "Extension (pdf)")) })
                Spacer(Modifier.width(8.dp))
                FilterChip(here, { here = !here }, { Text(tr("এই ফোল্ডারে", "This folder")) })
            }
        }
        item { CatChips(cat) { cat = it } }
        item { Text(if (vm.scanning) tr("স্ক্যান চলছে…", "Scanning…") else num("${res.size}") + tr("টি ফলাফল", " results"), fontSize = 12.sp) }
        items(res, key = { it.path }) { FileItem(vm, it, it.parent) }
    }
}

@Composable fun ToolsScreen(vm: VM) {
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { ItemRow("🗜️", tr("ZIP তৈরি", "Create ZIP"), tr("ফাইল নির্বাচন করে ZIP বানান", "Select files, then ZIP")) { vm.nav("files"); vm.selMode = true; vm.toast(tr("ফাইল বেছে নিয়ে ZIP চাপুন", "Select files then tap ZIP")) } }
        item { ItemRow("📦", tr("ZIP / আর্কাইভ", "ZIP / archives"), tr("তালিকা দেখা ও Extract", "List and extract")) { vm.go("zips") } }
        item { ItemRow("🤖", "APK", tr("APK শনাক্ত ও তথ্য", "Find APKs and see info")) { vm.go("apk") } }
        item { ItemRow("📑", tr("CSV রিপোর্ট", "CSV report"), tr("সব ফাইলের তালিকা Export", "Export all files")) { vm.csv(vm.all, "file-report.csv") } }
        item { ItemRow("⭐", tr("ফেভারিট তালিকা Export", "Export favorites"), "CSV") { vm.csv(vm.favs.map { File(it) }.filter { it.isFile }, "favorites.csv") } }
        item { ItemRow("📊", tr("বড় ফাইল খুঁজুন", "Find large files"), tr("স্টোরেজ বিশ্লেষণে দেখুন", "See in storage analysis")) { vm.go("storage") } }
        item { ItemRow("📄", tr("ডকুমেন্ট (PDF, TXT…)", "Documents (PDF, TXT…)"), tr("ক্যাটাগরিতে দেখুন", "Browse by category")) { vm.go("cats") } }
    }
}

@Composable fun StorageScreen(vm: VM) {
    val st = remember(vm.tick) { vm.storage() }; val used = st.first - st.second
    val by = vm.all.groupBy { catOf(it) }.mapValues { e -> e.value.sumOf { it.length() } }
    val sum = (by.values.sum()).coerceAtLeast(1L)
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(if (st.first > 0) used.toFloat() / st.first else 0f, 100.dp, num("${if (st.first > 0) (used * 100 / st.first) else 0}%"))
                    Column(Modifier.padding(start = 14.dp)) { Text(tr("মোট: ", "Total: ") + sizeStr(st.first)); Text(tr("ব্যবহৃত: ", "Used: ") + sizeStr(used)); Text(tr("খালি: ", "Free: ") + sizeStr(st.second)) }
                }
            }
        }
        items(Cat.values().toList()) { c ->
            val v = by[c] ?: 0L
            Column {
                Text(c.icon + " " + c.label + "  " + sizeStr(v) + " (" + num("${v * 100 / sum}") + "%)")
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(v.toFloat() / sum).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        item { Text(tr("সবচেয়ে বড় ফাইল", "Largest files"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(vm.all.sortedByDescending { it.length() }.take(15), key = { it.path }) { FileItem(vm, it, sizeStr(it.length()) + if (it.length() >= 50_000_000) tr(" · বড় ফাইল", " · large") else "") }
    }
}

@Composable fun BinScreen(vm: VM) {
    val l = remember(vm.tick) { vm.binFiles() }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { if (l.isNotEmpty()) OutlinedButton(onClick = { vm.dlg = Dlg.Sure(tr("বিনের সব ফাইল স্থায়ীভাবে মুছে যাবে।", "Everything in the bin will be deleted permanently.")) { vm.wipeBin() } }) { Text("🧹 " + tr("সব পরিষ্কার করুন", "Empty bin")) } }
        if (l.isEmpty()) item { Empty(tr("রিসাইকেল বিন খালি", "Recycle bin is empty")) }
        items(l, key = { it.path }) { f ->
            ItemRow(if (f.isDirectory) "📁" else catOf(f).icon, f.name.substringAfter("_"), sizeStr(if (f.isFile) f.length() else 0) + " · " + dateStr(vm.delTime(f))) {
                TextButton(onClick = { vm.restore(f) }) { Text("↩️") }
                TextButton(onClick = { vm.dlg = Dlg.Sure(tr("স্থায়ীভাবে মুছে ফেলবেন? আর ফেরানো যাবে না।", "Delete permanently? This cannot be undone.")) { vm.purge(listOf(f)) } }) { Text("❌") }
            }
        }
    }
}

@Composable fun FavScreen(vm: VM) {
    val l = vm.favs.map { File(it) }.filter { it.exists() }
    if (l.isEmpty()) Empty(tr("কোনো ফেভারিট নেই। ফাইল চেপে ⭐ দিন।", "No favorites yet. Open a file and tap ⭐."))
    else ListScreen(vm, l)
}

@Composable fun DupsScreen(vm: VM) {
    var g by remember { mutableStateOf<List<List<File>>?>(null) }
    LaunchedEffect(vm.all) { g = null; g = vm.dups() }
    val gs = g
    if (gs == null) Empty(tr("ডুপ্লিকেট খোঁজা হচ্ছে…", "Searching duplicates…"))
    else if (gs.isEmpty()) Empty("✅ " + tr("ডুপ্লিকেট পাওয়া যায়নি", "No duplicates found"))
    else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(tr("নাম নয়, ফাইলের আসল বিষয়বস্তু (SHA-256) মিলিয়ে ধরা হয়েছে। মোছার আগে নিশ্চিত করতে বলা হবে।", "Matched by real content (SHA-256), not name. You confirm before deleting."), fontSize = 12.sp) }
        items(gs) { grp ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(num("${grp.size}") + tr("টি একই ফাইল · ", " identical · ") + sizeStr(grp[0].length()), fontWeight = FontWeight.Bold)
                    grp.forEachIndexed { i, f ->
                        FileItem(vm, f, f.parent) {
                            if (i > 0) TextButton(onClick = { vm.dlg = Dlg.Sure(tr("এই কপিটি রিসাইকেল বিনে যাবে।", "This copy goes to the recycle bin.")) { vm.trash(listOf(f)) } }) { Text("🗑️") }
                            else Text(tr("রাখুন", "Keep"), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun SusScreen(vm: VM) {
    val l = remember(vm.all) { vm.suspicious() }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { Text(tr("ফাইলের নাম ও এক্সটেনশন দেখে সম্ভাব্য ঝুঁকি চিহ্নিত করা হয়। এটি অ্যান্টিভাইরাস নয়।", "Flags risky names and extensions. This is not an antivirus."), fontSize = 12.sp) }
        if (l.isEmpty()) item { Empty("✅ " + tr("সন্দেহজনক ফাইল পাওয়া যায়নি", "Nothing suspicious found")) }
        else item { Button(onClick = { vm.quarantine(l) }, modifier = Modifier.padding(vertical = 6.dp)) { Text("🧪 " + tr("সব কোয়ারেন্টাইনে কপি করুন", "Copy all to quarantine")) } }
        items(l, key = { it.path }) { f -> FileItem(vm, f, why(f).joinToString(", ")) { TextButton(onClick = { vm.quarantine(listOf(f)) }) { Text("🧪") } } }
    }
}

@Composable fun QuarScreen(vm: VM) {
    val l = remember(vm.tick) { vm.quarFiles() }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { Text(tr("এখানকার ফাইল আপনার অনুমতি ছাড়া মোছা হয় না। মূল ফাইলও থেকে যায়।", "Nothing here is deleted without your permission. Originals are kept."), fontSize = 12.sp) }
        if (l.isEmpty()) item { Empty(tr("কোয়ারেন্টাইন খালি", "Quarantine is empty")) }
        items(l, key = { it.path }) { f ->
            ItemRow("🧪", f.name, tr("মূল অবস্থান: ", "Original: ") + vm.origOf("quar", f)) {
                TextButton(onClick = { vm.quarRestore(f) }) { Text("↩️") }
                TextButton(onClick = { vm.dlg = Dlg.Sure(tr("কোয়ারেন্টাইন থেকে স্থায়ীভাবে মুছবেন?", "Delete from quarantine permanently?")) { vm.quarDelete(f) } }) { Text("🗑️") }
            }
        }
    }
}

@Composable fun PrivScreen(vm: VM, act: MainActivity) {
    if (!vm.privOK) {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("🔐", fontSize = 48.sp)
            Text(if (vm.pin.isEmpty()) tr("প্রাইভেট ফোল্ডার ব্যবহারে আগে সেটিংসে পিন সেট করুন।", "Set a PIN in Settings to use the private folder.") else tr("প্রাইভেট ফোল্ডার দেখতে পিন লাগবে।", "A PIN is required."), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            if (vm.pin.isNotEmpty()) {
                Button(onClick = { vm.dlg = Dlg.Ask(tr("পিন দিন", "Enter PIN"), "", true) { if (sha(it) == vm.pin) vm.privOK = true else vm.toast(tr("ভুল পিন", "Wrong PIN")) } }) { Text(tr("পিন দিয়ে খুলুন", "Unlock with PIN")) }
                if (vm.bio) OutlinedButton(onClick = { act.biometric { vm.privOK = true } }) { Text("👆 " + tr("ফিঙ্গারপ্রিন্ট", "Fingerprint")) }
            } else Button(onClick = { vm.nav("settings") }) { Text(tr("সেটিংসে যান", "Open settings")) }
        }
        return
    }
    val l = remember(vm.tick, vm.privOK) { vm.privFiles() }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { OutlinedButton(onClick = { vm.privOK = false }) { Text("🔒 " + tr("লক করুন", "Lock")) } }
        if (l.isEmpty()) item { Empty(tr("খালি। ফাইলের মেনু থেকে “প্রাইভেটে নিন” চাপুন।", "Empty. Use “Move to private” from a file's menu.")) }
        items(l, key = { it.path }) { f ->
            ItemRow(catOf(f).icon, f.name, sizeStr(f.length())) {
                val c = LocalContext.current
                TextButton(onClick = { openFile(c, f) }) { Text("👁️") }
                TextButton(onClick = { vm.fromPrivate(f) }) { Text("↩️") }
            }
        }
    }
}

@Composable fun CloudScreen(vm: VM) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Text(tr("Google Drive বা Dropbox-এ সরাসরি লগইন করতে তাদের API Key (OAuth Client ID) লাগে। তাই ফোনের ফাইল পিকার ও শেয়ার মেনু ব্যবহার হয়; দুটোতেই Drive ও Dropbox থাকে।", "Direct Drive/Dropbox sign-in needs your own OAuth client ID. This uses the system file picker and share sheet, which include Drive and Dropbox."), Modifier.padding(12.dp), fontSize = 13.sp) } }
        item { ItemRow("⬇️", tr("ক্লাউড থেকে ফাইল আনুন", "Download from cloud"), tr("বর্তমান ফোল্ডারে কপি হবে", "Saved into the current folder")) { pick.launch(arrayOf("*/*")) } }
        item { ItemRow("⬆️", tr("ক্লাউডে আপলোড করুন", "Upload to cloud"), tr("ফাইল বেছে শেয়ার → Drive/Dropbox", "Select files → Share → Drive/Dropbox")) { vm.nav("files"); vm.selMode = true; vm.toast(tr("ফাইল বেছে 📤 শেয়ার চাপুন", "Select files, tap Share")) } }
    }
}

@Composable fun SettingsScreen(vm: VM, act: MainActivity) {
    @Composable fun Sw(l: String, v: Boolean, f: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f)); Switch(v, f) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Sw(tr("ডার্ক মোড", "Dark mode"), vm.dark) { vm.dark = it; vm.setPref("dark", it) }
        Sw(tr("গ্রিড ভিউ", "Grid view"), vm.grid) { vm.grid = it; vm.setPref("grid", it) }
        Sw(tr("ফাইলের আকার দেখান", "Show file size"), vm.showSize) { vm.showSize = it; vm.setPref("size", it) }
        Sw(tr("তারিখ দেখান", "Show date"), vm.showDate) { vm.showDate = it; vm.setPref("date", it) }
        Sw(tr("নোটিফিকেশন (বার্তা)", "Notifications (toasts)"), vm.notif) { vm.notif = it; vm.setPref("notif", it) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(tr("ভাষা", "Language"), fontWeight = FontWeight.Bold)
        Row { FilterChip(LANG == "bn", { vm.setLang("bn") }, { Text("বাংলা") }); Spacer(Modifier.width(8.dp)); FilterChip(LANG == "en", { vm.setLang("en") }, { Text("English") }) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(tr("নিরাপত্তা", "Security"), fontWeight = FontWeight.Bold)
        Row {
            Button(onClick = { vm.dlg = Dlg.Ask(tr("নতুন পিন (৪+ সংখ্যা)", "New PIN (4+ digits)"), "", true) { if (it.length >= 4) { vm.setPin(it); vm.toast(tr("পিন সেট হয়েছে", "PIN set")) } else vm.toast(tr("অন্তত ৪ সংখ্যা", "At least 4 digits")) } }) { Text(if (vm.pin.isEmpty()) tr("পিন সেট করুন", "Set PIN") else tr("পিন বদলান", "Change PIN")) }
            if (vm.pin.isNotEmpty()) { Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { vm.setPin(""); vm.bio = false; vm.setPref("bio", false) }) { Text(tr("পিন বন্ধ", "Remove PIN")) } }
        }
        if (vm.pin.isNotEmpty() && act.bioAvailable()) Sw(tr("ফিঙ্গারপ্রিন্ট লক", "Fingerprint lock"), vm.bio) { vm.bio = it; vm.setPref("bio", it) }
        else if (vm.pin.isNotEmpty()) Text(tr("এই ডিভাইসে ফিঙ্গারপ্রিন্ট সমর্থিত নয় বা সেট করা নেই।", "Fingerprint not available on this device."), fontSize = 12.sp)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(tr("শর্টকাট: অ্যাপ আইকন চেপে ধরে সার্চ, স্টোরেজ ও রিসাইকেল বিনে যান।", "Shortcuts: long-press the app icon for Search, Storage and Recycle bin."), fontSize = 13.sp)
    }
}

// ---------- dialogs ----------
@Composable fun DialogHost(vm: VM) {
    val d = vm.dlg ?: return
    val close = { vm.dlg = null }
    when (d) {
        is Dlg.Info -> InfoDlg(vm, d.f)
        is Dlg.Ask -> {
            var t by remember { mutableStateOf(d.def) }
            AlertDialog(onDismissRequest = close, title = { Text(d.title) },
                text = { OutlinedTextField(t, { t = it }, singleLine = true, visualTransformation = if (d.pw) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (d.pw) KeyboardType.NumberPassword else KeyboardType.Text)) },
                confirmButton = { TextButton(onClick = { close(); d.ok(t) }) { Text(tr("ঠিক আছে", "OK")) } },
                dismissButton = { TextButton(onClick = close) { Text(tr("বাতিল", "Cancel")) } })
        }
        is Dlg.Sure -> AlertDialog(onDismissRequest = close, title = { Text(tr("নিশ্চিত করুন", "Confirm")) }, text = { Text(d.msg) },
            confirmButton = { TextButton(onClick = { close(); d.ok() }) { Text(tr("হ্যাঁ", "Yes")) } },
            dismissButton = { TextButton(onClick = close) { Text(tr("না", "No")) } })
        is Dlg.Lines -> AlertDialog(onDismissRequest = close, title = { Text(d.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { d.lines.forEach { Text(it, fontSize = 13.sp) } } },
            confirmButton = { TextButton(onClick = close) { Text(tr("বন্ধ", "Close")) } })
        is Dlg.Pick -> {
            var cur by remember { mutableStateOf(vm.cwd) }
            val subs = remember(cur) { cur.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList() }
            AlertDialog(onDismissRequest = close, title = { Text(tr("ফোল্ডার বেছে নিন", "Choose folder")) },
                text = {
                    Column {
                        Text(cur.path, fontSize = 11.sp)
                        if (cur != P.root) TextButton(onClick = { cur = cur.parentFile ?: P.root }) { Text("⬆️ ..") }
                        LazyColumn(Modifier.heightIn(max = 280.dp)) { items(subs) { s -> TextButton(onClick = { cur = s }) { Text("📁 " + s.name) } } }
                    }
                },
                confirmButton = { TextButton(onClick = { close(); d.ok(cur) }) { Text(tr("এখানে", "Here")) } },
                dismissButton = { TextButton(onClick = close) { Text(tr("বাতিল", "Cancel")) } })
        }
    }
}

@Composable fun InfoDlg(vm: VM, f: File) {
    val ctx = LocalContext.current
    val close = { vm.dlg = null }
    val w = why(f)
    val acts = ArrayList<Pair<String, () -> Unit>>()
    fun add(l: String, a: () -> Unit) { acts.add(l to a) }
    if (f.isFile) add("📂 " + tr("খুলুন", "Open")) { close(); openFile(ctx, f) }
    add(if (f.absolutePath in vm.favs) "★ " + tr("ফেভারিট সরান", "Unfavorite") else "☆ " + tr("ফেভারিট", "Favorite")) { vm.toggleFav(f); close() }
    add("✏️ " + tr("নাম পরিবর্তন", "Rename")) { vm.dlg = Dlg.Ask(tr("নতুন নাম", "New name"), f.name, false) { vm.rename(f, it) } }
    add("📋 " + tr("কপি", "Copy")) { vm.dlg = Dlg.Pick { vm.copy(listOf(f), it) } }
    add("📂 " + tr("সরান", "Move")) { vm.dlg = Dlg.Pick { vm.move(listOf(f), it) } }
    if (f.isFile) add("📤 " + tr("শেয়ার", "Share")) { close(); shareFiles(ctx, listOf(f)) }
    add("🗜️ " + tr("ZIP করুন", "Zip")) { close(); vm.zip(listOf(f)) }
    if (f.extension.equals("zip", true)) {
        add("📦 " + tr("ভেতরের তালিকা", "List contents")) { vm.dlg = Dlg.Lines(f.name, vm.zipList(f)) }
        add("📂 Extract") { close(); vm.unzip(f) }
    }
    if (f.extension.equals("apk", true)) add("🤖 " + tr("APK তথ্য", "APK info")) { vm.dlg = Dlg.Lines("APK", vm.apkInfo(f)) }
    if (w.isNotEmpty()) add("🧪 " + tr("কোয়ারেন্টাইনে কপি", "Quarantine copy")) { close(); vm.quarantine(listOf(f)) }
    if (f.isFile) add("🔐 " + tr("প্রাইভেটে নিন", "Move to private")) { close(); if (vm.pin.isEmpty()) vm.toast(tr("আগে সেটিংসে পিন সেট করুন", "Set a PIN in Settings first")) else vm.toPrivate(f) }
    add("🗑️ " + tr("মুছুন", "Delete")) { vm.dlg = Dlg.Sure(tr("রিসাইকেল বিনে পাঠানো হবে।", "It will go to the recycle bin.")) { vm.trash(listOf(f)) } }
    AlertDialog(onDismissRequest = close, title = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("অবস্থান: ", "Path: ") + (f.parent ?: ""), fontSize = 12.sp)
                if (f.isFile) Text(tr("ধরন: ", "Type: ") + catOf(f).label + " (." + f.extension + ")", fontSize = 12.sp)
                Text(tr("আকার: ", "Size: ") + sizeStr(if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }), fontSize = 12.sp)
                Text(tr("পরিবর্তন: ", "Modified: ") + dateStr(f.lastModified()), fontSize = 12.sp)
                if (w.isNotEmpty()) Text("⚠️ " + w.joinToString(", "), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                acts.chunked(2).forEach { r -> Row(Modifier.fillMaxWidth()) { r.forEach { (l, a) -> TextButton(onClick = a, modifier = Modifier.weight(1f)) { Text(l, fontSize = 13.sp) } }; if (r.size == 1) Spacer(Modifier.weight(1f)) } }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(tr("বন্ধ", "Close")) } })
}
