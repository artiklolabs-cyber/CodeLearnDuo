package com.codelearn.duo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val PY_KEYWORDS = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
    "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
    "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
)
private val PY_BUILTINS = setOf(
    "print", "len", "range", "int", "float", "str", "list", "dict", "set", "tuple", "sum", "min", "max",
    "sorted", "input", "open", "type", "enumerate", "zip", "map", "filter", "abs", "round", "bool"
)
private val KT_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
    "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var",
    "when", "while", "by", "catch", "constructor", "data", "enum", "finally", "import", "init", "override",
    "private", "public", "protected", "sealed", "open", "abstract", "companion", "lateinit", "const",
    "inline", "internal", "suspend", "vararg", "until", "downTo", "step"
)

private val PY_REGEX = Regex("""(#[^\n]*)|("{3}[\s\S]*?"{3}|'{3}[\s\S]*?'{3}|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(\b\d+(?:\.\d+)?\b)|([A-Za-z_][A-Za-z0-9_]*)""")
private val KT_REGEX = Regex("""(//[^\n]*|/\*[\s\S]*?\*/)|("{3}[\s\S]*?"{3}|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(\b\d+(?:\.\d+)?[fFL]?\b)|([A-Za-z_][A-Za-z0-9_]*)""")

private val ColComment = Color(0xFF6B7280)
private val ColString = Color(0xFF86EFAC)
private val ColNumber = Color(0xFFFBBF24)
private val ColKeyword = Color(0xFFC084FC)
private val ColBuiltin = Color(0xFF60A5FA)
private val ColFunction = Color(0xFF7DD3FC)

fun highlight(code: String, lang: Lang): AnnotatedString {
    val regex = if (lang == Lang.PYTHON) PY_REGEX else KT_REGEX
    val keywords = if (lang == Lang.PYTHON) PY_KEYWORDS else KT_KEYWORDS
    return buildAnnotatedString {
        append(code)
        for (m in regex.findAll(code)) {
            val g = m.groups
            val style: SpanStyle? = when {
                g[1] != null -> SpanStyle(color = ColComment, fontStyle = FontStyle.Italic)
                g[2] != null -> SpanStyle(color = ColString)
                g[3] != null -> SpanStyle(color = ColNumber)
                else -> {
                    val w = m.value
                    when {
                        w in keywords -> SpanStyle(color = ColKeyword)
                        lang == Lang.PYTHON && w in PY_BUILTINS -> SpanStyle(color = ColBuiltin)
                        code.getOrNull(m.range.last + 1) == '(' -> SpanStyle(color = ColFunction)
                        else -> null
                    }
                }
            }
            if (style != null) addStyle(style, m.range.first, m.range.last + 1)
        }
    }
}

class HighlightTransformation(private val lang: Lang) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, lang), OffsetMapping.Identity)
}
