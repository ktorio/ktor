package io.ktor.http

/**
 * Represents `Content-Disposition` header value
 */
class ContentDisposition(disposition: String, parameters: List<HeaderValueParam> = emptyList()) :
    HeaderValueWithParameters(disposition, parameters) {
    /**
     * Content disposition value without parameters
     */
    val disposition: String get() = content

    /**
     * Content disposition name (from parameter named `name`)
     */
    val name: String?
        get() = parameter(Parameters.Name)

    /**
     * Creates new with parameter appended
     */
    fun withParameter(key: String, value: String): ContentDisposition =
        ContentDisposition(disposition, parameters + HeaderValueParam(key, value))

    /**
     * Creates new with parameters appended
     */
    fun withParameters(newParameters: List<HeaderValueParam>): ContentDisposition =
        ContentDisposition(disposition, parameters + newParameters)

    override fun equals(other: Any?): Boolean =
        other is ContentDisposition &&
            disposition == other.disposition &&
            parameters == other.parameters

    override fun hashCode(): Int = disposition.hashCode() * 31 + parameters.hashCode()

    @Suppress("unused", "PublicApiImplicitType")
    companion object {
        /**
         * `Content-Disposition: file`
         */
        val File = ContentDisposition("file")

        /**
         * `Content-Disposition: mixed`
         */
        val Mixed = ContentDisposition("mixed")

        /**
         * `Content-Disposition: attachment`
         */
        val Attachment = ContentDisposition("attachment")

        /**
         * `Content-Disposition: inline`
         */
        val Inline = ContentDisposition("inline")

        /**
         * Parse `Content-Disposition` header [value]
         */
        fun parse(value: String): ContentDisposition = parse(value) { v, p -> ContentDisposition(v, p) }
    }

    /**
     * Frequently used content disposition parameter names
     */
    @Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType")
    object Parameters {
        const val FileName = "filename"
        const val FileNameAsterisk = "filename*"
        const val Name = "name"
        const val CreationDate = "creation-date"
        const val ModificationDate = "modification-date"
        const val ReadDate = "read-date"
        const val Size = "size"
        const val Handling = "handling"
    }
}
