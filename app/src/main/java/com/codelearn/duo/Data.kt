package com.codelearn.duo

import android.content.Context
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

object CourseRepository {
    val ids = listOf("python_basics", "kotlin_basics")

    fun load(context: Context): List<Course> = ids.map { id ->
        val text = context.assets.open("courses/$id.json").bufferedReader().use { it.readText() }
        parse(JSONObject(text))
    }

    private fun JSONObject.strList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun parseStep(o: JSONObject): Step {
        val notes = (o.optJSONArray("notes") ?: JSONArray()).objects()
            .map { LineNote(it.getInt("line"), it.getString("text")) }
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

    private fun parse(o: JSONObject): Course {
        val chapters = o.getJSONArray("chapters").objects().map { c ->
            Chapter(
                c.getInt("number"), c.getString("section"), c.getString("title"),
                c.getJSONArray("steps").objects().map { parseStep(it) }
            )
        }
        val quiz = o.getJSONArray("quiz").objects().mapIndexed { i, q ->
            QuizQ(
                "q${i + 1}", q.getInt("chapter"), q.getString("question"), q.optString("code"),
                q.strList("options"), q.getInt("answer"), q.optString("explanation")
            )
        }
        val examples = o.getJSONArray("examples").objects().map {
            Example(it.getString("id"), it.getString("title"), it.optString("description"), it.getString("code"))
        }
        val challenges = o.getJSONArray("challenges").objects().map {
            Challenge(
                it.getString("id"), it.getString("title"), it.getString("prompt"), it.getString("starter"),
                it.getString("expected"), it.optString("stdin")
            )
        }
        return Course(
            o.getString("id"), o.getString("title"), o.optString("description"),
            Lang.fromJson(o.getString("language")), chapters, quiz, examples, challenges
        )
    }
}
