package io.ktor.util.pipeline

/**
 * Represents a phase in a pipeline
 *
 * @param name a name for this phase
 */
class PipelinePhase(val name: String) {
    override fun toString() = "Phase('$name')"
}

/**
 * An exception about misconfigured phases in a pipeline
 */
class InvalidPhaseException(message: String) : Throwable(message)
