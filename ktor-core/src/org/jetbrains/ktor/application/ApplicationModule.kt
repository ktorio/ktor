package org.jetbrains.ktor.application

import org.jetbrains.ktor.util.*

abstract class ApplicationModule : ApplicationFeature<Application, Unit, ApplicationModule> {
    override val key = AttributeKey<ApplicationModule>(javaClass.simpleName)
    override fun install(pipeline: Application, configure: Unit.() -> Unit): ApplicationModule {
        Unit.configure()
        install(pipeline)
        return this
    }

    abstract fun install(application: Application)
}