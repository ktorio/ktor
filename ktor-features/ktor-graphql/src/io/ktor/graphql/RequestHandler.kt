package io.ktor.graphql

import graphql.*
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.validation.Validator
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HeaderValue
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.acceptItems
import io.ktor.request.httpMethod
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.graphql.parseRequest.parseGraphQLRequest

internal class RequestHandler(
    private val schema: GraphQLSchema,
    private val setup: (PipelineContext<Unit, ApplicationCall>.(GraphQLRequest) -> GraphQLRouteConfig)?
) {

    private lateinit var request: GraphQLRequest
    private var config = GraphQLRouteConfig()

    private lateinit var context: PipelineContext<Unit, ApplicationCall>
    private val call: ApplicationCall
        get() = context.call

    private val showGraphiQL: Boolean
        get() = config.graphiql && canDisplayGraphiQL

    suspend fun doRequest(requestContext: PipelineContext<Unit, ApplicationCall>) {
        context = requestContext

        val result = try {
            setup()
            execute()
        } catch (exception: Exception) {
            handleException(exception)
        }

        sendResponse(result)
    }

    private suspend fun setup() {
        try {
            request = parseGraphQLRequest(call)
            resolveConfig(request)
        } catch (exception: Exception) {
            // When we failed to parse the GraphQL request, we still need to get
            // the config, so call the setup function with an empty request to do this.
            resolveConfig(GraphQLRequest())
            throw exception
        }
    }

    private fun execute(): ExecutionResultData? {
        val query = request.query

        if (query == null) {

            if (showGraphiQL) {
                return null
            }

            throw HttpException(HttpStatusCode.BadRequest, HttpGraphQLError("Must provide query string."))
        }

        validateHttpMethod()

        val document = parse(query)

        val operation = getOperation(document)

        if (isMutationInGetRequest(operation)) {
            if (showGraphiQL) {
                return null
            }
            call.response.header("Allow", "POST")

            throw HttpException(
                    HttpStatusCode.MethodNotAllowed,
                    HttpGraphQLError("Can only perform a ${operation.toText()} operation from a POST request.")
            )
        }

        checkValidationErrors(document)

        val result = performRequest()

        validateResult(result)

        return ExecutionResultData(isDataPresent = true, result = result)
    }

    private fun handleException(exception: Exception): ExecutionResultData {
        val httpException = parseException(exception)

        call.response.status(httpException.statusCode)

        val result = ExecutionResultImpl(httpException.errors)

        return ExecutionResultData(isDataPresent = false, result = result)
    }

    private suspend fun sendResponse(result: ExecutionResultData?) {

        val formattedResult = if (result != null) formatResult(result, config.formatError) else null

        if (showGraphiQL) {
            call.respondText(renderGraphiQL(formattedResult, request), ContentType.Text.Html)

        } else {

            if (formattedResult == null) {
                throw Exception("Internal error, result can only be null if GraphiQL is requested")
            }

            val jsonResponse = mapper.writeValueAsString(formattedResult)
            call.respondText(jsonResponse, ContentType.Application.Json)
        }
    }

    private fun performRequest(): ExecutionResult {

        val executionInput = ExecutionInput(
                request.query,
                request.operationName,
                config.context,
                config.rootValue,
                request.variables)

        return GraphQL
                .newGraphQL(schema)
                .build()
                .execute(executionInput)

    }

    private fun resolveConfig(request: GraphQLRequest) {
        config = setup?.invoke(context, request) ?: GraphQLRouteConfig()
    }

    // If no data was included in the result, that indicates a runtime query
    // error, indicate as such with a generic status code.
    // Note: Information about the error itself will still be contained in
    // the resulting JSON payload.
    // http://facebook.github.io/graphql/June2018/#sec-Data
    private fun validateResult(result: ExecutionResult) {
        if (result.getData<Any>() == null) {
            call.response.status(HttpStatusCode.InternalServerError)
        }
    }

    private fun validateHttpMethod() {
        if (call.request.httpMethod != HttpMethod.Post && call.request.httpMethod != HttpMethod.Get) {
            call.response.header("allow", "GET, POST")
            throw HttpException(HttpStatusCode.MethodNotAllowed, HttpGraphQLError("GraphQL only supports GET and POST requests."))
        }
    }

    private fun isMutationInGetRequest(operation: OperationDefinition.Operation): Boolean {
        if (call.request.httpMethod == HttpMethod.Get) {
            if (operation != OperationDefinition.Operation.QUERY) {
                return true
            }
        }
        return false
    }

    private fun getOperation(document: Document): OperationDefinition.Operation = getOperation(document, request.operationName)

    private fun parseException(exception: Exception): HttpException {
        return when (exception) {
            is HttpException -> exception
            else -> HttpException(
                    HttpStatusCode.InternalServerError,
                    HttpGraphQLError(exception.message ?: "Internal server error")
            )
        }
    }

    private fun checkValidationErrors(document: Document) {
        val validationErrors = Validator().validateDocument(schema, document)
        if (validationErrors.isNotEmpty()) {
            throw HttpException(HttpStatusCode.BadRequest, validationErrors)
        }
    }

    private fun parse(query: String): Document {
        return try {
            Parser().parseDocument(query)
        } catch (exception: Exception) {
            val syntaxError = InvalidSyntaxError.toInvalidSyntaxError(exception)
            throw HttpException(HttpStatusCode.BadRequest, syntaxError)
        }
    }

    private val canDisplayGraphiQL: Boolean
        get() {
            // If `raw` exists, GraphiQL mode is not enabled.
            // Allowed to show GraphiQL if not requested as raw and this request
            // prefers HTML over JSON.
            val htmlText =  HeaderValue("text/html")
            val isRawRequest = call.parameters["raw"] != null

            if (isRawRequest) {
                return false
            }

            val acceptItems = call.request.acceptItems()

            if (acceptItems.isEmpty()) {
                return false
            }

            return acceptItems[0] == htmlText
        }
}
