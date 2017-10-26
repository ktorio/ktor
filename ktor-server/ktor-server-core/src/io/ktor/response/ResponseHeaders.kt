package io.ktor.response

import io.ktor.util.*

abstract class ResponseHeaders {
    operator fun contains(name: String): Boolean = getEngineHeaderValues(name).isNotEmpty()
    operator fun get(name: String): String? = getEngineHeaderValues(name).firstOrNull()
    fun values(name: String): List<String> = getEngineHeaderValues(name)
    fun allValues(): ValuesMap = ValuesMap.build(true) {
        getEngineHeaderNames().forEach {
            appendAll(it, getEngineHeaderValues(it))
        }
    }

    fun append(name: String, value: String) {
        engineAppendHeader(name, value)
    }

    protected abstract fun engineAppendHeader(name: String, value: String)
    protected abstract fun getEngineHeaderNames(): List<String>
    protected abstract fun getEngineHeaderValues(name: String): List<String>
}