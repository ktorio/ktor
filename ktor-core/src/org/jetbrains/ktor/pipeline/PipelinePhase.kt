package org.jetbrains.ktor.pipeline

class PipelinePhase(val name: String)

class PipelinePhases<TSubject : Any>(vararg phases: PipelinePhase) {
    private class PhaseContent<TSubject : Any>(val phase: PipelinePhase, val interceptors: MutableList<PipelineContext<TSubject>.(TSubject) -> Unit>) {
        override fun toString(): String = "Phase `${phase.name}`, ${interceptors.size} handlers"
    }

    private val _phases = phases.mapTo(mutableListOf<PhaseContent<TSubject>>()) { PhaseContent(it, mutableListOf()) }

    fun add(phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        _phases.add(PhaseContent(phase, mutableListOf()))
    }

    fun insertAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index + 1, PhaseContent(phase, mutableListOf()))
    }

    fun insertBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index, PhaseContent(phase, mutableListOf()))
    }

    fun interceptors(): List<PipelineContext<TSubject>.(TSubject) -> Unit> {
        return _phases.flatMap { it.interceptors }
    }

    fun intercept(phase: PipelinePhase, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        val phaseContent = _phases.firstOrNull() { it.phase == phase }
                ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        phaseContent.interceptors.add(block)
    }
}

class InvalidPhaseException(message: String) : Throwable(message)
