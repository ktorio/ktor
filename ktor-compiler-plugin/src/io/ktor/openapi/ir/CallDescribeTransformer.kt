package io.ktor.openapi.ir

import io.ktor.openapi.*
import io.ktor.openapi.ir.generators.*
import io.ktor.openapi.ir.inference.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Finds all route selector calls and chains `describe` calls with relevant details that can be found
 * at compile time.
 */
class CallDescribeTransformer(
    val logger: Logger,
    val pluginContext: IrPluginContext,
    val routes: RouteCallLookup,
    val handlerInferenceEnabled: Boolean,
) : IrElementTransformerVoid(),
    CodeGenContext,
    Logger by logger,
    IrPluginContext by pluginContext {

    companion object {
        const val DESCRIBE_FUNCTION_NAME = "describe"
        const val DESCRIBE_PACKAGE = "io.ktor.server.routing.openapi"
        const val OPENAPI_PACKAGE = "io.ktor.openapi"
    }

    private val callHandlerInference = IrCallHandlerInference.of(
        CallRespondInference,
        CallReceiveInference,
        ParameterInference,
        RequestHeaderInference,
        AppendResponseHeaderInference,
        ResponseHeaderExtensionInference,
        ResourceRouteCallInference,
    )

    private val describeFunction: IrSimpleFunction by lazy {
        CallableId(
            packageName = FqName(DESCRIBE_PACKAGE),
            callableName = Name.identifier(DESCRIBE_FUNCTION_NAME)
        ).let { callableId ->
            pluginContext.referenceFunctions(callableId).single().owner
        }
    }

    // current file as defined by during traversal
    private var currentFile: IrFile? = null
    // required for building new declarations from function scopes
    private var functionStack = mutableListOf<IrFunction>()
    private val variableScopeStack = mutableListOf<MutableMap<IrValueSymbol, IrExpression>>()
    private val typeParametersScopeStack = mutableListOf<Map<IrTypeParameterSymbol, IrType>>()

    override val irFile: IrFile? get() = currentFile

    override fun copyAndResolve(expression: IrExpression): IrExpression? {
        val copied = expression.deepCopyWithSymbols()
        return copied.inlineVariables { symbol ->
            variableScopeStack.firstNotNullOfOrNull { it[symbol] }
        }
    }

    override fun inferConcreteType(type: IrType): IrType {
        val scope = typeParametersScopeStack.lastOrNull() ?: return type
        return type.substituteTypeParameters(scope)
    }

    override fun visitFile(declaration: IrFile): IrFile {
        try {
            currentFile = declaration
            return super.visitFile(declaration)
        } finally {
            currentFile = null
            functionStack.clear()
            variableScopeStack.clear()
            typeParametersScopeStack.clear()
        }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        try {
            functionStack.add(declaration)
            variableScopeStack.add(mutableMapOf())
            typeParametersScopeStack.add(mutableMapOf())
            return super.visitFunction(declaration)
        } finally {
            typeParametersScopeStack.removeLast()
            variableScopeStack.removeLast()
            functionStack.removeLast()
        }
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        // Record variable initializers as we encounter them (order-correct, scope-correct).
        declaration.initializer?.let { initializer ->
            val resolved = copyAndResolve(initializer) ?: return@let
            val scope = variableScopeStack.lastOrNull() ?: return@let
            scope[declaration.symbol] = resolved
        }
        return super.visitVariable(declaration)
    }

    /**
     * Check our cache for routing details for the call location, as populated from the FIR analysis of the KDoc.
     *
     * If this is a match, we can populate more details from the lambda argument by looking for any call
     * references, like `call.respond(...)`, for example.
     */
    override fun visitCall(expression: IrCall): IrExpression {
        // Maintain a type-parameter substitution scope for the subtree under this call.
        val pushed = buildTypeParameterScopeForCall(expression)
        try {
            // check for route info from the FIR analysis step
            val route: RouteCall = routes[expression.coordinates()]
                ?: return super.visitCall(expression)

            // get the nearest declaration up the stack
            val currentFunction = functionStack.lastOrNull()
                ?: return super.visitCall(expression)

            // if leaf, check lambda body, and stop
            // else, check handle {} calls, and continue
            val (call, routeFields) = route.fields.let { fields ->
                if (route.isLeaf) {
                    expression to fields.includeLambdaBody(expression)
                } else {
                    super.visitCall(expression) to fields.includeHandleBodies(expression)
                }
            }

            if (routeFields.isEmpty())
                return call

            logger.log(buildString {
                append("ROUTE ${route.locationString()}; fields:")
                append(routeFields.joinToString("\n  - ", prefix = "\n  - "))
            })
            return call.chainDescribeCall(
                parentDeclaration = currentFunction,
                routeFields = routeFields
            )
        } finally {
            if (pushed) typeParametersScopeStack.removeLast()
        }
    }

    private fun buildTypeParameterScopeForCall(call: IrCall): Boolean {
        val substitutions = call.typeArgsAsMap()

        if (substitutions.isEmpty()) return false

        // Merge with the existing "current" scope so inner bindings win.
        val merged = mutableMapOf<IrTypeParameterSymbol, IrType>()
        typeParametersScopeStack.lastOrNull()?.let(merged::putAll)
        merged.putAll(substitutions)

        typeParametersScopeStack.add(merged)
        return true
    }

    /**
     * If handler inference is enabled, scan the lambda body for route details.
     */
    private fun RouteFieldList.includeLambdaBody(expression: IrCall): RouteFieldList {
        if (!handlerInferenceEnabled) return this
        val analyzer = CallHandlerAnalyzer(
            callInference = callHandlerInference,
            context = this@CallDescribeTransformer,
            visited = emptySet(),
            variables = variableScopeStack.snapshotScope(),
            typeParameters = typeParametersScopeStack.snapshotScope(),
        )
        return merge(analyzer.analyze(expression))
    }

    /**
     * For NON-leaf route nodes, we only want to analyze `Route.handle { ... }` bodies contained inside
     * the lambda of this call (e.g., `method(HttpMethod.Get) { handle { ... } }`).
     */
    private fun RouteFieldList.includeHandleBodies(expression: IrCall): RouteFieldList {
        if (!handlerInferenceEnabled) return this

        val fields = mutableListOf<RouteField>()

        val finder = HandleCallFinder(
            callInference = callHandlerInference,
            context = this@CallDescribeTransformer,
            variables = variableScopeStack.snapshotScope(),
            typeParameters = typeParametersScopeStack.snapshotScope(),
            out = fields
        )

        // Only traverse the immediate lambda bodies of this non-leaf route call.
        for (arg in expression.arguments) {
            val fnExpr = (arg as? IrFunctionExpression) ?: continue
            val body = fnExpr.function.body ?: continue
            body.accept(finder, Unit)
        }

        return merge(fields)
    }

    private class HandleCallFinder(
        private val callInference: IrCallHandlerInference,
        private val context: CodeGenContext,
        private val variables: MutableMap<IrValueSymbol, IrExpression>,
        private val typeParameters: MutableMap<IrTypeParameterSymbol, IrType> = mutableMapOf(),
        private val out: MutableList<RouteField>,
    ) : IrVisitor<Unit, Unit>(), CodeGenContext by context {

        private fun IrCall.isHandleCall(): Boolean {
            val fn = symbol.owner
            val fqName = fn.kotlinFqName.asString()
            return fqName == "io.ktor.server.routing.Route.handle" ||
                fqName == "io.ktor.server.routing.handle"
        }

        override fun visitElement(element: org.jetbrains.kotlin.ir.IrElement, data: Unit) {
            element.acceptChildren(this, data)
        }

        // Don’t descend into arbitrary lambdas (e.g., nested `get {}`), that’s where bleeding happens.
        override fun visitFunctionExpression(expression: IrFunctionExpression, data: Unit) {
            // Intentionally no-op
        }

        override fun visitCall(expression: IrCall, data: Unit) {
            if (expression.isHandleCall()) {
                // Analyze ONLY the handle handler bodies using the normal analyzer.
                for (arg in expression.arguments) {
                    val fnExpr = (arg as? IrFunctionExpression) ?: continue
                    val lambdaBody = fnExpr.function.body ?: continue

                    val analyzer = CallHandlerAnalyzer(
                        callInference = callInference,
                        context = context,
                        visited = emptySet(),
                        variables = variables.toMutableMap(),
                        typeParameters = typeParameters,
                    )
                    out += analyzer.analyze(lambdaBody)
                }
                return
            }

            // Keep walking statements/expressions so we can *find* handle calls,
            // but avoid descending into other lambdas (handled above).
            expression.acceptChildren(this, data)
        }
    }

    private fun <K, V> List<Map<K, V>>.snapshotScope(): MutableMap<K, V> {
        val snapshot = mutableMapOf<K, V>()
        for (scope in this) {
            snapshot.putAll(scope)
        }
        return snapshot
    }

    private fun IrExpression.coordinates() =
        SourceKey(currentFile?.path, startOffset, endOffset)

    context(context: CodeGenContext)
    private fun IrExpression.chainDescribeCall(
        parentDeclaration: IrFunction,
        routeFields: RouteFieldList,
    ): IrExpression {
        if (RouteField.Ignore in routeFields || routeFields.isEmpty())
            return this
        val parameterFields = mutableListOf<RouteField.Parameter>()
        val responseFields = mutableListOf<RouteField>()
        val describeExpressionBuilder = GeneralDescribeExpressionGenerator(
            delegateParameterField = parameterFields::add,
            delegateResponseField = responseFields::add,
        )

        return chainBuilder(
            parentDeclaration = parentDeclaration,
            functionToCall = describeFunction,
        ) {
            describeExpressionBuilder.generate(routeFields)
            ParametersGenerator.generate(parameterFields)
            ResponsesGenerator.generate(responseFields)
        }
    }
}
