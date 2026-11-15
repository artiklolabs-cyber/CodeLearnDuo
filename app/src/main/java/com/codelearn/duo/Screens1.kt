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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

// ---------- Shared components ----------

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    border: Color = Pal.elevated,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier.fillMaxWidth().clip(shape).background(Pal.card).border(BorderStroke(1.dp, border), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.height(IntrinsicSize.Min)) {
        if (accent != null) Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(16.dp), content = content)
    }
}

@Composable
fun Bar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Pal.elevated)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pal.green,
    enabled: Boolean = true,
    arrow: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = Color.White,
            disabledContainerColor = Pal.elevated, disabledContentColor = Pal.dim
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (arrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.text) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

fun accentText(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(color = Pal.purple, fontWeight = FontWeight.Bold)) { append(p) } else append(p)
    }
}

@Composable
fun CodeBlock(code: String, lang: Lang, fontSize: Int = 13, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.editorBg).border(1.dp, Pal.elevated, RoundedCornerShape(12.dp))) {
        Text(
            highlight(code, lang),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp, color = Pal.text)
        )
    }
}

@Composable
fun OutputBlock(text: String, modifier: Modifier = Modifier, label: String = "Output:", color: Color = Pal.green) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(12.dp)) {
        Text(label, color = Pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text.ifEmpty { " " }, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
fun OutputImages(images: List<String>, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    val bitmaps = remember(images) {
        images.mapNotNull { b64 ->
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bitmaps.forEach { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = "Output image",
                contentScale = ContentScale.Fit,
                filterQuality = if (bmp.width < 200) FilterQuality.None else FilterQuality.Medium,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun StatChip(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun GamificationHeader(vm: AppViewModel, quiz: Boolean, onSwitchCourse: () -> Unit) {
    val s = vm.snap
    val level = s.xp / 100 + 1
    val frac = (s.xp % 100) / 100f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatChip(Icons.Filled.LocalFireDepartment, Pal.amber, "${s.streak} Streak")
                StatChip(Icons.Filled.Bolt, Pal.amber, "${s.xp} XP")
                StatChip(Icons.Filled.EmojiEvents, Pal.amber, "Lv.$level")
            }
            Spacer(Modifier.height(12.dp))
            Bar(frac, Pal.amber)
        }
        if (quiz) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TrackChanges, null, tint = Pal.blue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${minOf(s.questionsToday, 10)}/10 questions today", color = Pal.text, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Bar(s.questionsToday / 10f, Pal.blue)
            }
        } else {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TrackChanges, null, tint = Pal.blue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${minOf(s.lessonsToday, 3)}/3 lessons today", color = Pal.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    repeat(3) { i ->
                        Box(Modifier.padding(start = 6.dp).size(10.dp).clip(CircleShape).background(if (i < s.lessonsToday) Pal.green else Pal.elevated))
                    }
                }
            }
        }
        AppCard(onClick = onSwitchCourse) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Pal.green, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(vm.activeCourse.title, color = Pal.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Pal.muted)
            }
        }
    }
}

// ---------- Learn ----------

enum class NodeState { DONE, CURRENT, OPEN, LOCKED }

private val NODE_OFFSETS = listOf(0f, 56f, 0f, -56f)

@Composable
fun LearnScreen(vm: AppViewModel, onSwitchCourse: () -> Unit, onStart: (String, Int) -> Unit) {
    val course = vm.activeCourse
    val s = vm.snap
    val done = s.done[course.id] ?: emptySet()
    val firstOpen = course.chapters.firstOrNull { it.number !in done }?.number ?: Int.MAX_VALUE
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "header") { GamificationHeader(vm, false, onSwitchCourse) }
        course.chapters.forEachIndexed { i, ch ->
            if (i == 0 || course.chapters[i - 1].section != ch.section) {
                item(key = "sec_${ch.number}") { SectionDivider(ch.section) }
            }
            item(key = "node_${ch.number}") {
                val state = when {
                    ch.number in done -> NodeState.DONE
                    ch.number == firstOpen -> NodeState.CURRENT
                    s.unlockAll -> NodeState.OPEN
                    else -> NodeState.LOCKED
                }
                val pct = if (state == NodeState.DONE) 1f
                else (s.steps["step_${course.id}_${ch.number}"] ?: 0) / ch.steps.size.toFloat()
                ChapterNode(ch, i, state, pct) { onStart(course.id, ch.number) }
            }
            if (i < course.chapters.size - 1) {
                item(key = "conn_${ch.number}") { Connector(i, ch.number in done) }
            }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun SectionDivider(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Pal.elevated))
        Text(label.uppercase(), color = Pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Pal.elevated))
    }
}

