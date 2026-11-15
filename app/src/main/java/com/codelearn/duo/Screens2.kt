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

// ---------- Quiz ----------

@Composable
fun QuizCard(accent: Color, icon: ImageVector, title: String, subtitle: String, progress: Float? = null, onClick: () -> Unit) {
    AppCard(accent = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Pal.muted, fontSize = 13.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Pal.muted)
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            Bar(progress, accent)
        }
    }
}

@Composable
fun QuizScreen(vm: AppViewModel, onSwitchCourse: () -> Unit, onPlay: (String, Int) -> Unit) {
    val course = vm.activeCourse
    val doneQ = vm.snap.quizDone[course.id] ?: emptySet()
    val mistakes = vm.mistakesFor(course)
    var pick by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GamificationHeader(vm, true, onSwitchCourse)
        QuizCard(Pal.green, Icons.Filled.School, "Chapter Quiz", "${doneQ.size}/${course.chapters.size} completed", doneQ.size / course.chapters.size.toFloat()) { pick = true }
        QuizCard(Pal.blue, Icons.Filled.Bolt, "Quick Quiz", "10 mixed questions from ${course.title}") { onPlay("quick", 0) }
        QuizCard(Pal.amber, Icons.Filled.Refresh, "Review Mistakes", if (mistakes == 0) "All clear!" else "$mistakes saved to review") {
            if (mistakes > 0) onPlay("review", 0)
        }
        QuizCard(Pal.red, Icons.Filled.Favorite, "Challenge", "3 lives · 1.5x XP multiplier") { onPlay("challenge", 0) }
        WeekCard(vm)
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            containerColor = Pal.card,
            title = { Text("Choose a chapter", color = Pal.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    course.chapters.forEach { ch ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pick = false; onPlay("chapter", ch.number) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${ch.number}. ${ch.title}", color = Pal.text, modifier = Modifier.weight(1f))
                            if (ch.number in doneQ) Icon(Icons.Filled.Check, null, tint = Pal.green)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Close") } }
        )
    }
}

@Composable
fun WeekCard(vm: AppViewModel) {
    val week = vm.snap.weekSessions
    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    AppCard {
        Text("This Week", color = Pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEachIndexed { i, n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (n > 0) Pal.green else Pal.elevated)
                            .then(if (i == todayIdx) Modifier.border(2.dp, Pal.blue, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (n > 0) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Text(labels[i], color = if (i == todayIdx) Pal.blue else Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("${week.sum()} sessions this week", color = Pal.muted, fontSize = 13.sp)
    }
}

@Composable
fun QuizPlayScreen(vm: AppViewModel, mode: String, chapter: Int, onClose: () -> Unit) {
    val course = vm.activeCourse
    val questions = remember { vm.buildQuiz(course, mode, chapter) }
    var idx by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var xp by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val challenge = mode == "challenge"
    val gain = if (challenge) 7 else 5

    if (questions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No questions available", color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Back", onClose, arrow = false)
        }
        return
    }

    if (finished) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(if (challenge && lives <= 0) Icons.Filled.HeartBroken else Icons.Filled.EmojiEvents, null, tint = Pal.amber, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text(if (challenge && lives <= 0) "Out of lives" else "Quiz complete!", color = Pal.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("$correctCount of ${questions.size} correct", color = Pal.muted, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("+$xp XP", color = Pal.amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Done", onClose, arrow = false)
        }
        return
    }

    val q = questions[idx]
    val correct = selected == q.answer

    fun finish(finalCorrect: Int) {
        val bonus = if (challenge) 15 else 10
        xp += bonus
        vm.finishQuiz(course, mode, chapter, finalCorrect, questions.size, bonus)
        finished = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Pal.muted) }
            Text("Question ${idx + 1}/${questions.size}", color = Pal.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (challenge) {
                repeat(3) { i ->
                    Icon(Icons.Filled.Favorite, null, tint = if (i < lives) Pal.red else Pal.elevated, modifier = Modifier.padding(end = 4.dp).size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Bar((idx + if (checked) 1 else 0) / questions.size.toFloat(), Pal.green, Modifier.padding(horizontal = 16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(q.question, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (q.code.isNotBlank()) CodeBlock(q.code, course.lang)
            q.options.forEachIndexed { i, opt ->
                val border = when {
                    checked && i == q.answer -> Pal.green
                    checked && i == selected -> Pal.red
                    i == selected -> Pal.blue
                    else -> Pal.elevated
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.card)
                        .border(2.dp, border, RoundedCornerShape(14.dp)).clickable { if (!checked) selected = i }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, border, CircleShape), contentAlignment = Alignment.Center) {
                        if (i == selected || (checked && i == q.answer)) Box(Modifier.size(10.dp).clip(CircleShape).background(border))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(opt, color = Pal.text, fontSize = 16.sp)
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (checked) {
                val c = if (correct) Pal.green else Pal.red
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.15f)).border(1.dp, c, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(if (correct) "Correct! +$gain XP" else "Not quite", color = c, fontWeight = FontWeight.Bold)
                    Text(q.explanation, color = Pal.text, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Continue", {
                    val outOfLives = challenge && lives <= 0
                    if (outOfLives || idx + 1 >= questions.size) finish(correctCount) else { idx += 1; selected = -1; checked = false }
                })
            } else {
                PrimaryButton("Check", {
                    checked = true
                    val ok = selected == q.answer
                    vm.answerQuestion(course.id, q, ok, gain)
                    if (ok) { correctCount += 1; xp += gain } else if (challenge) lives -= 1
                }, enabled = selected >= 0, arrow = false)
            }
        }
    }
}

// ---------- Practice ----------

@Composable
fun PracticeScreen(vm: AppViewModel, onExamples: (String) -> Unit, onChallenges: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Practice", color = Pal.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Run examples and solve coding challenges", color = Pal.muted, fontSize = 15.sp)
        vm.courses.forEach { c ->
            val accent = if (c.lang == Lang.PYTHON) Pal.green else Pal.blue
            val solved = vm.solvedCount(c)
            AppCard(accent = accent) {
                Text(c.title, color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(c.lang.label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Bar(solved / c.challenges.size.toFloat(), accent)
                Text("$solved/${c.challenges.size} challenges solved", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onExamples(c.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Examples") }
                    Button(
                        onClick = { onChallenges(c.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                    ) { Text("Challenges") }
                }
            }
        }
    }
}

@Composable
fun ExamplesScreen(vm: AppViewModel, courseId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val course = vm.course(courseId)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("${course.lang.name.lowercase().replaceFirstChar { it.uppercase() }} Examples", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(course.examples.size) { i ->
                val ex = course.examples[i]
                AppCard(onClick = { onOpen(vm.openExample(course, ex)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Pal.elevated), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Pal.text, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.title, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(ex.description, color = Pal.muted, fontSize = 13.sp)
                        }
                        Icon(Icons.Filled.PlayArrow, null, tint = Pal.green)
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengesScreen(vm: AppViewModel, courseId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val course = vm.course(courseId)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("${course.title} Challenges", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(course.challenges.size) { i ->
                val ch = course.challenges[i]
                val solved = vm.isSolved(course.id, ch.id)
                AppCard(accent = if (solved) Pal.green else Pal.amber, onClick = { onOpen(ch.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${ch.title}", color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(ch.prompt, color = Pal.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (solved) Icon(Icons.Filled.CheckCircle, null, tint = Pal.green)
                    }
                }
            }
        }
    }
}

private fun normalizeOutput(s: String): String =
    s.replace("\r\n", "\n").trimEnd().lines().joinToString("\n") { it.trimEnd() }

@Composable
fun ChallengeScreen(vm: AppViewModel, courseId: String, challengeId: String, onBack: () -> Unit) {
    val course = vm.course(courseId)
    val ch = course.challenges.first { it.id == challengeId }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(TextFieldValue(ch.starter)) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    val fs = vm.snap.fontSize
    val transformation = remember(course.lang) { HighlightTransformation(course.lang) }
    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fs.sp, lineHeight = (fs + 7).sp, color = Pal.text)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(ch.title, onBack) {
            if (vm.isSolved(course.id, ch.id)) Icon(Icons.Filled.CheckCircle, null, tint = Pal.green, modifier = Modifier.padding(end = 16.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ch.prompt, color = Pal.text, fontSize = 16.sp)
            OutputBlock(ch.expected, label = "Expected output:", color = Pal.amber)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.editorBg).border(1.dp, Pal.elevated, RoundedCornerShape(12.dp))) {
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(Pal.green),
                        visualTransformation = transformation,
                        modifier = Modifier.defaultMinSize(minWidth = 300.dp, minHeight = 160.dp)
                    )
                }
            }
            Button(
                onClick = {
                    if (!running) {
                        running = true
                        scope.launch {
                            val r = vm.runCode(course.lang, code.text, ch.stdin)
                            result = r
                            val ok = r.success && normalizeOutput(r.stdout) == normalizeOutput(ch.expected)
                            verdict = ok
                            if (ok) vm.solveChallenge(course, ch)
                            running = false
                        }
                    }
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.green, contentColor = Color.Black)
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Running…" else "Run & Verify", fontWeight = FontWeight.Bold)
            }
            val r = result
            if (r != null) {
                if (verdict == true) {
                    Text("Solved! Output matches the expected result.", color = Pal.green, fontWeight = FontWeight.Bold)
                } else {
                    Text("Not yet: the output does not match.", color = Pal.red, fontWeight = FontWeight.Bold)
                }
                OutputBlock(r.stdout, label = "Your output (${r.elapsedTime} ms):")
                OutputImages(r.images)
                if (r.errorOutput.isNotBlank()) OutputBlock(r.errorOutput, label = "Errors:", color = Pal.red)
            }
        }
    }
}
