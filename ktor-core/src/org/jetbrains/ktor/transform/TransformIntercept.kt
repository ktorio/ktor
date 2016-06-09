package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*

object TransformationSupport : ApplicationFeature<TransformTable> {
    override val name = "TransformationSupport"
    override val key = AttributeKey<TransformTable>(name)

    override fun install(application: Application, configure: TransformTable.() -> Unit): TransformTable {
        val table = TransformTable()

        configure(table)

        application.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.respond.intercept(RespondPipeline.Transform) { state ->
                state.obj = call.transform.transform(state.obj)
            }
        }

        return table
    }
}
