package io.ktor.openapi.ir

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrType

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class GenerateDsl

fun interface IrDescribeExpressionGenerator<T: RouteField> {
    context(context: LambdaBuilderContext)
    fun generate(fields: List<T>)
}

@GenerateDsl
data class LambdaBuilderContext(
    val pluginContext: CodeGenContext,
    val parentDeclaration: IrDeclaration,
    val lambdaFunction: IrFunction,
    val receiverParameter: IrValueParameter?,
    val receiverType: IrType?,
    val addStatement: (IrStatement) -> Unit
): CodeGenContext by pluginContext

context(context: LambdaBuilderContext)
operator fun IrStatement.unaryPlus() =
    context.addStatement(this)