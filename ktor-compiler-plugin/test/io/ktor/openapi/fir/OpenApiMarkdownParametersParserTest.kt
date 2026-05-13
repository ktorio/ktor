package io.ktor.openapi.fir

import io.ktor.openapi.*
import io.ktor.openapi.model.SchemaAttribute
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiMarkdownParametersParserTest {

    private val logLines = mutableListOf<String>()
    private val logger = Logger { message, _, _ ->
        logLines += message
    }
    private val packageName = FqName("com.acme")

    @Test
    fun `summary only`() {
        val input = """
            ## Test
            This only contains a summary.
            There should be no properties.
        """.trimIndent()

        assertParsed(input, RouteField.Summary(input))
    }

    @Test
    fun `single response`() {
        assertParsed(
            """
                Get a list of books
                
                - Response: 200 application/xml [Book]+ A list of books
            """.trimIndent(),
            RouteField.Summary("Get a list of books"),
            RouteField.Response(
                LocalReference.of(200),
                LocalReference.of("application/xml"),
                schemaReference("[Book]+"),
                "A list of books"
            ),
        )
    }

    @Test
    fun `multiple responses`() {
        assertParsed(
            """
                Get a list of books
                - Responses: 
                  - 200 application/xml [Book]+ A list of books
                  - 400 You messed up
            """.trimIndent(),
            RouteField.Summary("Get a list of books"),
            RouteField.Response(
                LocalReference.of(200),
                LocalReference.of("application/xml"),
                schemaReference("[Book]+"),
                "A list of books"
            ),
            RouteField.Response(
                LocalReference.of(400),
                contentType = null,
                typeReference = null,
                "You messed up"
            ),
        )
    }

    @Test
    fun `parameters with attributes`() {
        assertParsed(
            """
                Get a list of books
                - Query parameters:
                  - author [String] The name of the author
                      pattern: [a-zA-Z ]+
                      maxLength: 100
                  - searchString [String] The search string
                - Header: X-Token Your session token
            """.trimIndent(),
            RouteField.Summary("Get a list of books"),
            RouteField.Parameter(
                ParamIn.QUERY,
                LocalReference.of("author"),
                schemaReference("[String]"),
                "The name of the author",
                mapOf(
                    SchemaAttribute.pattern to "[a-zA-Z ]+",
                    SchemaAttribute.maxLength to "100"
                )
            ),
            RouteField.Parameter(
                ParamIn.QUERY,
                LocalReference.of("searchString"),
                schemaReference("[String]"),
                "The search string",
            ),
            RouteField.Parameter(
                ParamIn.HEADER,
                LocalReference.of("X-Token"),
                null,
                "Your session token",
            )
        )
    }

    @Test
    fun `ignored endpoint`() {
        assertParsed(
            """
                Get a list of books
                Ignore!
            """.trimIndent(),
            RouteField.Summary("Get a list of books"),
            RouteField.Ignore,
        )
    }

    private fun assertParsed(input: String, vararg expected: RouteField) {
        val commentLines = input.lines().map(::CommentLine)
        val actual = parseMarkdownParameters(commentLines, logger, packageName)
        assertEquals(listOf(*expected), actual)
    }

    private fun schemaReference(text: String): TypeReference {
        val (prefix, name, postfix) = schemaArgRegex.matchEntire(text)?.destructured
            ?: error("Invalid schema reference: $text")
        return getSchemaReference(prefix, name, postfix, packageName)
    }

}