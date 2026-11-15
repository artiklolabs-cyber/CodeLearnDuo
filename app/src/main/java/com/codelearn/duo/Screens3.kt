package com.codelearn.duo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

// ---------- Editor: file manager ----------

private fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "${b / (1024 * 1024)} MB"
}

@Composable
fun EditorFilesScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var dir by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val entries = remember(dir, tick) { vm.workspace.list(dir) }
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WsEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WsEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = dir.isNotEmpty()) { dir = dir.substringBeforeLast('/', "") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                var name = "imported.txt"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
                }
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                vm.workspace.importFile(dir, name, text)
                tick++
                message = "Imported $name"
            } catch (e: Exception) {
                message = "Import failed: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(if (dir.isEmpty()) "Editor" else dir.substringAfterLast('/') + "/", if (dir.isEmpty()) null else ({ dir = dir.substringBeforeLast('/', "") })) {
            IconButton(onClick = { showImport = true }) { Icon(Icons.Filled.UploadFile, "Import", tint = Pal.text) }
            IconButton(onClick = { showNewFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "New folder", tint = Pal.text) }
            IconButton(onClick = { showNewFile = true }) { Icon(Icons.Filled.NoteAdd, "New file", tint = Pal.green) }
        }
        if (message != null) Text(message ?: "", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This folder is empty", color = Pal.muted)
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.size) { i ->
                val e = entries[i]
                var menu by remember { mutableStateOf(false) }
                AppCard(onClick = { if (e.isDir) dir = e.rel else onOpen(e.rel) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (e.isDir) Icons.Filled.Folder else Icons.Filled.Description, null,
                            tint = if (e.isDir) Pal.amber else if (e.name.endsWith(".py")) Pal.green else Pal.blue
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, color = Pal.text, fontWeight = FontWeight.SemiBold)
                            if (!e.isDir) Text(formatSize(e.size), color = Pal.muted, fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = Pal.muted) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; if (e.isDir) dir = e.rel else onOpen(e.rel) })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameTarget = e })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleteTarget = e })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFile) {
        var name by remember { mutableStateOf("") }
        var lang by remember { mutableStateOf(Lang.PYTHON) }
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            containerColor = Pal.card,
            title = { Text("New file", color = Pal.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("File name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Lang.entries.forEach { l ->
                            FilterChip(selected = lang == l, onClick = { lang = l }, label = { Text(".${l.ext}") })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rel = vm.workspace.createFile(dir, name, lang)
                    showNewFile = false
                    if (rel == null) message = "Could not create file (empty name or already exists)" else { tick++; onOpen(rel) }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFile = false }) { Text("Cancel") } }
        )
    }
    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            containerColor = Pal.card,
            title = { Text("New folder", color = Pal.text) },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (!vm.workspace.createFolder(dir, name)) message = "Could not create folder"
                    showNewFolder = false; tick++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }
    if (showImport) {
        var pasteMode by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("pasted.py") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = Pal.card,
            title = { Text("Import code", color = Pal.text) },
            text = {
                if (!pasteMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showImport = false; picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Pick a file") }
                        OutlinedButton(onClick = { pasteMode = true }, modifier = Modifier.fillMaxWidth()) { Text("Paste code") }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("File name") }, singleLine = true)
                        OutlinedTextField(text, { text = it }, label = { Text("Code") }, modifier = Modifier.heightIn(min = 140.dp))
                    }
                }
            },
            confirmButton = {
                if (pasteMode) TextButton(onClick = {
                    val rel = vm.workspace.importFile(dir, name, text)
                    showImport = false; tick++
                    message = if (rel != null) "Imported $rel" else "Import failed"
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
    renameTarget?.let { target ->
        var name by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Pal.card,
            title = { Text("Rename", color = Pal.text) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (!vm.workspace.rename(target.rel, name)) message = "Rename failed"
                    renameTarget = null; tick++
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Pal.card,
            title = { Text("Delete ${target.name}?", color = Pal.text) },
            text = { Text("This cannot be undone.", color = Pal.muted) },
            confirmButton = {
                TextButton(onClick = { vm.workspace.delete(target.rel); deleteTarget = null; tick++ }) { Text("Delete", color = Pal.red) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ---------- Editor: code editor ----------

@Composable
fun CodeEditorScreen(vm: AppViewModel, path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = Lang.fromExt(path.substringAfterLast('.', ""))
    val fileName = path.substringAfterLast('/')
    val defaultText = remember(path) { vm.defaultContent(path) }
    var saved by remember(path) { mutableStateOf(vm.workspace.read(path)) }
    var tfv by remember(path) { mutableStateOf(TextFieldValue(saved)) }
    val undo = remember(path) { ArrayList<String>() }
    val redo = remember(path) { ArrayList<String>() }
    var stdin by remember { mutableStateOf("") }
    var stdinOpen by remember { mutableStateOf(false) }
    var consoleOpen by remember { mutableStateOf(false) }
    var consoleExpanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    val fs = vm.snap.fontSize
    val dirty = tfv.text != saved
    val transformation = remember(lang) { HighlightTransformation(lang ?: Lang.PYTHON) }
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace, fontSize = fs.sp, lineHeight = (fs + 7).sp, color = Pal.text,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )

    fun apply(new: TextFieldValue) {
        if (new.text != tfv.text) {
            undo.add(tfv.text)
            if (undo.size > 300) undo.removeAt(0)
            redo.clear()
        }
        tfv = new
    }

    fun save() {
        vm.workspace.write(path, tfv.text)
        saved = tfv.text
    }

    fun goBack() {
        if (tfv.text != saved) save()
        onBack()
    }

    fun insert(s: String, back: Int = 0) {
        val sel = tfv.selection
        val t = tfv.text.replaceRange(sel.min, sel.max, s)
        apply(TextFieldValue(t, TextRange(sel.min + s.length - back)))
    }

    fun doUndo() {
        if (undo.isEmpty()) return
        redo.add(tfv.text)
        val t = undo.removeAt(undo.lastIndex)
        tfv = TextFieldValue(t, TextRange(minOf(tfv.selection.start, t.length)))
    }

    fun doRedo() {
        if (redo.isEmpty()) return
        undo.add(tfv.text)
        val t = redo.removeAt(redo.lastIndex)
        tfv = TextFieldValue(t, TextRange(minOf(tfv.selection.start, t.length)))
    }

    fun toggleComment() {
        val marker = if (lang == Lang.KOTLIN) "//" else "#"
        val text = tfv.text
        val sel = tfv.selection
        val start = text.lastIndexOf('\n', sel.min - 1) + 1
        val end = text.indexOf('\n', sel.max).let { if (it < 0) text.length else it }
        val lines = text.substring(start, end).split("\n")
        val allCommented = lines.all { it.isBlank() || it.trimStart().startsWith(marker) }
        val out = lines.map { l ->
            when {
                l.isBlank() -> l
                allCommented -> {
                    val i = l.indexOf(marker)
                    l.substring(0, i) + l.substring(i + marker.length).removePrefix(" ")
                }
                else -> "$marker $l"
            }
        }.joinToString("\n")
        apply(TextFieldValue(text.replaceRange(start, end, out), TextRange(start, start + out.length)))
    }

    fun copyText() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val sel = tfv.selection
        val chunk = if (sel.collapsed) tfv.text else tfv.text.substring(sel.min, sel.max)
        cm.setPrimaryClip(ClipData.newPlainText("code", chunk))
    }

    fun pasteText() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val s = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (!s.isNullOrEmpty()) insert(s)
    }

    fun run() {
        if (running) return
        save()
        running = true
        consoleOpen = true
        scope.launch {
            result = vm.runCode(lang, tfv.text, stdin)
            running = false
        }
    }

    BackHandler { goBack() }

    val before = tfv.text.substring(0, tfv.selection.start.coerceIn(0, tfv.text.length))
    val curLine = before.count { it == '\n' } + 1
    val curCol = before.length - before.lastIndexOf('\n')
    val lineCount = tfv.text.count { it == '\n' } + 1

    Column(Modifier.fillMaxSize().imePadding().background(Pal.bg)) {
        // top bar
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.text) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fileName, color = Pal.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lang?.label ?: "Plain text", color = Pal.muted, fontSize = 11.sp)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = Pal.text) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Save") }, onClick = { menuOpen = false; save() })
                    if (defaultText != null) {
                        DropdownMenuItem(text = { Text("Reset to Example") }, onClick = {
                            menuOpen = false
                            apply(TextFieldValue(defaultText, TextRange(0)))
                        })
                    }
                    DropdownMenuItem(text = { Text("Clear") }, onClick = { menuOpen = false; apply(TextFieldValue("", TextRange(0))) })
                    DropdownMenuItem(text = { Text("Font Size") }, onClick = { menuOpen = false; showFont = true })
                }
            }
            Button(
                onClick = { run() },
                enabled = !running,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.green, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.padding(end = 8.dp).height(38.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (running) "Running" else "Run", fontWeight = FontWeight.Bold)
            }
        }

        // editor surface
        Box(Modifier.weight(1f).fillMaxWidth().background(Pal.editorBg)) {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.width(46.dp).background(Pal.card).padding(top = 12.dp, end = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (n in 1..lineCount) Text("$n", style = textStyle.copy(color = Pal.dim))
                }
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(top = 12.dp, start = 10.dp, end = 10.dp)) {
                    BasicTextField(
                        value = tfv,
                        onValueChange = { apply(it) },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(Pal.green),
                        visualTransformation = transformation,
                        modifier = Modifier.defaultMinSize(minWidth = 400.dp, minHeight = 400.dp)
                    )
                }
            }
            if (defaultText != null) {
                Text(
                    "EXAMPLE", color = Pal.purple, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50))
                        .background(Pal.purple.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (dirty) "● Unsaved" else "✓ Saved", color = if (dirty) Pal.amber else Pal.green, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.card).padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Text(
                    "$curLine:$curCol", color = Pal.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.card).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // stdin
        Column(Modifier.fillMaxWidth().background(Pal.card)) {
            Row(Modifier.fillMaxWidth().clickable { stdinOpen = !stdinOpen }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Input (stdin)", color = Pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (stdinOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, null, tint = Pal.muted, modifier = Modifier.size(18.dp))
            }
            AnimatedVisibility(stdinOpen) {
                OutlinedTextField(
                    stdin, { stdin = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Text sent to the program's standard input") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), maxLines = 3
                )
            }
        }

        // console
        Column(Modifier.fillMaxWidth().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Output", color = Pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                val r = result
                val status = when {
                    running -> "Running…"
                    r == null -> ""
                    r.success -> "✓ ${r.elapsedTime} ms · ${r.statusDescription}"
                    else -> "✗ ${r.statusDescription} · ${r.elapsedTime} ms"
                }
                Text(status, color = if (r?.success == false) Pal.red else Pal.green, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { result = null }) { Text("Clear", fontSize = 12.sp) }
                IconButton(onClick = { consoleExpanded = !consoleExpanded; consoleOpen = true }) {
                    Icon(if (consoleExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, "Expand", tint = Pal.muted)
                }
                IconButton(onClick = { consoleOpen = !consoleOpen }) {
                    Icon(if (consoleOpen) Icons.Filled.Close else Icons.Filled.Terminal, "Toggle", tint = Pal.muted)
                }
            }
            AnimatedVisibility(consoleOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.fillMaxWidth().height(if (consoleExpanded) 300.dp else 140.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val r = result
                    if (r == null) {
                        Text(if (running) "Running…" else "Run your code to see output here.", color = Pal.dim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    } else {
                        if (r.stdout.isNotEmpty()) Text(r.stdout, color = Pal.green, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        if (r.errorOutput.isNotBlank()) Text(r.errorOutput, color = Pal.red, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        if (r.images.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutputImages(r.images)
                        }
                        if (r.stdout.isEmpty() && r.errorOutput.isBlank() && r.images.isEmpty()) Text("(no output)", color = Pal.dim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }

        // quick symbol bar
        Row(
            Modifier.fillMaxWidth().background(Pal.card).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showFind = true }) { Icon(Icons.Filled.Search, "Find", tint = Pal.text) }
            IconButton(onClick = { doUndo() }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = Pal.text) }
            IconButton(onClick = { doRedo() }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = Pal.text) }
            IconButton(onClick = { copyText() }) { Icon(Icons.Filled.ContentCopy, "Copy", tint = Pal.text) }
            IconButton(onClick = { pasteText() }) { Icon(Icons.Filled.ContentPaste, "Paste", tint = Pal.text) }
            SymbolKey("⇥") { insert("    ") }
            SymbolKey(if (lang == Lang.KOTLIN) "//" else "#") { toggleComment() }
            SymbolKey("()") { insert("()", 1) }
            SymbolKey("{}") { insert("{}", 1) }
            SymbolKey("=") { insert("=") }
            SymbolKey("\"\"") { insert("\"\"", 1) }
        }
    }

    if (showFont) {
        AlertDialog(
            onDismissRequest = { showFont = false },
            containerColor = Pal.card,
            title = { Text("Font size", color = Pal.text) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 14, 16, 18, 20).forEach { s ->
                        FilterChip(selected = fs == s, onClick = { vm.setFontSize(s) }, label = { Text("$s") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFont = false }) { Text("Done") } }
        )
    }
    if (showFind) {
        var find by remember { mutableStateOf("") }
        var replace by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFind = false },
            containerColor = Pal.card,
            title = { Text("Search & Replace", color = Pal.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true)
                    OutlinedTextField(replace, { replace = it }, label = { Text("Replace with") }, singleLine = true)
                    if (note.isNotEmpty()) Text(note, color = Pal.muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            if (find.isEmpty()) return@TextButton
                            val text = tfv.text
                            var i = text.indexOf(find, tfv.selection.max)
                            if (i < 0) i = text.indexOf(find)
                            if (i < 0) note = "No matches" else {
                                tfv = tfv.copy(selection = TextRange(i, i + find.length)); note = "Found at ${i + 1}"
                            }
                        }) { Text("Find next") }
                        TextButton(onClick = {
                            val sel = tfv.selection
                            if (find.isNotEmpty() && !sel.collapsed && tfv.text.substring(sel.min, sel.max) == find) {
                                apply(TextFieldValue(tfv.text.replaceRange(sel.min, sel.max, replace), TextRange(sel.min + replace.length)))
                                note = "Replaced"
                            } else note = "Use Find next first"
                        }) { Text("Replace") }
                        TextButton(onClick = {
                            if (find.isNotEmpty()) {
                                val count = tfv.text.windowed(find.length).count { it == find }
                                apply(TextFieldValue(tfv.text.replace(find, replace), TextRange(0)))
                                note = "Replaced $count"
                            }
                        }) { Text("All") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
        )
    }
}

@Composable
fun SymbolKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(8.dp)).background(Pal.elevated)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Pal.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

