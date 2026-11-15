package com.codelearn.duo

import android.util.Base64
import com.chaquo.python.Python
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ExecutionResult(
    val success: Boolean,
    val stdout: String,
    val errorOutput: String,
    val elapsedTime: Long,
    val statusDescription: String,
    val images: List<String> = emptyList()
)

object PythonExecutor {
    private fun execute(code: String, stdin: String): ExecutionResult {
        val started = System.currentTimeMillis()
        return try {
            if (!Python.isStarted()) {
                ExecutionResult(
                    false, "",
                    "The embedded Python runtime is not running. Restart the app; if this persists, reinstall it.",
                    0, "Runtime unavailable"
                )
            } else {
                val raw = Python.getInstance().getModule("py_runner")
                    .callAttr("run_user_code", code, stdin).toString()
                val j = JSONObject(raw)
                val ok = j.optBoolean("success")
                val imgArr = j.optJSONArray("images")
                val images = if (imgArr == null) emptyList() else (0 until imgArr.length()).map { imgArr.getString(it) }
                ExecutionResult(ok, j.optString("stdout"), j.optString("stderr"), j.optLong("elapsedMs"), if (ok) "Finished" else "Error", images)
            }
        } catch (t: Throwable) {
            ExecutionResult(false, "", "Python execution failed: ${t.message}", System.currentTimeMillis() - started, "Error")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun run(code: String, stdin: String): ExecutionResult {
        val job = GlobalScope.async(Dispatchers.IO) { execute(code, stdin) }
        return withTimeoutOrNull(30_000L) { job.await() }
            ?: ExecutionResult(
                false, "",
                "Execution did not finish within 30 seconds. Check for an infinite loop or a call that waits for input (use the stdin bar).",
                30_000L, "Timed out"
            )
    }
}

object KotlinJudge0Executor {
    private const val TIP = "Tip: open More > Kotlin Compiler API Settings to use your own Judge0 endpoint, X-Auth-Token or RapidAPI key."
    private val memoryCache = HashMap<String, Int>()

    private class JudgeException(message: String) : Exception(message)
    private class HttpResult(val code: Int, val body: String)

    private fun http(method: String, url: String, body: String?, s: Judge0Settings): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            if (s.authToken.isNotBlank()) conn.setRequestProperty("X-Auth-Token", s.authToken)
            if (s.rapidKey.isNotBlank()) {
                conn.setRequestProperty("X-RapidAPI-Key", s.rapidKey)
                conn.setRequestProperty("X-RapidAPI-Host", URL(url).host)
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun versionOf(name: String): List<Int> =
        Regex("\\d+").findAll(name).map { it.value.toInt() }.toList()

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun detectLanguageId(s: Judge0Settings, store: ProgressStore): Int {
        if (s.langOverride > 0) return s.langOverride
        memoryCache[s.baseUrl]?.let { return it }
        store.cachedLang(s.baseUrl)?.let { memoryCache[s.baseUrl] = it; return it }
        val r = http("GET", "${s.baseUrl}/languages", null, s)
        if (r.code == 429) throw JudgeException("The Judge0 server is rate limiting requests (HTTP 429) while looking up languages. Wait a moment and try again.\n\n$TIP")
        if (r.code !in 200..299) {
            throw JudgeException("Could not look up the Kotlin language id: GET ${s.baseUrl}/languages returned HTTP ${r.code}.\nSet a manual Kotlin language_id in settings if this server hides /languages.\n\n$TIP")
        }
        val arr = JSONArray(r.body)
        var bestId = -1
        var bestVersion = emptyList<Int>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            if (!name.startsWith("Kotlin", ignoreCase = true)) continue
            val v = versionOf(name)
            val c = compareVersions(v, bestVersion)
            if (bestId < 0 || c > 0 || (c == 0 && o.getInt("id") > bestId)) {
                bestId = o.getInt("id")
                bestVersion = v
            }
        }
        if (bestId < 0) throw JudgeException("The Judge0 server at ${s.baseUrl} does not list a Kotlin language.\n\n$TIP")
        memoryCache[s.baseUrl] = bestId
        store.saveLang(s.baseUrl, bestId)
        return bestId
    }

    private fun b64(text: String): String = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun dec(o: JSONObject, key: String): String {
        if (o.isNull(key)) return ""
        val v = o.optString(key, "")
        if (v.isBlank()) return ""
        return try { String(Base64.decode(v, Base64.DEFAULT), Charsets.UTF_8) } catch (e: IllegalArgumentException) { v }
    }

    private fun rateLimited(ms: Long) = ExecutionResult(
        false, "",
        "The Judge0 server is rate limiting requests (HTTP 429). Wait a few seconds and run again.\n\n$TIP",
        ms, "Rate limited"
    )

    suspend fun run(code: String, stdin: String, settings: Judge0Settings, store: ProgressStore): ExecutionResult =
        withContext(Dispatchers.IO) { runInternal(code, stdin, settings, store) }

    private suspend fun runInternal(code: String, stdin: String, settings: Judge0Settings, store: ProgressStore): ExecutionResult {
        val started = System.currentTimeMillis()
        fun elapsed() = System.currentTimeMillis() - started
        try {
            val base = settings.baseUrl.trim().trimEnd('/')
            val s = settings.copy(baseUrl = base)
            val langId = detectLanguageId(s, store)
            val payload = JSONObject().put("language_id", langId).put("source_code", b64(code)).put("stdin", b64(stdin))
            val sub = http("POST", "$base/submissions?base64_encoded=true", payload.toString(), s)
            if (sub.code == 429) return rateLimited(elapsed())
            if (sub.code !in 200..299) {
                return ExecutionResult(false, "", "Judge0 rejected the submission (HTTP ${sub.code}).\n${sub.body.take(300)}\n\n$TIP", elapsed(), "Submission failed")
            }
            val token = JSONObject(sub.body).optString("token")
            if (token.isBlank()) {
                return ExecutionResult(false, "", "Judge0 did not return a submission token.\n\n$TIP", elapsed(), "Submission failed")
            }
            var attempt = 0
            while (attempt < 25) {
                delay(600)
                val r = http("GET", "$base/submissions/$token?base64_encoded=true&fields=stdout,stderr,compile_output,status,time,memory", null, s)
                if (r.code == 429) return rateLimited(elapsed())
                if (r.code !in 200..299) {
                    return ExecutionResult(false, "", "Polling failed with HTTP ${r.code}.\n${r.body.take(300)}\n\n$TIP", elapsed(), "Polling failed")
                }
                val j = JSONObject(r.body)
                val status = j.optJSONObject("status")
                val id = status?.optInt("id", 0) ?: 0
                if (id >= 3) {
                    val out = dec(j, "stdout")
                    val err = listOf(dec(j, "compile_output"), dec(j, "stderr")).filter { it.isNotBlank() }.joinToString("\n")
                    val desc = status?.optString("description") ?: "Finished"
                    val ms = j.optString("time").toDoubleOrNull()?.let { (it * 1000).toLong() } ?: elapsed()
                    val shownErr = if (id != 3 && err.isBlank()) desc else err
                    return ExecutionResult(id == 3, out, shownErr, ms, desc)
                }
                attempt++
            }
            return ExecutionResult(false, "", "The remote sandbox did not finish within about 15 seconds.\n\n$TIP", elapsed(), "Timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: JudgeException) {
            return ExecutionResult(false, "", e.message ?: "Judge0 error", elapsed(), "Lookup failed")
        } catch (e: UnknownHostException) {
            return ExecutionResult(false, "", "No connection to the Kotlin sandbox. Check that you are online.\n\n$TIP", elapsed(), "Offline")
        } catch (e: SocketTimeoutException) {
            return ExecutionResult(false, "", "The Kotlin sandbox took too long to respond.\n\n$TIP", elapsed(), "Timed out")
        } catch (e: IOException) {
            return ExecutionResult(false, "", "Network error while contacting the Kotlin sandbox: ${e.message}\n\n$TIP", elapsed(), "Network error")
        } catch (e: Exception) {
            return ExecutionResult(false, "", "Unexpected error: ${e.message}\n\n$TIP", elapsed(), "Error")
        }
    }
}
