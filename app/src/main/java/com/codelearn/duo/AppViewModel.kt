package com.codelearn.duo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = ProgressStore(app)
    val workspace = WorkspaceStore(app)
    val courses: List<Course> = CourseRepository.load(app)

    var snap by mutableStateOf(store.snapshot())
        private set

    init {
        workspace.ensureSeeded()
    }

    private fun refresh() {
        snap = store.snapshot()
    }

    val activeCourse: Course get() = courses.firstOrNull { it.id == snap.activeCourse } ?: courses.first()

    fun course(id: String): Course = courses.firstOrNull { it.id == id } ?: courses.first()

    fun selectCourse(id: String) { store.putString("active_course", id); refresh() }
    fun setUnlockAll(v: Boolean) { store.putBool("unlock_all", v); refresh() }
    fun setFontSize(v: Int) { store.putInt("font_size", v); refresh() }
    fun saveJudge0(s: Judge0Settings) { store.saveJudge0(s); refresh() }

    fun resetProgress() { store.resetProgress(); refresh() }
    fun restoreDefaultFiles() { workspace.restoreDefaults() }

    fun resumeStep(courseId: String, chapter: Int, total: Int): Int {
        if (snap.done[courseId]?.contains(chapter) == true) return 0
        return (snap.steps["step_${courseId}_$chapter"] ?: 0).coerceIn(0, (total - 1).coerceAtLeast(0))
    }

    fun saveStep(courseId: String, chapter: Int, idx: Int) {
        store.putInt("step_${courseId}_$chapter", idx); refresh()
    }

    fun completeLesson(course: Course, ch: Chapter) {
        store.addToSet("done_${course.id}", ch.number.toString())
        store.remove("step_${course.id}_${ch.number}")
        store.addXp(20)
        store.incLessons()
        store.recordSession()
        store.markActive()
        refresh()
    }

    fun answerQuestion(courseId: String, q: QuizQ, correct: Boolean, xp: Int) {
        val key = "$courseId|${q.id}"
        store.markActive()
        store.incQuestions()
        if (correct) {
            store.addXp(xp)
            store.removeFromSet("mistakes", key)
        } else {
            store.addToSet("mistakes", key)
        }
        refresh()
    }

    fun finishQuiz(course: Course, mode: String, chapter: Int, correct: Int, total: Int, bonus: Int) {
        store.recordSession()
        store.markActive()
        if (bonus > 0) store.addXp(bonus)
        if (mode == "chapter" && total > 0 && correct * 100 / total >= 70) {
            store.addToSet("quizdone_${course.id}", chapter.toString())
        }
        refresh()
    }

    fun mistakesFor(course: Course): Int = snap.mistakes.count { it.startsWith(course.id + "|") }

    fun buildQuiz(course: Course, mode: String, chapter: Int): List<QuizQ> = when (mode) {
        "chapter" -> course.chapters.firstOrNull { it.number == chapter }?.let { course.chapterQuiz(it) } ?: emptyList()
        "quick" -> course.allQuestions.shuffled().take(10)
        "review" -> snap.mistakes
            .filter { it.startsWith(course.id + "|") }
            .mapNotNull { course.questionById(it.substringAfter('|')) }
        "challenge" -> course.allQuestions.shuffled().take(15)
        else -> emptyList()
    }

    fun isSolved(courseId: String, challengeId: String): Boolean = snap.solved.contains("$courseId:$challengeId")

    fun solvedCount(course: Course): Int = course.challenges.count { isSolved(course.id, it.id) }

    fun solveChallenge(course: Course, ch: Challenge) {
        if (!isSolved(course.id, ch.id)) {
            store.addToSet("solved", "${course.id}:${ch.id}")
            store.addXp(25)
        }
        store.recordSession()
        store.markActive()
        refresh()
    }

    fun defaultContent(path: String): String? {
        DefaultFiles.files[path]?.let { return it }
        for (c in courses) for (ex in c.examples) {
            if (path == "examples/${ex.id}.${c.lang.ext}") return ex.code
        }
        return null
    }

    fun openExample(course: Course, ex: Example): String {
        val rel = "examples/${ex.id}.${course.lang.ext}"
        if (!workspace.exists(rel)) workspace.write(rel, ex.code)
        return rel
    }

    fun prepareScratch(lang: Lang, code: String): String {
        val rel = "lesson_try.${lang.ext}"
        workspace.write(rel, code + "\n")
        return rel
    }

    suspend fun runCode(lang: Lang?, code: String, stdin: String): ExecutionResult = when (lang) {
        Lang.PYTHON -> PythonExecutor.run(code, stdin)
        Lang.KOTLIN -> KotlinJudge0Executor.run(code, stdin, snap.judge0, store)
        null -> ExecutionResult(false, "", "Only .py and .kt files can be run.", 0, "Unsupported file")
    }
}
