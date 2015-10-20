package org.jetbrains.ktor.http

data class ContentTypeParameter(val name: String, val value: String)
class ContentType(val contentType: String, val contentSubtype: String, val parameters: List<ContentTypeParameter> = listOf()) {

    override fun toString() = if (parameters.isEmpty())
        "$contentType/$contentSubtype"
    else
        "$contentType/$contentSubtype; ${parameters.map { "${it.name}=${it.value}" }.joinToString("; ")}"

    fun parameter(name: String) = parameters.firstOrNull { it.name == name }?.value

    fun withParameter(name: String, value: String): ContentType {
        return ContentType(contentType, contentSubtype, parameters + ContentTypeParameter(name, value))
    }

    fun match(other: ContentType): Boolean =
            (other.contentType == "*" || other.contentType == contentType)
            && (other.contentSubtype == "*" || other.contentSubtype == contentSubtype)
            && (other.parameters.filter { it.name != "*" && it.value != "*" }.all { parameter(it.name) == it.value })
            && (other.parameters.filter { it.name != "*" && it.value == "*" }.all { parameter(it.name) != null })
            && (other.parameters.filter { it.name == "*" && it.value != "*" }.all { this.parameters.any { p -> p.value == it.value } })

    fun match(pattern: String): Boolean = match(ContentType.parse(pattern))

    override fun equals(other: Any?) = when (other) {
        is ContentType -> contentType == other.contentType
                && contentSubtype == other.contentSubtype
                && parameters.size == other.parameters.size
                // TODO: does equality necessary impose order of parameters?
                && parameters.withIndex().all { it.value == other.parameters[it.index] }
        else -> false
    }

    companion object {
        fun parse(value: String): ContentType {
            val parts = value.split(";")
            val content = parts[0].split("/")
            if (content.size != 2)
                throw BadContentTypeFormatException(value)
            val parameters = parts.drop(1).filter { it.isNotBlank() }.map {
                val pair = it.trim().split("=")
                if (pair.size != 2)
                    throw BadContentTypeFormatException(value)
                ContentTypeParameter(pair[0].trim(), pair[1].trim())
            }
            return ContentType(content[0].trim(), content[1].trim(), parameters)
        }

        val Any = ContentType("*", "*")
    }

    object Application {
        val Any = ContentType("application", "*")
        val Atom = ContentType("application", "atom+xml")
        val Json = ContentType("application", "json")
        val JavaScript = ContentType("application", "javascript")
        val OctetStream = ContentType("application", "octet-stream")
        val FontWoff = ContentType("application", "font-woff")
        val Rss = ContentType("application", "rss+xml")
        val Xml = ContentType("application", "xml")
        val Xml_Dtd = ContentType("application", "xml-dtd")
        val Zip = ContentType("application", "zip")
        val GZip = ContentType("application", "gzip")
        val FormUrlEncoded = ContentType("application", "x-www-form-urlencoded")
    }

    object Audio {
        val Any = ContentType("audio", "*")
        val MP4 = ContentType("audio", "mp4")
        val MPEG = ContentType("audio", "mpeg")
        val OGG = ContentType("audio", "ogg")
    }

    object Image {
        val Any = ContentType("image", "*")
        val GIF = ContentType("image", "gif")
        val JPEG = ContentType("image", "jpeg")
        val PNG = ContentType("image", "png")
        val SVG = ContentType("image", "svg+xml")
        val XIcon = ContentType("image", "x-icon")
    }

    object Message {
        val Any = ContentType("message", "*")
        val Http = ContentType("message", "http")
    }

    object MultiPart {
        val Any = ContentType("multipart", "*")
        val Mixed = ContentType("multipart", "mixed")
        val Alternative = ContentType("multipart", "alternative")
        val Related = ContentType("multipart", "related")
        val FormData = ContentType("multipart", "form-data")
        val Signed = ContentType("multipart", "signed")
        val Encrypted = ContentType("multipart", "encrypted")
    }

    object Text {
        val Any = ContentType("text", "*")
        val Plain = ContentType("text", "plain")
        val CSS = ContentType("text", "css")
        val Html = ContentType("text", "html")
        val JavaScript = ContentType("text", "javascript")
        val VCard = ContentType("text", "vcard")
        val Xml = ContentType("text", "xml")
    }

    object Video {
        val Any = ContentType("video", "*")
        val MPEG = ContentType("video", "mpeg")
        val MP4 = ContentType("video", "mp4")
        val OGG = ContentType("video", "ogg")
        val QuickTime = ContentType("video", "quicktime")
    }
}

class BadContentTypeFormatException(value: String) : Exception("Bad Content-Type format: $value")