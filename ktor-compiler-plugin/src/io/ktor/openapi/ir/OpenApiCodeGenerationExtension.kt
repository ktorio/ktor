package io.ktor.openapi.ir

import io.ktor.openapi.Logger
import io.ktor.openapi.routing.RouteCallLookup
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class OpenApiCodeGenerationExtension(
    val logger: Logger,
    val routes: RouteCallLookup,
    val handlerInferenceEnabled: Boolean
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        moduleFragment.transform(
            transformer = CallDescribeTransformer(
                logger,
                pluginContext,
                routes,
                handlerInferenceEnabled,
            ),
            null
        )
    }
}