package org.jetbrains.ktor.http

class ContentType(val contentType: String, val contentSubtype: String, parameters: List<HeaderValueParam> = emptyList()) : HeaderValueWithParameters("$contentType/$contentSubtype", parameters) {
    fun withParameter(name: String, value: String): ContentType {
        return ContentType(contentType, contentSubtype, parameters + HeaderValueParam(name, value))
    }
    fun withoutParameters() = ContentType(contentType, contentSubtype)

    fun match(other: ContentType): Boolean =
            (other.contentType == "*" || other.contentType == contentType)
            && (other.contentSubtype == "*" || other.contentSubtype == contentSubtype)
            && (other.parameters.filter { it.name != "*" && it.value != "*" }.all { parameter(it.name) == it.value })
            && (other.parameters.filter { it.name != "*" && it.value == "*" }.all { parameter(it.name) != null })
            && (other.parameters.filter { it.name == "*" && it.value != "*" }.all { this.parameters.any { p -> p.value == it.value } })

    fun match(pattern: String): Boolean = match(ContentType.parse(pattern))

    override fun equals(other: Any?): Boolean =
        other is ContentType &&
        contentType == other.contentType &&
        contentSubtype == other.contentSubtype &&
        parameters == other.parameters

    override fun hashCode(): Int{
        var result = contentType.hashCode()
        result += 31 * result + contentSubtype.hashCode()
        result += 31 * parameters.hashCode()
        return result
    }

    companion object {
        fun parse(value: String): ContentType = HeaderValueWithParameters.parse(value) { parts, parameters ->
            val content = parts.split("/")
            if (content.size != 2)
                throw BadContentTypeFormatException(value)

            ContentType(content[0].trim(), content[1].trim(), parameters)
        }

        val Any = ContentType("*", "*")
    }

    @Suppress("unused")
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

    @Suppress("unused")
    object Audio {
        val Any = ContentType("audio", "*")
        val MP4 = ContentType("audio", "mp4")
        val MPEG = ContentType("audio", "mpeg")
        val OGG = ContentType("audio", "ogg")
    }

    @Suppress("unused")
    object Image {
        val Any = ContentType("image", "*")
        val GIF = ContentType("image", "gif")
        val JPEG = ContentType("image", "jpeg")
        val PNG = ContentType("image", "png")
        val SVG = ContentType("image", "svg+xml")
        val XIcon = ContentType("image", "x-icon")
    }

    @Suppress("unused")
    object Message {
        val Any = ContentType("message", "*")
        val Http = ContentType("message", "http")
    }

    @Suppress("unused")
    object MultiPart {
        val Any = ContentType("multipart", "*")
        val Mixed = ContentType("multipart", "mixed")
        val Alternative = ContentType("multipart", "alternative")
        val Related = ContentType("multipart", "related")
        val FormData = ContentType("multipart", "form-data")
        val Signed = ContentType("multipart", "signed")
        val Encrypted = ContentType("multipart", "encrypted")
        val ByteRanges = ContentType("multipart", "byteranges")
    }

    @Suppress("unused")
    object Text {
        val Any = ContentType("text", "*")
        val Plain = ContentType("text", "plain")
        val CSS = ContentType("text", "css")
        val Html = ContentType("text", "html")
        val JavaScript = ContentType("text", "javascript")
        val VCard = ContentType("text", "vcard")
        val Xml = ContentType("text", "xml")
    }

    @Suppress("unused")
    object Video {
        val Any = ContentType("video", "*")
        val MPEG = ContentType("video", "mpeg")
        val MP4 = ContentType("video", "mp4")
        val OGG = ContentType("video", "ogg")
        val QuickTime = ContentType("video", "quicktime")
    }
}

class BadContentTypeFormatException(value: String) : Exception("Bad Content-Type format: $value")
