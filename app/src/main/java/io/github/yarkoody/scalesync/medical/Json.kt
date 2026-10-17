package io.github.yarkoody.scalesync.medical

/**
 * Minimal JSON reader/writer, so the medical-record logic has no Android dependency and runs in
 * plain JVM unit tests. Values are Map<String, Any?>, List<Any?>, String, Double, Boolean or null.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) p.fail("unexpected text after the end")
        return v
    }

    fun write(v: Any?, indent: String = ""): String = StringBuilder().also { out(it, v, indent, 0) }.toString()

    private fun out(sb: StringBuilder, v: Any?, indent: String, depth: Int) {
        val nl = if (indent.isEmpty()) "" else "\n"
        fun pad(d: Int) { if (indent.isNotEmpty()) repeat(d) { sb.append(indent) } }
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{').append(nl)
                v.entries.forEachIndexed { i, (k, x) ->
                    pad(depth + 1); str(sb, k.toString()); sb.append(if (indent.isEmpty()) ":" else ": ")
                    out(sb, x, indent, depth + 1)
                    if (i < v.size - 1) sb.append(','); sb.append(nl)
                }
                pad(depth); sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('[').append(nl)
                v.forEachIndexed { i, x ->
                    pad(depth + 1); out(sb, x, indent, depth + 1)
                    if (i < v.size - 1) sb.append(','); sb.append(nl)
                }
                pad(depth); sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    class JsonException(msg: String) : Exception(msg)

    private class Parser(val s: String) {
        var i = 0
        fun fail(msg: String): Nothing = throw JsonException("$msg at character $i")
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else fail("unexpected '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }

        fun string(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("unterminated escape")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++; ws()
                m[k] = value()
                ws()
                if (i >= s.length) fail("unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> { i--; fail("expected ',' or '}'") }
                }
            }
        }

        fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws()
                l.add(value())
                ws()
                if (i >= s.length) fail("unterminated list")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> { i--; fail("expected ',' or ']'") }
                }
            }
        }
    }
}
