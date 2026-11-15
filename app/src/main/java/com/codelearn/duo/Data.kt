package com.codelearn.duo

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class Lang(val ext: String, val label: String, val json: String) {
    PYTHON("py", "Python 3.13", "python"),
    KOTLIN("kt", "Kotlin JVM", "kotlin");

    companion object {
        fun fromExt(ext: String): Lang? = entries.firstOrNull { it.ext == ext.lowercase() }
        fun fromJson(s: String): Lang = entries.firstOrNull { it.json == s } ?: PYTHON
    }
}

enum class StepType { CONCEPT, CODE_WALKTHROUGH, MULTIPLE_CHOICE }

data class LineNote(val line: Int, val text: String)

data class Step(
    val type: StepType,
    val title: String,
    val body: String,
    val code: String,
    val notes: List<LineNote>,
    val output: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

data class Chapter(val number: Int, val section: String, val title: String, val steps: List<Step>)

data class QuizQ(
    val id: String,
    val chapter: Int,
    val question: String,
    val code: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

data class Example(val id: String, val title: String, val description: String, val code: String)

data class Challenge(
    val id: String,
    val title: String,
    val prompt: String,
    val starter: String,
    val expected: String,
    val stdin: String
)

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val lang: Lang,
    val chapters: List<Chapter>,
    val quiz: List<QuizQ>,
    val examples: List<Example>,
    val challenges: List<Challenge>
) {
    fun lessonQuestions(ch: Chapter): List<QuizQ> =
        ch.steps.mapIndexedNotNull { i, s ->
            if (s.type == StepType.MULTIPLE_CHOICE) lessonQuestion(ch, i) else null
        }

    fun lessonQuestion(ch: Chapter, stepIndex: Int): QuizQ {
        val s = ch.steps[stepIndex]
        return QuizQ("c${ch.number}s$stepIndex", ch.number, s.question, s.code, s.options, s.answer, s.explanation)
    }

    val allQuestions: List<QuizQ> get() = quiz + chapters.flatMap { lessonQuestions(it) }

    fun chapterQuiz(ch: Chapter): List<QuizQ> = quiz.filter { it.chapter == ch.number } + lessonQuestions(ch)

    fun questionById(id: String): QuizQ? = allQuestions.firstOrNull { it.id == id }
}

/**
 * Loads the base course files (assets/courses/<id>.json) and merges every module file found in
 * assets/courses/modules/*.json whose "course" field matches. Modules add chapters, quiz questions,
 * examples and challenges. Existing chapter numbers and ids are never changed, so saved progress
 * keeps working; new content uses new chapter numbers and unique ids.
 */
object CourseRepository {
    val ids = listOf("python_basics", "kotlin_basics")
    private const val MODULE_DIR = "courses/modules"
    private const val TAG = "CourseRepository"

    private class Module(
        val file: String,
        val id: String,
        val courseId: String,
        val order: Int,
        val chapters: List<Chapter>,
        val quiz: List<QuizQ>,
        val examples: List<Example>,
        val challenges: List<Challenge>
    )

    fun load(context: Context): List<Course> {
        val modules = loadModules(context)
        return ids.map { id ->
            val text = context.assets.open("courses/$id.json").bufferedReader().use { it.readText() }
            val base = parseCourse(JSONObject(text))
            merge(base, modules.filter { it.courseId == id }.sortedWith(compareBy({ it.order }, { it.file })))
        }
    }

    private fun loadModules(context: Context): List<Module> {
        val files = try {
            context.assets.list(MODULE_DIR)?.filter { it.endsWith(".json") }?.sorted()
        } catch (e: java.io.IOException) {
            null
        } ?: return emptyList()
        return files.mapNotNull { name ->
            try {
                val text = context.assets.open("$MODULE_DIR/$name").bufferedReader().use { it.readText() }
                parseModule(name, JSONObject(text))
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid module $name: ${e.message}")
                null
            }
        }
    }

    private fun merge(base: Course, modules: List<Module>): Course {
        val chapters = base.chapters.toMutableList()
        val quiz = base.quiz.toMutableList()
        val examples = base.examples.toMutableList()
        val challenges = base.challenges.toMutableList()
        for (m in modules) {
            for (c in m.chapters) if (chapters.none { it.number == c.number }) chapters.add(c)
            for (q in m.quiz) if (quiz.none { it.id == q.id }) quiz.add(q)
            for (e in m.examples) if (examples.none { it.id == e.id }) examples.add(e)
            for (h in m.challenges) if (challenges.none { it.id == h.id }) challenges.add(h)
        }
        return base.copy(
            chapters = chapters.sortedBy { it.number },
            quiz = quiz,
            examples = examples,
            challenges = challenges
        )
    }

    private fun JSONObject.strList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun parseStep(o: JSONObject): Step {
        val notes = o.optJSONArray("notes").objects().map { LineNote(it.getInt("line"), it.getString("text")) }
        return Step(
            type = StepType.valueOf(o.getString("type")),
            title = o.optString("title"),
            body = o.optString("body"),
            code = o.optString("code"),
            notes = notes,
            output = o.optString("output"),
            question = o.optString("question"),
            options = o.strList("options"),
            answer = o.optInt("answer", 0),
            explanation = o.optString("explanation")
        )
    }

    private fun parseChapters(arr: JSONArray?): List<Chapter> = arr.objects().map { c ->
        Chapter(
            c.getInt("number"), c.getString("section"), c.getString("title"),
            c.getJSONArray("steps").objects().map { parseStep(it) }
        )
    }

    private fun parseQuiz(arr: JSONArray?, idPrefix: String): List<QuizQ> = arr.objects().mapIndexed { i, q ->
        QuizQ(
            q.optString("id").ifBlank { "$idPrefix${i + 1}" }, q.getInt("chapter"), q.getString("question"),
            q.optString("code"), q.strList("options"), q.getInt("answer"), q.optString("explanation")
        )
    }

    private fun parseExamples(arr: JSONArray?): List<Example> = arr.objects().map {
        Example(it.getString("id"), it.getString("title"), it.optString("description"), it.getString("code"))
    }

    private fun parseChallenges(arr: JSONArray?): List<Challenge> = arr.objects().map {
        Challenge(
            it.getString("id"), it.getString("title"), it.getString("prompt"), it.getString("starter"),
            it.getString("expected"), it.optString("stdin")
        )
    }

    private fun parseCourse(o: JSONObject): Course = Course(
        o.getString("id"), o.getString("title"), o.optString("description"),
        Lang.fromJson(o.getString("language")),
        parseChapters(o.optJSONArray("chapters")),
        parseQuiz(o.optJSONArray("quiz"), "q"),
        parseExamples(o.optJSONArray("examples")),
        parseChallenges(o.optJSONArray("challenges"))
    )

    private fun parseModule(file: String, o: JSONObject): Module {
        val id = o.optString("id").ifBlank { file.removeSuffix(".json") }
        return Module(
            file, id, o.getString("course"), o.optInt("order", 100),
            parseChapters(o.optJSONArray("chapters")),
            parseQuiz(o.optJSONArray("quiz"), "${id}_q"),
            parseExamples(o.optJSONArray("examples")),
            parseChallenges(o.optJSONArray("challenges"))
        )
    }
}