@Composable
fun ChapterNode(ch: Chapter, index: Int, state: NodeState, pct: Float, onStart: () -> Unit) {
    val dx = NODE_OFFSETS[index % 4]
    val color = when (state) {
        NodeState.DONE -> Pal.green
        NodeState.CURRENT, NodeState.OPEN -> Pal.blue
        NodeState.LOCKED -> Pal.elevated
    }
    val icon = when (state) {
        NodeState.DONE -> Icons.Filled.Star
        NodeState.LOCKED -> Icons.Filled.Lock
        else -> Icons.Filled.PlayArrow
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.offset(x = dx.dp).size(84.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(color)
                    .clickable(enabled = state != NodeState.LOCKED, onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (state == NodeState.LOCKED) Pal.dim else Color.White, modifier = Modifier.size(34.dp))
            }
            if (state == NodeState.DONE) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(Pal.card).border(2.dp, Pal.green, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Pal.green, modifier = Modifier.size(16.dp)) }
            }
        }
        if (state == NodeState.CURRENT) {
            Spacer(Modifier.height(8.dp))
            AppCard(Modifier.fillMaxWidth(0.9f), border = Pal.blue) {
                Text("CHAPTER ${ch.number}", color = Pal.blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(ch.title, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${ch.steps.size} steps · ~4 min", color = Pal.muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Bar(pct, Pal.blue)
                Text("${(pct * 100).toInt()}%", color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Start", onStart, color = Pal.blue)
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(ch.title, color = if (state == NodeState.LOCKED) Pal.dim else Pal.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(x = dx.dp))
        }
    }
}

@Composable
fun Connector(index: Int, done: Boolean) {
    val a = NODE_OFFSETS[index % 4]
    val b = NODE_OFFSETS[(index + 1) % 4]
    val color = if (done) Pal.green else Pal.dim
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val cx = size.width / 2f
        val x1 = cx + a.dp.toPx()
        val x2 = cx + b.dp.toPx()
        val p = Path().apply {
            moveTo(x1, 0f)
            cubicTo(x1, size.height * 0.5f, x2, size.height * 0.5f, x2, size.height)
        }
        drawPath(
            p, color,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 20f)))
        )
    }
}

// ---------- Course selection ----------

