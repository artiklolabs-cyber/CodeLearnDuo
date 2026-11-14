package com.codelearn.duo

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class Judge0Settings(
    val baseUrl: String = DEFAULT_BASE,
    val authToken: String = "",
    val rapidKey: String = "",
    val langOverride: Int = 0
) {
    companion object {
        const val DEFAULT_BASE = "https://ce.judge0.com"
    }
}

data class ProgressSnapshot(
    val activeCourse: String,
    val unlockAll: Boolean,
    val fontSize: Int,
    val xp: Int,
    val streak: Int,
    val done: Map<String, Set<Int>>,
    val quizDone: Map<String, Set<Int>>,
    val solved: Set<String>,
    val mistakes: Set<String>,
    val steps: Map<String, Int>,
    val weekSessions: List<Int>,
    val lessonsToday: Int,
    val questionsToday: Int,
    val judge0: Judge0Settings
)

class ProgressStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("codelearn_prefs", Context.MODE_PRIVATE)
    private val settingKeys = setOf("active_course", "unlock_all", "font_size", "j0_base", "j0_token", "j0_rapid", "j0_lang")

    private fun today(): Long = LocalDate.now().toEpochDay()

    private fun strSet(key: String): MutableSet<String> =
        HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())

    private fun intSet(key: String): Set<Int> = strSet(key).mapNotNull { it.toIntOrNull() }.toSet()

    fun snapshot(): ProgressSnapshot {
        val t = today()
        val last = prefs.getLong("last_day", -1L)
        val streak = if (last >= t - 1) prefs.getInt("streak", 0) else 0
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        val week = (0..6).map { prefs.getInt("sess_${monday + it}", 0) }
        val steps = prefs.all.filterKeys { it.startsWith("step_") }.mapValues { (it.value as? Int) ?: 0 }
        return ProgressSnapshot(
            activeCourse = prefs.getString("active_course", "python_basics") ?: "python_basics",
            unlockAll = prefs.getBoolean("unlock_all", false),
            fontSize = prefs.getInt("font_size", 14),
            xp = prefs.getInt("xp", 0),
            streak = streak,
            done = CourseRepository.ids.associateWith { intSet("done_$it") },
            quizDone = CourseRepository.ids.associateWith { intSet("quizdone_$it") },
            solved = strSet("solved"),
            mistakes = strSet("mistakes"),
            steps = steps,
            weekSessions = week,
            lessonsToday = prefs.getInt("lessons_$t", 0),
            questionsToday = prefs.getInt("questions_$t", 0),
            judge0 = Judge0Settings(
                prefs.getString("j0_base", Judge0Settings.DEFAULT_BASE) ?: Judge0Settings.DEFAULT_BASE,
                prefs.getString("j0_token", "") ?: "",
                prefs.getString("j0_rapid", "") ?: "",
                prefs.getInt("j0_lang", 0)
            )
        )
    }

    fun putString(key: String, v: String) = prefs.edit().putString(key, v).apply()
    fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun putInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun addXp(n: Int) = prefs.edit().putInt("xp", prefs.getInt("xp", 0) + n).apply()

    fun markActive() {
        val t = today()
        val last = prefs.getLong("last_day", -1L)
        if (last == t) return
        val streak = if (last == t - 1) prefs.getInt("streak", 0) + 1 else 1
        prefs.edit().putLong("last_day", t).putInt("streak", streak).apply()
    }

    fun recordSession() {
        val key = "sess_${today()}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun incLessons() {
        val key = "lessons_${today()}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun incQuestions() {
        val key = "questions_${today()}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun addToSet(key: String, value: String) {
        val s = strSet(key); s.add(value); prefs.edit().putStringSet(key, s).apply()
    }

    fun removeFromSet(key: String, value: String) {
        val s = strSet(key); s.remove(value); prefs.edit().putStringSet(key, s).apply()
    }

    fun hasInSet(key: String, value: String): Boolean = strSet(key).contains(value)

    fun saveJudge0(s: Judge0Settings) {
        prefs.edit()
            .putString("j0_base", s.baseUrl.trim().ifBlank { Judge0Settings.DEFAULT_BASE })
            .putString("j0_token", s.authToken.trim())
            .putString("j0_rapid", s.rapidKey.trim())
            .putInt("j0_lang", s.langOverride)
            .apply()
        clearLangCache()
    }

    private fun cacheKey(base: String) = "j0cache_" + base.trim().trimEnd('/').hashCode()
    fun cachedLang(base: String): Int? = prefs.getInt(cacheKey(base), -1).takeIf { it > 0 }
    fun saveLang(base: String, id: Int) = prefs.edit().putInt(cacheKey(base), id).apply()
    fun clearLangCache() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("j0cache_") }.forEach { e.remove(it) }
        e.apply()
    }

    fun resetProgress() {
        val e = prefs.edit()
        prefs.all.keys.filter { it !in settingKeys && !it.startsWith("j0cache_") }.forEach { e.remove(it) }
        e.apply()
    }
}
