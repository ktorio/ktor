# Ktor Graphql

Easily serve GraphQL over http together with Ktor.

## Setup

Add the `graphQL` route inside of Ktor's `routing` feature:

```
val server = embeddedServer(Netty, port = 8080) {
        routing {
            graphQL("/graphql", schema) {
                config {
                    graphiql = true
                }
            }
        }
    }

server.start(wait = true)
``` 

The `graphQL` route has two required parameters:
 
- The path associated with the graphQL endpoint.
- A graphQL schema (see [GraphQL-Java](https://github.com/graphql-java/graphql-java)).

Optionally, various options can be passed in a `config`.

## Options

The `config` accepts the following options:

* `graphiql`: If true, then `GraphiQL` is presented when the graphQL endpoint is loaded in the browser. This is useful for testing your application during development.
* `rootValue`: A value passed as the root value in GraphQL-Java's `ExecutionInput` from [`graphql.ExecutionInput`](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/ExecutionInput.java).
* `context` A value passed as the context in in GraphQL-Java's `ExecutionInput` from [`graphql.ExecutionInput`](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/ExecutionInput.java).
* `formatError`: An optional function that it is used to format any errors that occur in completing a GraphQL operation. If not provided, GraphQL's default spec-compliant [`GraphQLError.toSpecification()`](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/GraphQLError.java) is used.

# HTTP usage

`ktor-graphql` will accept requests with the parameters:

* `query`: A string GraphQL document to be executed.
* `variables`: The runtime values to use for any GraphQL query variables as a JSON object.
* `operationName`: If the provided query contains multiple named operations, this specifies which operation should be executed. If not provided, a 400 error will be returned if the query contains multiple named operations.
* `raw`: If the `graphiql` option is enabled and the raw parameter is provided raw JSON will always be returned instead of GraphiQL even when loaded from a browser.

GraphQL will first look for each parameter in the URL's query-string:

```
/graphql?query=query+getUser($id:ID){user(id:$id){name}}&variables={"id":"4"}
```

If not found in the query-string, it will look in the POST request body.

`ktor-graphql` will interpret the POST body depending on the provided *Content-Type* header.

* `application/json`: the POST body will be parsed as a JSON object of parameters.
* `application/graphql`: the POST body will be parsed as GraphQL query string, which provides the query parameter.

## Guides

### Masking exceptions

An unexpected exception may occur in your application that you may not want to expose to consumers of your API because 
they may contain cryptic errors or may expose server-side code. For example, if the query `{ user(id: "1") { name } }` is executed then
the server will respond with `Could not cast db.user to User`. A more constructive error could be 
`Internal server error. Please report this error to errors@domain.com`

In order to do this we introduce an Exception subclass `ClientException`. This exception should be thrown whenever
an error occurs that the user of the API should be notified about. An use case for `ClientException` is an error stating that you need to be authenticated
to perform the operation.

```
class ClientException(message: String): Exception(message)
```

Next, we customize the error behavior in the `formatError` option by customizing the error `message`
depending on whether the original exception is a `ClientException` or not.

```
graphQL("/graphql", schema) {
    config {
        formatError = {
            val clientMessage = if (this is ExceptionWhileDataFetching) {

                val formattedMessage = if (exception is ClientException) {
                    exception.message
                } else {
                    "Internal server error"
                }

                formattedMessage
            } else {
                message
            }

            val result = toSpecification()
            result["message"] = clientMessage

            result
        }
    }
}
```

## Credit

This library is based on the [express-graphql](https://github.com/graphql/express-graphql) library. 

Part of the documentation, API and test cases in this library are based on express-graphql, credit goes to the authors of this library.
