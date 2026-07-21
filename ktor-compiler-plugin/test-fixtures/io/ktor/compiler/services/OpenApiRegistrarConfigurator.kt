package io.ktor.compiler.services

import io.ktor.openapi.*
import io.ktor.openapi.fir.OpenApiAnalysisExtension
import io.ktor.openapi.ir.OpenApiCodeGenerationExtension
import io.ktor.openapi.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class OpenApiRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {

    @OptIn(ExperimentalSerializationApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        val routes: RouteCallLookup = mutableMapOf()
        val logger = Logger { message, cause, location ->
            println(buildString {
                append("LOG ")
                location?.let {
                    append("$it: ")
                }
                append(message)
            })
            cause?.let {
                cause.printStackTrace()
            }
        }
        FirExtensionRegistrarAdapter.registerExtension(
            OpenApiAnalysisExtension(
                logger,
                routes,
                onlyCommented = false
            )
        )
        IrGenerationExtension.registerExtension(
            OpenApiCodeGenerationExtension(
                logger,
                routes,
                handlerInferenceEnabled = true
            )
        )
    }
}
