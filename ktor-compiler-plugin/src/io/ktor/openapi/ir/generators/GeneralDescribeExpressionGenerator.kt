package io.ktor.openapi.ir.generators

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*

class GeneralDescribeExpressionGenerator(
    val delegateParameterField: (RouteField.Parameter) -> Unit = {},
    val delegateResponseField: (RouteField) -> Unit = {},
) : IrDescribeExpressionGenerator<RouteField> {
    context(context: LambdaBuilderContext)
    override fun generate(fields: List<RouteField>) {
        for (field in fields) {
            try {
                when (field) {
                    // operationId = "..."
                    is RouteField.OperationId -> {
                        assignProperty("operationId", field.value)
                    }

                    // deprecated = true
                    is RouteField.Deprecated -> {
                        assignProperty("deprecated", field.reason.isNotEmpty())
                    }

                    // description = "..."
                    is RouteField.Description -> {
                        assignProperty("description", field.text)
                    }

                    // summary = "..."
                    is RouteField.Summary -> {
                        assignProperty("summary", field.text)
                    }

                    // externalDocs = ExternalDocs("url", "description")
                    is RouteField.ExternalDocs -> {
                        assignProperty("externalDocs", field.url)
                    }

                    // requestBody { ... }
                    is RouteField.Body -> {
                        +callFunctionWithScope("requestBody") {
                            generateMediaTypeContent(field)
                        }
                    }

                    // security {
                    //     requirement("foo", listOf("scopes"))
                    // }
                    is RouteField.Security -> {
                        // security without scheme is ignored
                        field.scheme?.let { scheme ->
                            +callFunctionWithScope("security") {
                                // TODO scopes as list argument
                                +callFunctionNamed("requirement", scheme.toConst())
                            }
                        }
                    }

                    // tag("value")
                    is RouteField.Tag -> {
                        +callFunctionNamed("tag", field.name.toConst())
                    }

                    // responses { ... }
                    is RouteField.Response,
                    is RouteField.ResponseHeader -> {
                        delegateResponseField(field)
                    }

                    // parameters { cookie("X-Token") { ... } }
                    is RouteField.Parameter -> {
                        delegateParameterField(field)
                    }

                    // should not hit this
                    RouteField.Ignore -> error("Expected ignored to be filtered out")
                }
            } catch (cause: Throwable) {
                context.log("Failed to generate code for $field", cause)
            }
        }
    }
}