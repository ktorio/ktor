package io.ktor.openapi.routing

import io.ktor.openapi.ir.CodeGenContext
import io.ktor.openapi.ir.LambdaBuilderContext
import io.ktor.openapi.ir.toConst
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

sealed interface LocalReference {
    companion object {
        context(context: CodeGenContext)
        fun of(expression: IrExpression): Expression? =
            context.copyAndResolve(expression)
                ?.let(::Expression)

        fun of(stringValue: String) =
            StringValue(stringValue)

        fun of(intValue: Int) =
            IntValue(intValue)
    }

    context(context: LambdaBuilderContext)
    fun asExpression(): IrExpression

    /**
     * A string representation for matching against literal values found in the KDoc.
     */
    val key: String?

    data class Expression(
        val expression: IrExpression
    ): LocalReference {
        context(context: LambdaBuilderContext)
        override fun asExpression(): IrExpression =
            expression.deepCopyWithSymbols(
                context.parentDeclaration as? IrDeclarationParent
            )
        override val key: String?
            get() = null
    }
    data class IntValue(val intValue: Int): LocalReference {
        context(context: LambdaBuilderContext)
        override fun asExpression(): IrConst =
            intValue.toConst()
        override val key: String
            get() = intValue.toString()
    }
    data class StringValue(override val key: String): LocalReference {
        context(context: LambdaBuilderContext)
        override fun asExpression(): IrConst =
            key.toConst()
    }
}
