# ktor-server-graphql

A Ktor server plugin that provides a GraphQL endpoint powered by [graphql-java](https://github.com/graphql-java/graphql-java).

## Features

- **SDL-first schema definition** with runtime wiring via the graphql-java DSL
- **Pre-built schema support** for programmatic schema construction
- **Async execution** using Kotlin coroutines
- **Per-request context** injection from the `ApplicationCall`
- **Custom engine configuration** for instrumentation, execution strategies, etc.
- **GraphiQL IDE** - optional built-in interactive query editor
- **Self-contained JSON handling** - no ContentNegotiation plugin required

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-graphql:$ktor_version")
}
```

## Quick Start

```kotlin
fun Application.module() {
    routing {
        graphQL {
            schema("""
                type Query {
                    hello: String
                    greet(name: String!): String
                }
            """) {
                type("Query") {
                    it.dataFetcher("hello") { "Hello, World!" }
                        .dataFetcher("greet") { env ->
                            "Hello, ${env.getArgument<String>("name")}!"
                        }
                }
            }
        }
    }
}
```

Send a query:

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ hello }"}'
```

Response:

```json
{
  "data": {
    "hello": "Hello, World!"
  }
}
```

## Configuration

### Schema Definition (SDL)

Define your schema using the GraphQL Schema Definition Language and bind data fetchers:

```kotlin
graphQL {
    schema("""
        type Query {
            users: [User]
            user(id: ID!): User
        }

        type User {
            id: ID!
            name: String!
            email: String
        }
    """) {
        type("Query") {
            it.dataFetcher("users") { userService.findAll() }
                .dataFetcher("user") { env ->
                    userService.findById(env.getArgument("id"))
                }
        }
    }
}
```

### Pre-built Schema

Pass a pre-built `GraphQLSchema` instance:

```kotlin
graphQL {
    schema(myPrebuiltSchema)
}
```

Or use a factory function:

```kotlin
graphQL {
    schema {
        SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)
    }
}
```

### Custom Endpoint Path

```kotlin
graphQL {
    endpoint = "api/graphql"  // default: "graphql"
    // ...
}
```

### GraphiQL IDE

Enable the interactive GraphiQL IDE:

```kotlin
graphQL {
    graphiql = true                      // default: false
    graphiqlEndpoint = "playground"      // default: "graphiql"
    // ...
}
```

Then open `http://localhost:8080/graphiql` (or your custom endpoint) in a browser.

### Per-Request Context

Inject values from the `ApplicationCall` into the GraphQL context, making them available to data fetchers:

```kotlin
graphQL {
    context { call ->
        mapOf(
            "userId" to call.principal<UserPrincipal>()?.id,
            "locale" to call.request.headers["Accept-Language"]
        )
    }
    schema(/* ... */) {
        type("Query") {
            it.dataFetcher("profile") { env ->
                val userId = env.graphQlContext.get<String>("userId")
                userService.findById(userId)
            }
        }
    }
}
```

### Custom GraphQL Engine

Customize the `graphql-java` engine for instrumentation, execution strategies, or other advanced settings:

```kotlin
graphQL {
    engine { schema ->
        GraphQL.newGraphQL(schema)
            .instrumentation(myInstrumentation)
            .defaultDataFetcherExceptionHandler(myHandler)
            .build()
    }
    // ...
}
```

## GraphQL Request Format

The plugin accepts POST requests with a JSON body following the [GraphQL over HTTP specification](https://graphql.github.io/graphql-over-http/):

```json
{
  "query": "query GetUser($id: ID!) { user(id: $id) { name email } }",
  "operationName": "GetUser",
  "variables": { "id": "123" },
  "extensions": {}
}
```

| Field           | Type                | Required | Description                          |
|-----------------|---------------------|----------|--------------------------------------|
| `query`         | `String`            | Yes      | The GraphQL query/mutation string    |
| `operationName` | `String`            | No       | Operation name for multi-op queries  |
| `variables`     | `Map<String, Any?>` | No       | Variable values                      |
| `extensions`    | `Map<String, Any?>` | No       | Protocol extensions                  |

## Requirements

- JDK 11 or higher (required by graphql-java)