// ---------- More / settings ----------

@Composable
fun MoreScreen(vm: AppViewModel) {
    val s = vm.snap
    var showApi by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("More", color = Pal.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Unlock All Chapters", color = Pal.text, fontWeight = FontWeight.Bold)
                    Text("Skip the sequential lock on the Learn path", color = Pal.muted, fontSize = 13.sp)
                }
                Switch(checked = s.unlockAll, onCheckedChange = { vm.setUnlockAll(it) })
            }
        }
        AppCard {
            Text("Editor font size", color = Pal.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 16, 18, 20).forEach { f ->
                    FilterChip(selected = s.fontSize == f, onClick = { vm.setFontSize(f) }, label = { Text("$f") })
                }
            }
        }
        AppCard(onClick = { showApi = true }) {
            Text("Kotlin Compiler API Settings", color = Pal.text, fontWeight = FontWeight.Bold)
            Text(s.judge0.baseUrl, color = Pal.muted, fontSize = 13.sp)
        }
        AppCard(onClick = { confirmRestore = true }) {
            Text("Restore Default Example Files", color = Pal.text, fontWeight = FontWeight.Bold)
            Text("Rewrites the starter files in your workspace", color = Pal.muted, fontSize = 13.sp)
        }
        AppCard(onClick = { confirmReset = true }, border = Pal.red) {
            Text("Reset Progress", color = Pal.red, fontWeight = FontWeight.Bold)
            Text("Clears XP, streak, chapters, quizzes and solved challenges", color = Pal.muted, fontSize = 13.sp)
        }
        info?.let { Text(it, color = Pal.green, fontSize = 13.sp) }
    }
    if (showApi) {
        var base by remember { mutableStateOf(s.judge0.baseUrl) }
        var token by remember { mutableStateOf(s.judge0.authToken) }
        var rapid by remember { mutableStateOf(s.judge0.rapidKey) }
        var lid by remember { mutableStateOf(if (s.judge0.langOverride > 0) s.judge0.langOverride.toString() else "") }
        AlertDialog(
            onDismissRequest = { showApi = false },
            containerColor = Pal.card,
            title = { Text("Kotlin Compiler API", color = Pal.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kotlin runs on a Judge0 CE server. The default is ${Judge0Settings.DEFAULT_BASE}.", color = Pal.muted, fontSize = 12.sp)
                    OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(token, { token = it }, label = { Text("X-Auth-Token (optional)") }, singleLine = true)
                    OutlinedTextField(rapid, { rapid = it }, label = { Text("X-RapidAPI-Key (optional)") }, singleLine = true)
                    OutlinedTextField(lid, { lid = it.filter { c -> c.isDigit() } }, label = { Text("Kotlin language_id override (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveJudge0(Judge0Settings(base.trim().ifBlank { Judge0Settings.DEFAULT_BASE }, token, rapid, lid.toIntOrNull() ?: 0))
                    showApi = false
                    info = "Kotlin compiler settings saved"
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showApi = false }) { Text("Cancel") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Pal.card,
            title = { Text("Reset all progress?", color = Pal.text) },
            text = { Text("This cannot be undone.", color = Pal.muted) },
            confirmButton = { TextButton(onClick = { vm.resetProgress(); confirmReset = false; info = "Progress reset" }) { Text("Reset", color = Pal.red) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = Pal.card,
            title = { Text("Restore example files?", color = Pal.text) },
            text = { Text("The starter files will be overwritten with their original content.", color = Pal.muted) },
            confirmButton = { TextButton(onClick = { vm.restoreDefaultFiles(); confirmRestore = false; info = "Example files restored" }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }
}
