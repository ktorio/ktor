package org.jetbrains.ktor.pipeline

import java.util.*

class PipelinePhase(val name: String) {
    override fun toString() = "Phase('$name')"
}

class PipelinePhases<TSubject : Any>(vararg phases: PipelinePhase) {
    private class PhaseContent<TSubject : Any>(val phase: PipelinePhase,
                                               val relation: PipelinePhaseRelation,
                                               val interceptors: MutableList<PipelineContext<TSubject>.(TSubject) -> Unit>) {
        override fun toString(): String = "Phase `${phase.name}`, ${interceptors.size} handlers"
    }

    sealed class PipelinePhaseRelation() {
        class After(val relativeTo: PipelinePhase) : PipelinePhaseRelation()
        class Before(val relativeTo: PipelinePhase) : PipelinePhaseRelation()
        class Last() : PipelinePhaseRelation()
    }

    private val _phases = phases.mapTo(mutableListOf<PhaseContent<TSubject>>()) { PhaseContent(it, PipelinePhaseRelation.Last(), mutableListOf()) }
    private var interceptorsQuantity = 0

    val items : List<PipelinePhase> get() = _phases.map { it.phase }

    fun add(phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        _phases.add(PhaseContent(phase, PipelinePhaseRelation.Last(), mutableListOf()))
    }

    fun insertAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index + 1, PhaseContent(phase, PipelinePhaseRelation.After(reference), mutableListOf()))
    }

    fun insertBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index, PhaseContent(phase, PipelinePhaseRelation.Before(reference), mutableListOf()))
    }

    fun interceptors(): List<PipelineContext<TSubject>.(TSubject) -> Unit> {
        return _phases.flatMapTo(ArrayList(interceptorsQuantity)) { it.interceptors }
    }

    fun intercept(phase: PipelinePhase, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        val phaseContent = _phases.firstOrNull { it.phase == phase }
                ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        phaseContent.interceptors.add(block)
        interceptorsQuantity ++
    }

    fun merge(from: PipelinePhases<TSubject>) {
        from._phases.forEach { content ->
            val phaseContent = _phases.firstOrNull { it.phase == content.phase } ?: run {
                when (content.relation) {
                    is PipelinePhaseRelation.Last -> add(content.phase)
                    is PipelinePhaseRelation.Before -> insertBefore(content.relation.relativeTo, content.phase)
                    is PipelinePhaseRelation.After -> insertAfter(content.relation.relativeTo, content.phase)
                }
                _phases.first { it.phase == content.phase }
            }
            phaseContent.interceptors.addAll(content.interceptors)
            interceptorsQuantity += content.interceptors.size
        }
    }
}

class InvalidPhaseException(message: String) : Throwable(message)
