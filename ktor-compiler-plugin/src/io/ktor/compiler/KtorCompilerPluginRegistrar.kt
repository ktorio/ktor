package io.ktor.compiler

import io.ktor.compiler.KtorCommandLineProcessor.Companion.PLUGIN_ID
import io.ktor.openapi.Logger
import io.ktor.openapi.fir.OpenApiAnalysisExtension
import io.ktor.openapi.ir.OpenApiCodeGenerationExtension
import io.ktor.openapi.OpenApiProcessorConfig
import io.ktor.openapi.routing.RouteCallLookup
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@OptIn(ExperimentalSerializationApi::class)
class KtorCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val pluginId: String get() = PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val openApiConfig = readConfiguration(configuration)
        if (!openApiConfig.enabled) {
            return
        }

        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        val logger = Logger.wrap(messageCollector, openApiConfig.debug, openApiConfig.logDir)
        val routes: RouteCallLookup = mutableMapOf()
        // Analysis FIR plugin reads the comments and caches them to the routes graph
        FirExtensionRegistrarAdapter.registerExtension(
            OpenApiAnalysisExtension(
                logger,
                routes,
                openApiConfig.onlyCommented,
            )
        )
        // Code generation plugin introduces calls to the routing annotation API
        IrGenerationExtension.registerExtension(OpenApiCodeGenerationExtension(logger, routes, openApiConfig.codeInference))
    }

    private fun readConfiguration(
        cc: CompilerConfiguration,
    ): OpenApiProcessorConfig =
        with(KtorCommandLineProcessor) {
            OpenApiProcessorConfig(
                enabled = cc[OPENAPI_ENABLED_KEY]?.toBooleanStrictOrNull() ?: false,
                debug = cc[OPENAPI_DEBUG_KEY]?.toBooleanStrictOrNull() ?: false,
                codeInference = cc[OPENAPI_CODE_INFERENCE_KEY]?.toBooleanStrictOrNull() ?: false,
                onlyCommented = cc[OPENAPI_ONLY_COMMENTED_KEY]?.toBooleanStrictOrNull() ?: false,
                logDir = cc[OPENAPI_LOG_DIR]
            )
        }
}