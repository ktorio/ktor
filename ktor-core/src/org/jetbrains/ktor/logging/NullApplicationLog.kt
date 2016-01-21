package org.jetbrains.ktor.logging

/**
 * Implements [ApplicationLog] by doing nothing
 */
public class NullApplicationLog(override val name: String = "Application") : ApplicationLog {
    override fun fork(name: String): ApplicationLog = NullApplicationLog("${this.name}.$name")
}