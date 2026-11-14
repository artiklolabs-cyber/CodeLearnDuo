package com.codelearn.duo

import android.content.Context
import java.io.File

data class WsEntry(val name: String, val rel: String, val isDir: Boolean, val size: Long)

object DefaultFiles {
    val files: LinkedHashMap<String, String> = linkedMapOf(
        "hello_python.py" to """
            # Hello Python 3.13
            # Edit the code below, then press Run.

            def greet(name):
                return f"Hello, {name}!"

            print(greet("World"))
            for i in range(1, 4):
                print("Count:", i)
        """.trimIndent() + "\n",
        "HelloKotlin.kt" to """
            fun main() {
                val list = listOf("Kotlin", "is", "fun")
                for (word in list) {
                    println(word)
                }
                println("Words: ${'$'}{list.size}")
            }
        """.trimIndent() + "\n",
        "examples/loops_python.py" to """
            # Loops in Python
            for i in range(1, 6):
                print(i, "x 2 =", i * 2)

            total = 0
            while total < 10:
                total += 3
            print("Total:", total)
        """.trimIndent() + "\n",
        "examples/functions_kotlin.kt" to """
            fun add(a: Int, b: Int): Int = a + b

            fun greet(name: String, greeting: String = "Hello"): String = "${'$'}greeting, ${'$'}name!"

            fun main() {
                println(add(2, 3))
                println(greet("Kotlin"))
                println(greet("World", "Hi"))
            }
        """.trimIndent() + "\n"
    )
}

class WorkspaceStore(context: Context) {
    val root = File(context.filesDir, "workspace")
    private val prefs = context.getSharedPreferences("codelearn_workspace", Context.MODE_PRIVATE)

    fun ensureSeeded() {
        root.mkdirs()
        if (!prefs.getBoolean("seeded", false)) {
            restoreDefaults()
            prefs.edit().putBoolean("seeded", true).apply()
        }
    }

    fun restoreDefaults() {
        root.mkdirs()
        DefaultFiles.files.forEach { (rel, text) -> write(rel, text) }
    }

    private fun resolve(rel: String): File {
        val f = File(root, rel).canonicalFile
        val r = root.canonicalFile
        require(f.path == r.path || f.path.startsWith(r.path + File.separator)) { "Path outside workspace" }
        return f
    }

    fun join(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"

    fun list(dirRel: String): List<WsEntry> {
        val d = resolve(dirRel)
        val kids = d.listFiles() ?: return emptyList()
        return kids
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { WsEntry(it.name, join(dirRel, it.name), it.isDirectory, if (it.isFile) it.length() else 0L) }
    }

    fun read(rel: String): String = try { resolve(rel).readText() } catch (e: Exception) { "" }

    fun write(rel: String, text: String) {
        val f = resolve(rel)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    fun exists(rel: String): Boolean = try { resolve(rel).exists() } catch (e: Exception) { false }

    fun sanitize(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trimStart('.')

    private fun boilerplate(lang: Lang?): String = when (lang) {
        Lang.PYTHON -> "# New Python file\nprint(\"Hello from Python\")\n"
        Lang.KOTLIN -> "fun main() {\n    println(\"Hello from Kotlin\")\n}\n"
        null -> ""
    }

    fun createFile(dirRel: String, rawName: String, lang: Lang): String? {
        var name = sanitize(rawName)
        if (name.isEmpty()) return null
        if (!name.endsWith(".py") && !name.endsWith(".kt")) name += "." + lang.ext
        val rel = join(dirRel, name)
        if (exists(rel)) return null
        write(rel, boilerplate(Lang.fromExt(name.substringAfterLast('.'))))
        return rel
    }

    fun createFolder(dirRel: String, rawName: String): Boolean {
        val name = sanitize(rawName)
        if (name.isEmpty()) return false
        val f = resolve(join(dirRel, name))
        return !f.exists() && f.mkdirs()
    }

    fun rename(rel: String, rawName: String): Boolean {
        val name = sanitize(rawName)
        if (name.isEmpty()) return false
        val src = resolve(rel)
        val dst = resolve(join(rel.substringBeforeLast('/', ""), name))
        return !dst.exists() && src.renameTo(dst)
    }

    fun delete(rel: String): Boolean = try { resolve(rel).deleteRecursively() } catch (e: Exception) { false }

    fun importFile(dirRel: String, rawName: String, text: String): String? {
        val name = sanitize(rawName)
        if (name.isEmpty()) return null
        var rel = join(dirRel, name)
        var n = 1
        while (exists(rel)) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            rel = join(dirRel, if (ext.isEmpty()) "${base}_$n" else "${base}_$n.$ext")
            n++
        }
        write(rel, text)
        return rel
    }
}
