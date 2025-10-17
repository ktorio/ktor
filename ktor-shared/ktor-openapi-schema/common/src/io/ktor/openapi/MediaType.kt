package io.ktor.openapi

import io.ktor.openapi.ReferenceOr.Value
import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/** Each Media Type Object provides schema and examples for the media type identified by its key. */
@Serializable(MediaType.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class MediaType(
    /** The schema defining the content of the request, response, or parameter. */
    public val schema: ReferenceOr<Schema>? = null,
    /**
     * Examples of the media type. Each example object SHOULD match the media type and specified
     * schema if present. The examples field is mutually exclusive of the example field. Furthermore,
     * if referencing a schema which contains an example, the examples value SHALL override the
     * example provided by the schema.
     */
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    /**
     * A map between a property name and its encoding information. The key, being the property name,
     * MUST exist in the schema as a property. The encoding object SHALL only apply to requestBody
     * objects when the media type is multipart or application/x-www-form-urlencoded.
     */
    public val encoding: Map<String, Encoding>? = null,
    /**
     * Any additional external documentation for this media type.
     */
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<MediaType>(
            generatedSerializer(),
            { mt, extensions -> mt.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [MediaType] instance. */
    @KtorDsl
    public class Builder {
        /** The schema defining the content. */
        public var schema: Schema? = null

        private val _examples = mutableMapOf<String, ReferenceOr<ExampleObject>>()
        private val _encoding = mutableMapOf<String, Encoding>()

        /** Examples of the media type. */
        public val examples: Map<String, ReferenceOr<ExampleObject>> get() = _examples

        /** Encoding information for properties. */
        public val encoding: Map<String, Encoding> get() = _encoding

        /** Specification-extensions for this media type. */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Adds an example for this media type.
         *
         * @param name The example identifier.
         * @param example The example object.
         */
        public fun example(name: String, example: ExampleObject) {
            _examples[name] = Value(example)
        }

        /**
         * Adds encoding information for a property.
         *
         * @param propertyName The property name.
         * @param encoding The encoding configuration.
         */
        public fun encoding(propertyName: String, encoding: Encoding) {
            _encoding[propertyName] = encoding
        }

        /**
         * Adds a specification-extension.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        internal fun build(): MediaType {
            return MediaType(
                schema = schema?.let(::Value),
                examples = _examples.ifEmpty { null },
                encoding = _encoding.ifEmpty { null },
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}
