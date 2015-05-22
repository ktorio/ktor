package ktor.application

import java.util.ArrayList

fun ApplicationResponse.contentType(value : ContentType) = contentType(value.toString())

class ContentType(val contentType: String, val contentSubtype: String, val parameters: List<Pair<String, String>> = listOf()) {

    override fun toString() = if (parameters.size() == 0) "$contentType/$contentSubtype" else "$contentType/$contentSubtype; ${parameters.map { "${it.first}=${it.second}" }.join("; ")}"

    fun withParameter(name: String, value: String): ContentType {
        val newParameters = ArrayList<Pair<String, String>>(parameters)
        newParameters.add(name to value)
        return ContentType(contentType, contentSubtype, newParameters)
    }

    override fun equals(other: Any?) = when (other) {
        is ContentType -> contentType == other.contentType
        && contentSubtype == other.contentSubtype
        && parameters.size() == other.parameters.size()
        && parameters.withIndex().all { it.value == other.parameters[it.index] }
        else -> false
    }

    companion object {
        fun parse(value: String): ContentType {
            val parts = value.split(";")
            val content = parts[0].split("/")
            if (content.size() != 2)
                throw BadContentTypeFormat(value)
            val parameters = parts.drop(1).map {
                val pair = it.trim().split("=")
                if (pair.size() != 2)
                    throw BadContentTypeFormat(value)
                pair[0].trim() to pair[1].trim()
            }
            return ContentType(content[0].trim(), content[1].trim(), parameters)
        }

        val Any = ContentType("*", "*")
    }

    object Application {
        val Any by AnyReflectionContentTypeProperty()
        val Atom by XmlReflectionContentTypeProperty()
        val Json  by ReflectionContentTypeProperty()
        val JavaScript  by ReflectionContentTypeProperty()
        val Octet_Stream  by ReflectionContentTypeProperty()
        val Font_Woff  by ReflectionContentTypeProperty()
        val Rss  by XmlReflectionContentTypeProperty()
        val Xml  by ReflectionContentTypeProperty()
        val Xml_Dtd  by ReflectionContentTypeProperty()
        val Zip  by ReflectionContentTypeProperty()
        val GZip  by ReflectionContentTypeProperty()
    }
    object Audio {
        val Any by AnyReflectionContentTypeProperty()
        val MP4  by ReflectionContentTypeProperty()
        val MPEG  by ReflectionContentTypeProperty()
        val OGG  by ReflectionContentTypeProperty()
    }
    object Image {
        val Any by AnyReflectionContentTypeProperty()
        val GIF  by ReflectionContentTypeProperty()
        val JPEG  by ReflectionContentTypeProperty()
        val PNG  by ReflectionContentTypeProperty()
        val SVG  by XmlReflectionContentTypeProperty()
    }
    object Message {
        val Any by AnyReflectionContentTypeProperty()
        val Http  by ReflectionContentTypeProperty()
    }
    object MultiPart {
        val Any by AnyReflectionContentTypeProperty()
        val Mixed  by ReflectionContentTypeProperty()
        val Alternative  by ReflectionContentTypeProperty()
        val Related  by ReflectionContentTypeProperty()
        val Form_Data  by ReflectionContentTypeProperty()
        val Signed  by ReflectionContentTypeProperty()
        val Encrypted  by ReflectionContentTypeProperty()
    }
    object Text {
        val Any by AnyReflectionContentTypeProperty()
        val Plain by ReflectionContentTypeProperty()
        val CSS by ReflectionContentTypeProperty()
        val Html by ReflectionContentTypeProperty()
        val JavaScript by ReflectionContentTypeProperty()
        val VCard by ReflectionContentTypeProperty()
        val Xml by ReflectionContentTypeProperty()
    }
    object Video {
        val Any by AnyReflectionContentTypeProperty()
        val MPEG  by ReflectionContentTypeProperty()
        val MP4  by ReflectionContentTypeProperty()
        val OGG  by ReflectionContentTypeProperty()
        val QuickTime  by ReflectionContentTypeProperty()
    }
}


class BadContentTypeFormat(value: String) : Exception("Bad Content-Type format: $value")

class ReflectionContentTypeProperty(val parameters: List<Pair<String, String>> = listOf()) {
    public fun get(group: Any, property: PropertyMetadata): ContentType {
        val contentType = group.javaClass.getSimpleName().toLowerCase()
        val contentSubtype = property.name.toLowerCase().replace("_", "-")
        return ContentType(contentType, contentSubtype, parameters)
    }
}

class XmlReflectionContentTypeProperty(val parameters: List<Pair<String, String>> = listOf()) {
    public fun get(group: Any, property: PropertyMetadata): ContentType {
        val contentType = group.javaClass.getSimpleName().toLowerCase()
        val contentSubtype = property.name.toLowerCase().replace("_", "-") + "+xml"
        return ContentType(contentType, contentSubtype, parameters)
    }
}

class AnyReflectionContentTypeProperty(val parameters: List<Pair<String, String>> = listOf()) {
    public fun get(group: Any, property: PropertyMetadata): ContentType {
        val contentType = group.javaClass.getSimpleName().toLowerCase()
        val contentSubtype = "*"
        return ContentType(contentType, contentSubtype, parameters)
    }
}
