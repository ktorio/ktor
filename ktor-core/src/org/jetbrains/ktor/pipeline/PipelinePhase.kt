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
        object Last : PipelinePhaseRelation()
    }

    private val _phases = phases.mapTo(ArrayList<PhaseContent<TSubject>>(phases.size)) {
        PhaseContent(it, PipelinePhaseRelation.Last, mutableListOf())
    }

    private var interceptorsQuantity = 0

    val items: List<PipelinePhase> get() = _phases.map { it.phase }

    fun add(phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        _phases.add(PhaseContent(phase, PipelinePhaseRelation.Last, mutableListOf()))
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
        interceptorsQuantity++
    }

    fun merge(from: PipelinePhases<TSubject>) {
        val fromPhases = from._phases
        for (index in 0..fromPhases.lastIndex) {
            val fromContent = fromPhases[index]
            val phaseContent = _phases.firstOrNull { it.phase == fromContent.phase } ?: run {
                when (fromContent.relation) {
                    is PipelinePhaseRelation.Last -> add(fromContent.phase)
                    is PipelinePhaseRelation.Before -> insertBefore(fromContent.relation.relativeTo, fromContent.phase)
                    is PipelinePhaseRelation.After -> insertAfter(fromContent.relation.relativeTo, fromContent.phase)
                }
                _phases.first { it.phase == fromContent.phase }
            }
            phaseContent.interceptors.addAll(fromContent.interceptors)
            interceptorsQuantity += fromContent.interceptors.size
        }
    }
}

class InvalidPhaseException(message: String) : Throwable(message)
