package io.ktor.openapi.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol

val KtSourceElement.range: IntRange get() =
    startOffset until endOffset

fun FirFunctionCall.getFunctionName(): String =
    calleeReference.name.asString()

fun FirFunctionCall.isInPackage(packageName: String) =
    calleeReference.symbol?.packageFqName()?.asString() == packageName