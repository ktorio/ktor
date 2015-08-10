package org.jetbrains.ktor.jobs

public interface JobService {
    fun async(name: String, body: () -> Unit)
    fun schedule(name: String, seconds: Long, body: () -> Unit)
}