package io.ktor.openapi.ir

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildReceiverParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.interpreter.IrInterpreter
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterEnvironment
import org.jetbrains.kotlin.ir.interpreter.checker.EvaluationMode
import org.jetbrains.kotlin.ir.interpreter.transformer.transformConst
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.toIrConst
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.SpecialNames

private object IrCodeGenConstants {
    // Mitigates breaking change in IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA between 2.3.0 and 2.3.20
    val LOCAL_LAMBDA_ORIGIN = IrDeclarationOriginImpl("LOCAL_FUNCTION_FOR_LAMBDA")
}

context(context: CodeGenContext)
fun IrExpression.chainBuilder(
    parentDeclaration: IrFunction,
    functionToCall: IrSimpleFunction,
    bodyGen: context(LambdaBuilderContext) () -> Unit = {}
): IrCallImpl {
    return IrCallImpl.fromSymbolOwner(
        startOffset,
        endOffset,
        type,
        functionToCall.symbol
    ).apply {
        // receiver arg
        functionToCall.parameters.firstOrNull { parameter ->
            parameter.kind == IrParameterKind.ExtensionReceiver || parameter.kind == IrParameterKind.DispatchReceiver
        }?.let { receiverParameter ->
            // assign receiver to the current expression
            arguments[receiverParameter.indexInParameters] = this@chainBuilder
        }
        // lambda arg
        functionToCall.parameters.lastOrNull()?.let { lambdaParameter ->
            arguments[lambdaParameter.indexInParameters] = builderLambda(
                parentDeclaration,
                lambdaParameter,
                bodyGen
            )
        }
    }
}

/**
 * Assign the last parameter to the given lambda expression.
 *
 * This assumes that:
 * - The lambda return type is Unit
 */
context(pluginContext: CodeGenContext)
fun builderLambda(
    parentDeclaration: IrDeclaration,
    lambdaParameter: IrValueParameter,
    bodyGen: LambdaBuilderContext.() -> Unit = {}
): IrFunctionExpression {
    // Get the lambda type from the parameter
    val lambdaType = lambdaParameter.type as? IrSimpleType
        ?: error("Parameter type is not a simple type")
    val receiverType = lambdaType.arguments.firstOrNull()?.typeOrNull

    val function = pluginContext.irFactory.buildFun {
        name = SpecialNames.ANONYMOUS
        visibility = DescriptorVisibilities.LOCAL
        returnType = pluginContext.irBuiltIns.unitType
        origin = IrCodeGenConstants.LOCAL_LAMBDA_ORIGIN
    }.apply {
        parameters = buildList {
            receiverType?.let {
                add(buildReceiverParameter {
                    type = receiverType
                    kind = IrParameterKind.ExtensionReceiver
                })
            }
        }
    }

    val receiverParameter = receiverType?.let {
        function.buildReceiverParameter {
            type = receiverType
            kind = IrParameterKind.ExtensionReceiver
        }
    }

    function.parameters = buildList {
        receiverParameter?.let(::add)
    }

    val bodyStatements = buildList {
        bodyGen(LambdaBuilderContext(
            pluginContext,
            parentDeclaration,
            function,
            receiverParameter,
            receiverType,
            ::add
        ))
    }

    function.parent = parentDeclaration as? IrDeclarationParent ?: parentDeclaration.parent
    function.body = pluginContext.irFactory.createBlockBody(
        parentDeclaration.startOffset,
        parentDeclaration.endOffset,
        bodyStatements
    )

    return IrFunctionExpressionImpl(
        parentDeclaration.startOffset,
        parentDeclaration.endOffset,
        lambdaParameter.type,
        function,
        IrStatementOrigin.LAMBDA
    )
}

