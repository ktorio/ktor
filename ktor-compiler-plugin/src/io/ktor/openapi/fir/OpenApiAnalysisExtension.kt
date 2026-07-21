package io.ktor.openapi.fir

import io.ktor.openapi.Logger
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RoutingFunctionConstants
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTE_INTERFACE
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_CONTEXT
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.isSuspendOrKSuspendFunctionType
import org.jetbrains.kotlin.fir.types.receiverType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class OpenApiAnalysisExtension(
    val logger: Logger,
    val routes: RouteCallLookup,
    val onlyCommented: Boolean,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::registerChecker
    }

    fun registerChecker(session: FirSession): FirAdditionalCheckersExtension {
        return OpenApiFirAdditionalChecksExtension(session)
    }

    inner class OpenApiFirAdditionalChecksExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers
            get() = object : ExpressionCheckers() {
                override val functionCallCheckers = setOf(
                    OpenApiRouteCallReader(logger, onlyCommented) { route ->
                        routes[route.coordinates()] = route
                    }
                )
            }
    }

}

class OpenApiRouteCallReader(
    private val logger: Logger,
    private val onlyCommented: Boolean,
    private val onRoute: (RouteCall) -> Unit
) : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (!isRouteFunction(expression)) return
        val range = expression.source?.range ?: return
        val fileText = context.containingFileSymbol?.source?.text ?: return
        val packageFqName = context.containingFileSymbol?.packageFqName ?: return
        try {
            val fields = try {
                parsePrecedingComment(logger, packageFqName, fileText, range.first)
            } catch (t: Throwable) {
                logger.log("Failure to parse KDoc", t)
                emptyList()
            }
            // If onlyCommented is enabled and no preceding comment is found, skip early
            if (onlyCommented && fields.isEmpty()) return

            val isLeaf = expression.hasHandlerLambda()
            val routeCall = RouteCall(
                fir = expression,
                fields = fields,
                isLeaf = isLeaf,
            ) ?: return

            onRoute(routeCall)
        } catch (e: Exception) {
            logger.log("Exception while reading $expression: ${e.message}", e)
        }
    }

    private fun isRouteFunction(call: FirFunctionCall): Boolean =
        call.calleeReference.symbol?.packageFqName()?.toString().orEmpty().startsWith("io.ktor")
                && call.resolvedType.classId?.asFqNameString() == ROUTE_INTERFACE

    context(context: CheckerContext)
    private fun FirFunctionCall.hasHandlerLambda(): Boolean {
        return arguments.any { arg ->
            arg.resolvedType.isSuspendOrKSuspendFunctionType(context.session) &&
                arg.resolvedType.isExtensionFunctionType &&
                arg.resolvedType.receiverType(context.session)?.classId?.asSingleFqName()?.asString() == ROUTING_CONTEXT
        }
    }
}
