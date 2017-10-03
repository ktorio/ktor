package org.jetbrains.ktor.pipeline

class PipelinePhase(val name: String) {
    override fun toString() = "Phase('$name')"
}

class InvalidPhaseException(message: String) : Throwable(message)
