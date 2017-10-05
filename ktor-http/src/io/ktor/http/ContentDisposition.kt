package io.ktor.http

class ContentDisposition(disposition: String, parameters: List<HeaderValueParam> = emptyList()) : HeaderValueWithParameters(disposition, parameters) {
    val disposition : String get() = content

    val name: String?
        get() = parameter(Parameters.Name)

    fun withParameter(key: String, value: String) = ContentDisposition(disposition, parameters + HeaderValueParam(key, value))
    fun withParameters(newParameters: List<HeaderValueParam>) = ContentDisposition(disposition, parameters + newParameters)

    override fun equals(other: Any?): Boolean =
        other is ContentDisposition &&
        disposition == other.disposition &&
        parameters == other.parameters

    override fun hashCode(): Int = disposition.hashCode() * 31 + parameters.hashCode()

    companion object {
        val File = ContentDisposition("file")
        val Mixed = ContentDisposition("mixed")
        val Attachment = ContentDisposition("attachment")
        val Inline = ContentDisposition("inline")

        fun parse(value: String) = parse(value) { v, p -> ContentDisposition(v, p) }
    }

    object Parameters {
        val FileName = "filename"
        val FileNameAsterisk = "filename*"
        val Name = "name"
        val CreationDate = "creation-date"
        val ModificationDate = "modification-date"
        val ReadDate = "read-date"
        val Size = "size"
        val Handling = "handling"
    }
}