import java.nio.file.Path

class KDocUpdater {
    private val kdocs = mutableListOf<KDocLocation>()

    fun collectForUpdate(startOffset: Int, endOffset: Int, fqname: String) {
        kdocs += KDocLocation(startOffset, endOffset, fqname)
    }

    fun executeUpdate(file: Path, link: String) {
        val content = file.toFile().readText()
        kdocs.sortBy { it.startOffset }

        val newContent = StringBuilder()
        var lastUsedIndex = 0
        for ((start, end, fqname) in kdocs) {
            newContent.append(content.substring(lastUsedIndex, start))
            lastUsedIndex = end

            val kdoc = content.substring(start, end)
            val newKdoc = updateKDocWithLink(kdoc, link, fqname)
            newContent.append(newKdoc)
        }

        newContent.append(content.substring(lastUsedIndex))
        val updatedContent = newContent.toString()
        if (updatedContent != content) {
            file.toFile().writeText(updatedContent)
        }
    }
}

private val KDOC_TAGS = listOf(
    "param",
    "property",
    "return",
    "constructor",
    "receiver",
    "throws",
    "exception",
    "see",
    "author",
    "since",
    "suppress",
    "sample"
)

internal fun updateKDocWithLink(content: String, link: String, fqname: String): String {
    val sanitizedContent = removeFeedbackLinksFromKDoc(content)
    val lines = sanitizedContent.split("\n")
    if (lines.any { isKDocTagLine(it, "suppress") }) return sanitizedContent
    if (lines.size == 1) {
        return buildSingleLineKDoc(lines.first(), link, fqname)
    }

    var insertionIndex = lines.indexOfFirst(::isKDocTagLine)
    if (insertionIndex == -1) {
        insertionIndex = lines.size - 1
    }

    val indent = lines[1].takeWhile { it == ' ' }
    val blankLine = "$indent*"

    val updatedContent = buildString {
        var lastLine = ""
        fun appendLineNoDuplicates(line: String) {
            val trimmedLine = line.trimEnd()
            if (trimmedLine == lastLine && trimmedLine == blankLine) return
            appendLine(trimmedLine)
            lastLine = trimmedLine
        }

        for (i in 0 until insertionIndex) {
            appendLineNoDuplicates(lines[i])
        }

        appendLineNoDuplicates(blankLine)
        appendLineNoDuplicates(indent + formatLink(link, fqname))

        if (insertionIndex != lines.size - 1) {
            appendLineNoDuplicates(blankLine)
        }

        for (i in insertionIndex until lines.size - 1) {
            appendLineNoDuplicates(lines[i])
        }

        append(lines.last())
    }
    return removeBoundaryBlankLinesFromKDoc(updatedContent)
}

internal fun removeFeedbackLinksFromKDoc(content: String): String {
    val lines = content.split("\n").toMutableList()
    var linkIndex = lines.indexOfFirst(::isFormattedLink)
    while (linkIndex != -1) {
        val previousBlankLine = lines.getOrNull(linkIndex - 1)?.takeIf { it.isBlankKDocLine() }
        val nextBlankLine = lines.getOrNull(linkIndex + 1)?.takeIf { it.isBlankKDocLine() }
        val nextContentIndex = linkIndex + 1 + if (nextBlankLine == null) 0 else 1
        val keepBlankLine = lines.getOrNull(nextContentIndex)?.let(::isKDocTagLine) == true
        val removalIndex = if (previousBlankLine == null) linkIndex else linkIndex - 1
        val removalCount = 1 + listOfNotNull(previousBlankLine, nextBlankLine).size

        repeat(removalCount) { lines.removeAt(removalIndex) }
        if (keepBlankLine) {
            lines.add(removalIndex, previousBlankLine ?: nextBlankLine!!)
        }

        linkIndex = lines.indexOfFirst(::isFormattedLink)
    }

    return removeBoundaryBlankLinesFromKDoc(lines.joinToString("\n"))
}

private fun removeBoundaryBlankLinesFromKDoc(content: String): String {
    val lines = content.split("\n").toMutableList()
    while (lines.getOrNull(1)?.isBlankKDocLine() == true) {
        lines.removeAt(1)
    }
    while (lines.getOrNull(lines.lastIndex - 1)?.isBlankKDocLine() == true) {
        lines.removeAt(lines.lastIndex - 1)
    }
    return lines.joinToString("\n")
}

private fun isKDocTagLine(line: String): Boolean {
    return KDOC_TAGS.any { tag -> isKDocTagLine(line, tag) }
}

private fun isKDocTagLine(line: String, tag: String): Boolean {
    val content = line.trimStart().removePrefix("*").trimStart()
    return content == "@$tag" || content.startsWith("@$tag ") || content.startsWith("@$tag\t")
}

private fun String.isBlankKDocLine(): Boolean = trim().let { it.isEmpty() || it == "*" }

private const val LINK_TITLE = "Report a problem"

private fun isFormattedLink(line: String): Boolean {
    return line.trimStart().startsWith("* [$LINK_TITLE]")
}

private fun formatLink(link: String, fqname: String): String {
    return "* [$LINK_TITLE]($link?fqname=$fqname)"
}

private fun buildSingleLineKDoc(line: String, link: String, fqname: String): String {
    val indent = line.takeWhile { it.isWhitespace() }
    val rawKDoc = line.substringAfter("/**").substringBeforeLast("*/").trim()

    return buildString {
        appendLine("$indent/**")
        appendLine("$indent * $rawKDoc")
        appendLine("$indent *")
        appendLine("$indent ${formatLink(link, fqname)}")
        append("$indent */")
    }
}