@Composable
fun SelectCourseScreen(vm: AppViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Select Course", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (lang in Lang.entries) {
                val dot = if (lang == Lang.PYTHON) Pal.green else Pal.blue
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                    Text(lang.name, color = Pal.muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                }
                for (c in vm.courses.filter { it.lang == lang }) {
                    val selected = c.id == vm.snap.activeCourse
                    val doneCount = vm.snap.done[c.id]?.size ?: 0
                    Box {
                        AppCard(border = if (selected) Pal.green else Pal.elevated, onClick = { vm.selectCourse(c.id); onBack() }) {
                            Text(c.title, color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(c.description, color = Pal.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("$doneCount/${c.chapters.size} chapters completed", color = dot, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (selected) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(Pal.green),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Lesson player ----------

@Composable
fun LessonPlayerScreen(vm: AppViewModel, courseId: String, chapterNo: Int, onClose: () -> Unit, onTry: (String) -> Unit) {
    val course = vm.course(courseId)
    val ch = course.chapters.first { it.number == chapterNo }
    val total = ch.steps.size
    var idx by remember { mutableIntStateOf(vm.resumeStep(courseId, chapterNo, total)) }
    var selected by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var earned by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(idx) { scroll.scrollTo(0) }

    if (finished) {
        LessonComplete(vm, ch, earned + 20, onClose)
        return
    }

    val step = ch.steps[idx]
    val isMc = step.type == StepType.MULTIPLE_CHOICE
    val correct = isMc && selected == step.answer

    fun next() {
        if (idx + 1 < total) {
            idx += 1
            selected = -1
            checked = false
            vm.saveStep(courseId, chapterNo, idx)
        } else {
            vm.completeLesson(course, ch)
            finished = true
        }
    }

    Column(Modifier.fillMaxSize().background(Pal.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Pal.muted) }
            Text(ch.title, color = Pal.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${idx + 1}/$total", color = Pal.muted, modifier = Modifier.padding(end = 16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(when { i < idx -> Pal.green; i == idx -> Pal.blue; else -> Pal.elevated })
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(16.dp)) {
            when (step.type) {
                StepType.CONCEPT -> ConceptStep(step)
                StepType.CODE_WALKTHROUGH -> WalkthroughStep(step, course.lang, vm.snap.fontSize) { onTry(vm.prepareScratch(course.lang, step.code)) }
                StepType.MULTIPLE_CHOICE -> McStep(step, course.lang, selected, checked) { if (!checked) selected = it }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (isMc && checked) {
                val c = if (correct) Pal.green else Pal.red
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.15f)).border(1.dp, c, RoundedCornerShape(12.dp)).padding(12.dp)
                ) {
                    Text(if (correct) "Correct!" else "Not quite", color = c, fontWeight = FontWeight.Bold)
                    Text(step.explanation, color = Pal.text, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (isMc && !checked) {
                PrimaryButton("Check", {
                    checked = true
                    vm.answerQuestion(courseId, course.lessonQuestion(ch, idx), selected == step.answer, 5)
                    if (selected == step.answer) earned += 5
                }, enabled = selected >= 0, arrow = false)
            } else {
                PrimaryButton(if (idx + 1 == total) "Finish" else "Continue", { next() })
            }
        }
    }
}

@Composable
fun ConceptStep(step: Step) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(84.dp).clip(CircleShape).background(Pal.purple.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lightbulb, null, tint = Pal.purple, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(step.title, color = Pal.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(accentText(step.body), color = Pal.text, fontSize = 17.sp, lineHeight = 26.sp)
    }
}

@Composable
fun WalkthroughStep(step: Step, lang: Lang, fontSize: Int, onTry: () -> Unit) {
    val lines = step.code.split("\n")
    val notes = step.notes.associateBy { it.line }
    var active by remember(step) { mutableIntStateOf(-1) }
    var lastNote by remember(step) { mutableStateOf("") }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize + 7).sp, color = Pal.text)
    Column {
        Text(step.title, color = Pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.editorBg).border(1.dp, Pal.elevated, RoundedCornerShape(12.dp)).padding(vertical = 8.dp)) {
            lines.forEachIndexed { i, l ->
                val n = i + 1
                val note = notes[n]
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (active == n) Pal.purple.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable(enabled = note != null) {
                            if (active == n) active = -1 else { active = n; lastNote = note?.text ?: "" }
                        }
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(n.toString(), style = style.copy(color = Pal.dim), modifier = Modifier.width(24.dp))
                    Text(highlight(l.ifEmpty { " " }, lang), style = style, modifier = Modifier.weight(1f))
                    if (note != null) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Pal.purple), contentAlignment = Alignment.Center) {
                            Text("i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = active > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.purple.copy(alpha = 0.15f)).border(1.dp, Pal.purple, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("Line ${if (active > 0) active else ""}", color = Pal.purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(lastNote, color = Pal.text, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Tap highlighted lines for details", color = Pal.muted, fontStyle = FontStyle.Italic, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutputBlock(step.output)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onTry, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Code, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try in Editor")
        }
    }
}

@Composable
fun McStep(step: Step, lang: Lang, selected: Int, checked: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(step.question, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (step.code.isNotBlank()) CodeBlock(step.code, lang)
        step.options.forEachIndexed { i, opt ->
            val border = when {
                checked && i == step.answer -> Pal.green
                checked && i == selected -> Pal.red
                i == selected -> Pal.blue
                else -> Pal.elevated
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.card)
                    .border(2.dp, border, RoundedCornerShape(14.dp)).clickable { onSelect(i) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, border, CircleShape), contentAlignment = Alignment.Center) {
                    if (i == selected || (checked && i == step.answer)) Box(Modifier.size(10.dp).clip(CircleShape).background(border))
                }
                Spacer(Modifier.width(12.dp))
                Text(opt, color = Pal.text, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun LessonComplete(vm: AppViewModel, ch: Chapter, xp: Int, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(110.dp).clip(CircleShape).background(Pal.green.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, null, tint = Pal.amber, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Chapter complete!", color = Pal.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(ch.title, color = Pal.muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1f)) {
                StatChip(Icons.Filled.Bolt, Pal.amber, "+$xp XP")
            }
            AppCard(Modifier.weight(1f)) {
                StatChip(Icons.Filled.LocalFireDepartment, Pal.amber, "${vm.snap.streak} Streak")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Next chapter unlocked", color = Pal.green, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Continue", onClose)
    }
}
