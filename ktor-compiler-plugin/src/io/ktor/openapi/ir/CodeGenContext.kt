package io.ktor.openapi.ir

import io.ktor.openapi.Logger
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

interface CodeGenContext: Logger, IrPluginContext {
    val irFile: IrFile?

    fun copyAndResolve(expression: IrExpression): IrExpression?

    fun inferConcreteType(type: IrType): IrType
}
