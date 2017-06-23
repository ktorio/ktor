package org.jetbrains.ktor.pipeline

import java.util.*

class PipelinePhase(val name: String) {
    override fun toString() = "Phase('$name')"
}

class PipelinePhases<TSubject : Any>(vararg phases: PipelinePhase) {

    private class PhaseContent<TSubject : Any>(val phase: PipelinePhase,
                                               val relation: PipelinePhaseRelation,
                                               val interceptors: MutableList<PipelineInterceptor<TSubject>>) {
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
        interceptors = null
    }

    fun insertAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index + 1, PhaseContent(phase, PipelinePhaseRelation.After(reference), mutableListOf()))
        interceptors = null
    }

    fun insertBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (_phases.any { it.phase == phase }) return
        val index = _phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        _phases.add(index, PhaseContent(phase, PipelinePhaseRelation.Before(reference), mutableListOf()))
        interceptors = null
    }

    @Volatile
    private var interceptors: ArrayList<PipelineInterceptor<TSubject>>? = null

    fun interceptors(): ArrayList<PipelineInterceptor<TSubject>> {
        return interceptors ?: cacheInterceptors()
    }

    private fun cacheInterceptors(): ArrayList<PipelineInterceptor<TSubject>> {
        val destination = ArrayList<PipelineInterceptor<TSubject>>(interceptorsQuantity)
        for (phaseIndex in 0.._phases.lastIndex) {
            val elements = _phases[phaseIndex].interceptors
            for (elementIndex in 0..elements.lastIndex) {
                destination.add(elements[elementIndex])
            }
        }
        interceptors = destination
        return destination
    }

    fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject>) {
        val phaseContent = _phases.firstOrNull { it.phase == phase }
                ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        phaseContent.interceptors.add(block)
        interceptorsQuantity++
        interceptors = null
    }

    fun merge(from: PipelinePhases<TSubject>) {
        if (from._phases.isEmpty())
            return
        if (_phases.isEmpty()) {
            val fromPhases = from._phases
            @Suppress("LoopToCallChain")
            for (index in 0..fromPhases.lastIndex) {
                val fromContent = fromPhases[index]
                _phases.add(PhaseContent(fromContent.phase, fromContent.relation, fromContent.interceptors.toMutableList()))
            }
            interceptors = null
            return
        }

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
        interceptors = null
    }
}

class InvalidPhaseException(message: String) : Throwable(message)