context(context: LambdaBuilderContext)
fun assignProperty(propertyName: String, value: Any?) {
    if (value == null) return
    val declaration = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
    val receiverType = context.receiverType ?: error("Operation.Builder not found")
    val propertyToSet = receiverType.classOrNull?.owner?.declarations
        ?.filterIsInstance<IrProperty>()
        ?.firstOrNull { it.name.asString() == propertyName }
        ?: error("Property $propertyName not found")
    val setter = propertyToSet.setter!!
    val setterValueArg = setter.parameters.first { it.kind == IrParameterKind.Regular }

    context.addStatement(declaration.irCall(setter).apply {
        dispatchReceiver = declaration.irGet(context.receiverParameter!!)
        arguments[setterValueArg.indexInParameters] = when(value) {
            is IrExpression -> value
            is String -> declaration.irString(value)
            is Boolean -> declaration.irBoolean(value)
            is Int -> declaration.irInt(value)
            else -> error("Unsupported value type: ${value::class}")
        }
    })
}

context(context: LambdaBuilderContext)
fun callFunctionNamed(functionName: String, vararg args: IrExpression): IrFunctionAccessExpression {
    val functionToCall = context.receiverType?.classOrNull?.owner?.declarations
        ?.filterIsInstance<IrFunction>()
        ?.firstOrNull { it.name.asString() == functionName }
    return callFunction(functionToCall!!, *args)
}

context(context: LambdaBuilderContext)
fun callFunction(functionToCall: IrFunction, vararg args: IrExpression): IrFunctionAccessExpression {
    val builder = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
    return builder.irCall(functionToCall).apply {
        var index = 0
        arguments[index++] = builder.irGet(context.receiverParameter!!)
        for (arg in args) {
            arguments[index++] = arg
        }
    }
}

context(context: LambdaBuilderContext)
fun callFunctionWithTypeArg(functionToCall: IrFunction, typeArg: IrType): IrFunctionAccessExpression {
    val builder = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
    return builder.irCall(functionToCall).apply {
        arguments[0] = builder.irGet(context.receiverParameter!!)
        typeArguments[0] = typeArg
    }
}

context(context: LambdaBuilderContext)
fun callFunctionWithScope(
    functionName: String,
    vararg args: IrExpression,
    body: context(LambdaBuilderContext) () -> Unit
): IrStatement {
    val builder = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
    val functionToCall = context.receiverType?.classOrNull?.owner?.declarations
        ?.filterIsInstance<IrFunction>()
        ?.firstOrNull { it.name.asString() == functionName }

    return builder.irCall(functionToCall!!).apply {
        var index = 0
        arguments[index++] = builder.irGet(context.receiverParameter!!)
        for (arg in args)
            arguments[index++] = arg
        arguments[index] = builderLambda(
            parentDeclaration = context.lambdaFunction,
            lambdaParameter = functionToCall.parameters.last(),
            bodyGen = body
        )
    }
}

context(context: CodeGenContext)
fun IrExpression.evaluateToConst(): IrConst? {
    val interpreter = IrInterpreter(IrInterpreterEnvironment(context.irBuiltIns))
    val result = interpreter.interpret(this, context.irFile)

    return result as? IrConst
}

context(context: CodeGenContext)
fun String.toConst(): IrConst =
    this.toIrConst(context.irBuiltIns.stringType)

context(context: CodeGenContext)
fun Int.toConst(): IrConst =
    this.toIrConst(context.irBuiltIns.intType)

context(context: CodeGenContext)
fun Boolean.toConst(): IrConst =
    this.toIrConst(context.irBuiltIns.booleanType)

context(context: CodeGenContext)
fun Double.toConst(): IrConst =
    this.toIrConst(context.irBuiltIns.doubleType)

fun IrExpression.inlineVariables(lookup: (IrValueSymbol) -> IrExpression?): IrExpression? =
    try {
        transform(object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                return when (val argumentValue = lookup(expression.symbol)?.deepCopyWithSymbols()) {
                    null -> throw MissingVariableException(expression.symbol)
                    else -> argumentValue
                }
            }
        }, null)
    } catch (e: MissingVariableException) {
        null
    }

class MissingVariableException(val symbol: IrValueSymbol) : Exception("Missing variable: $symbol")
