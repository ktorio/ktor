package io.ktor.graphql

import graphql.ExecutionResult

internal class ExecutionResultData(val isDataPresent: Boolean, val result: ExecutionResult)