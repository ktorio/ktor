package io.ktor.graphql

import graphql.language.Document
import graphql.language.NodeUtil
import graphql.language.OperationDefinition

internal fun OperationDefinition.Operation.toText(): String = when(this) {
    OperationDefinition.Operation.QUERY -> "query"
    OperationDefinition.Operation.MUTATION -> "mutation"
    OperationDefinition.Operation.SUBSCRIPTION -> "subscription"
}

internal fun getOperation(document: Document, operationName: String?) = NodeUtil.getOperation(document, operationName).operationDefinition.operation