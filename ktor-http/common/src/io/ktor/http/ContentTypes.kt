package io.ktor.http

import kotlinx.io.charsets.*

/**
 * Represents a value for a `Content-Type` header.
 * @property contentType represents a type part of the media type.
 * @property contentSubtype represents a subtype part of the media type.
 */
class ContentType private constructor(val contentType: String, val contentSubtype: String, existingContent: String, parameters: List<HeaderValueParam> = emptyList())
    : HeaderValueWithParameters(existingContent, parameters) {

    constructor(contentType: String, contentSubtype: String, parameters: List<HeaderValueParam> = emptyList()) : this(contentType, contentSubtype, "$contentType/$contentSubtype", parameters)

    /**
     * Creates a copy of `this` type with the added parameter with the [name] and [value].
     */
    fun withParameter(name: String, value: String): ContentType {
        if (hasParameter(name, value)) return this

        return ContentType(contentType, contentSubtype, content, parameters + HeaderValueParam(name, value))
    }

    private fun hasParameter(name: String, value: String): Boolean = when (parameters.size) {
        0 -> false
        1 -> parameters[0].let { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
        else -> parameters.any { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
    }

    /**
     * Creates a copy of `this` type without any parameters
     */
    fun withoutParameters(): ContentType = ContentType(contentType, contentSubtype)

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters. 
     */
    fun match(pattern: ContentType): Boolean {
        if (pattern.contentType != "*" && !pattern.contentType.equals(contentType, ignoreCase = true))
            return false
        if (pattern.contentSubtype != "*" && !pattern.contentSubtype.equals(contentSubtype, ignoreCase = true))
            return false
        for ((patternName, patternValue) in pattern.parameters) {
            val matches = when (patternName) {
                "*" -> {
                    when (patternValue) {
                        "*" -> true
                        else -> parameters.any { p -> p.value.equals(patternValue, ignoreCase = true) }
                    }
                }
                else -> {
                    val value = parameter(patternName)
                    when (patternValue) {
                        "*" -> value != null
                        else -> value.equals(patternValue, ignoreCase = true)
                    }
                }
            }
            if (!matches)
                return false
        }
        return true
    }

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters.
     */
    fun match(pattern: String): Boolean = match(ContentType.parse(pattern))

    override fun equals(other: Any?): Boolean =
            other is ContentType &&
                    contentType.equals(other.contentType, ignoreCase = true) &&
                    contentSubtype.equals(other.contentSubtype, ignoreCase = true) &&
                    parameters == other.parameters

    override fun hashCode(): Int {
        var result = contentType.toLowerCase().hashCode()
        result += 31 * result + contentSubtype.toLowerCase().hashCode()
        result += 31 * parameters.hashCode()
        return result
    }

    companion object {
        /**
         * Parses a string representing a `Content-Type` header into a [ContentType] instance.
         */
        fun parse(value: String): ContentType = HeaderValueWithParameters.parse(value) { parts, parameters ->
            val slash = parts.indexOf('/')
            if (slash == -1) {
                if (parts.trim() == "*")
                    return Any
                throw BadContentTypeFormatException(value)
            }
            val type = parts.substring(0, slash).trim()
            if (type.isEmpty())
                throw BadContentTypeFormatException(value)
            val subtype = parts.substring(slash + 1).trim()
            if (subtype.isEmpty() || subtype.contains('/'))
                throw BadContentTypeFormatException(value)
            ContentType(type, subtype, parameters)
        }

        /**
         * Represents a pattern `* / *` to match any content type.
         */
        val Any: ContentType = ContentType("*", "*")
    }

    /**
     * Provides a list of standard subtypes of an `application` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Application {
        /**
         * Represents a pattern `application / *` to match any application content type.
         */
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

    /**
     * Provides a list of standard subtypes of an `audio` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Audio {
        val Any = ContentType("audio", "*")
        val MP4 = ContentType("audio", "mp4")
        val MPEG = ContentType("audio", "mpeg")
        val OGG = ContentType("audio", "ogg")
    }

    /**
     * Provides a list of standard subtypes of an `image` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Image {
        val Any = ContentType("image", "*")
        val GIF = ContentType("image", "gif")
        val JPEG = ContentType("image", "jpeg")
        val PNG = ContentType("image", "png")
        val SVG = ContentType("image", "svg+xml")
        val XIcon = ContentType("image", "x-icon")
    }

    /**
     * Provides a list of standard subtypes of a `message` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Message {
        val Any = ContentType("message", "*")
        val Http = ContentType("message", "http")
    }

    /**
     * Provides a list of standard subtypes of a `multipart` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
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

    /**
     * Provides a list of standard subtypes of a `text` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Text {
        val Any = ContentType("text", "*")
        val Plain = ContentType("text", "plain")
        val CSS = ContentType("text", "css")
        val CSV = ContentType("text", "csv")
        val Html = ContentType("text", "html")
        val JavaScript = ContentType("text", "javascript")
        val VCard = ContentType("text", "vcard")
        val Xml = ContentType("text", "xml")
    }

    /**
     * Provides a list of standard subtypes of a `video` content type.
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Video {
        val Any = ContentType("video", "*")
        val MPEG = ContentType("video", "mpeg")
        val MP4 = ContentType("video", "mp4")
        val OGG = ContentType("video", "ogg")
        val QuickTime = ContentType("video", "quicktime")
    }
}

/**
 * Exception thrown when a content type string is malformed.
 */
class BadContentTypeFormatException(value: String) : Exception("Bad Content-Type format: $value")

/**
 * Creates a copy of `this` type with the added charset parameter with [charset] value.
 */
fun ContentType.withCharset(charset: Charset): ContentType = withParameter("charset", charset.name)

/**
 * Extracts a [Charset] value from the given `Content-Type`, `Content-Disposition` or similar header value.  
 */
fun HeaderValueWithParameters.charset(): Charset? = parameter("charset")?.let { Charset.forName(it) }
