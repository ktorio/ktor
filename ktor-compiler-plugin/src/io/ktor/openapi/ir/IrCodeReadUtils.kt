package io.ktor.openapi.ir

import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

context(context: CodeGenContext)
fun IrCall.isApplicationCall() =
    receiverIsType("io.ktor.server.application.ApplicationCall")

context(context: CodeGenContext)
fun IrCall.receiverIsType(fqName: String): Boolean =
    context.referenceClass(ClassId.topLevel(FqName(fqName)))
        ?.let { classSymbol -> receiverIsType(classSymbol) }
        ?: false

fun IrCall.receiverIsType(
    classSymbol: IrClassSymbol,
): Boolean {
    val receiver = functionReceiver ?: return false
    return receiver.type.isSubtypeOfClass(classSymbol)
}

val IrCall.functionReceiver get() =
    symbol.owner.parameters.firstOrNull {
        it.kind == IrParameterKind.DispatchReceiver ||
            it.kind == IrParameterKind.ExtensionReceiver
    }

fun IrCall.typeArgsAsMap(): Map<IrTypeParameterSymbol, IrType> {
    val fn = symbol.owner
    val fnTypeParams = fn.typeParameters
    val fnTypeArgs = typeArguments
    return buildMap {
        for (i in 0 until minOf(fnTypeParams.size, fnTypeArgs.size)) {
            val arg = fnTypeArgs[i] ?: continue
            put(fnTypeParams[i].symbol, arg)
        }
    }
}

fun IrType.substituteTypeParameters(scope: Map<IrTypeParameterSymbol, IrType>): IrType {
    fun IrType.substituteRecursively(): IrType {
        val simple = this as? IrSimpleType ?: return this

        // If the classifier *is* a type parameter (e.g., E), replace it with its concrete type.
        val typeParam = simple.classifier as? IrTypeParameterSymbol
        if (typeParam != null) {
            val replacement = scope[typeParam] ?: return this
            // Preserve nullability from the usage site.
            return if (simple.isMarkedNullable())
                replacement.makeNullable()
            else replacement
        }

        // Otherwise, recurse into generic arguments (e.g., List<E>).
        var changed = false
        val newArgs: List<IrTypeArgument> = simple.arguments.map { arg ->
            val proj = arg as? IrTypeProjection ?: return@map arg
            val oldType = proj.type
            val newType = oldType.substituteRecursively()
            if (newType === oldType) arg
            else {
                changed = true
                newType
            }
        }

        if (!changed) return this

        return IrSimpleTypeImpl(
            classifier = simple.classifier,
            hasQuestionMark = simple.isMarkedNullable(),
            arguments = newArgs,
            annotations = simple.annotations
        )
    }

    return substituteRecursively()
}
