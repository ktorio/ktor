package io.ktor.tests.server.application

import io.ktor.config.ApplicationConfigValue

class MockConfigValue(private val stringValue: String, private val listValue: List<String>) : ApplicationConfigValue {
    override fun getString() = stringValue

    override fun getList() = listValue
}