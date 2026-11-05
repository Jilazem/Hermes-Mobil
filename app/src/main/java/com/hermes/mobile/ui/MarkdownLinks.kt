package com.hermes.mobile.ui

/** Read a Markdown destination without truncating balanced URL parentheses. */
internal fun markdownLinkDestination(source: String, open: Int): Pair<Int, String>? {
    if (source.getOrNull(open) != '(') return null
    var depth = 1
    var i = open + 1
    var angled = false
    while (i < source.length) {
        when (source[i]) {
            '\\' -> { i += 2; continue }
            '<' -> angled = true
            '>' -> angled = false
            '(' -> if (!angled) depth++
            ')' -> if (!angled && --depth == 0) {
                val raw = source.substring(open + 1, i).trim()
                val target: String
                val rest: String
                if (raw.startsWith('<')) {
                    val end = raw.indexOf('>')
                    if (end < 0) return null
                    target = raw.substring(1, end)
                    rest = raw.substring(end + 1).trim()
                } else {
                    val end = raw.indexOfFirst { it.isWhitespace() }.let { if (it < 0) raw.length else it }
                    target = raw.substring(0, end)
                    rest = raw.substring(end).trim()
                }
                if (rest.isNotEmpty() && !(rest.length >= 2 && rest.first() == rest.last() && rest.first() in "\"'")) return null
                return i to target.replace(Regex("""\\([()\\])"""), "$1")
            }
        }
        i++
    }
    return null
}

internal fun trimBareLink(raw: String): String {
    var url = raw.trimEnd('.', ',', ';', ':', '!', '?')
    while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) url = url.dropLast(1)
    return url
}
