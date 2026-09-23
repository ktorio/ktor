/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

private const val PUBLIC_SUFFIX_RULE_CHUNK_SIZE = 12_000

private data class PublicSuffixListRules(
    val exact: List<String>,
    val wildcards: List<String>,
    val exceptions: List<String>
)

/** Downloads the Public Suffix List and generates Kotlin rule data. */
@DisableCachingByDefault(because = "The task explicitly fetches the latest upstream list")
abstract class UpdatePublicSuffixList : DefaultTask() {
    @get:Input
    abstract val sourceUrl: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun update() {
        val source = sourceUrl.get()
        val request = HttpRequest.newBuilder(URI(source))
            .header("User-Agent", "Ktor Public Suffix List updater")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

        check(response.statusCode() in 200..299) {
            "Failed to download the Public Suffix List from $source: HTTP ${response.statusCode()}"
        }

        val bytes = response.body()
        val content = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        val rules = parsePublicSuffixList(content)
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
        val generated = renderPublicSuffixList(source, checksum, rules)
        val output = destination.get().asFile.toPath()

        Files.createDirectories(output.parent)
        if (Files.exists(output) && Files.readString(output) == generated) return

        val temporary = Files.createTempFile(output.parent, ".public-suffix-list-", ".tmp")
        try {
            Files.writeString(temporary, generated)
            try {
                Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private fun parsePublicSuffixList(content: String): PublicSuffixListRules {
    require(content.isNotBlank()) { "The Public Suffix List is empty" }

    val exact = mutableSetOf<String>()
    val wildcards = mutableSetOf<String>()
    val exceptions = mutableSetOf<String>()

    content.lineSequence().forEachIndexed { index, sourceLine ->
        val line = sourceLine.trim().removePrefix("\uFEFF")
        if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed

        val (target, rule) = when {
            line.startsWith("!") -> exceptions to line.drop(1)
            line.startsWith("*.") -> wildcards to line.drop(2)
            else -> exact to line
        }

        require(
            rule.isNotEmpty() &&
                '*' !in rule &&
                '!' !in rule &&
                rule.none { it.isWhitespace() || it.isISOControl() } &&
                rule.split('.').none { it.isEmpty() } &&
                rule.none { it == '"' || it == '$' || it == '\\' }
        ) {
            "Malformed Public Suffix List rule at line ${index + 1}: $line"
        }
        target += rule
    }

    require(exact.isNotEmpty() || wildcards.isNotEmpty() || exceptions.isNotEmpty()) {
        "The Public Suffix List contains no rules"
    }

    return PublicSuffixListRules(
        exact = exact.sorted(),
        wildcards = wildcards.sorted(),
        exceptions = exceptions.sorted()
    )
}

private fun renderPublicSuffixList(
    sourceUrl: String,
    checksum: String,
    rules: PublicSuffixListRules
): String = buildString {
    appendLine("/*")
    appendLine(
        " * Copyright 2014-2026 JetBrains s.r.o and contributors. " +
            "Use of this source code is governed by the Apache 2.0 license."
    )
    appendLine(" *")
    appendLine(" * Public suffix data is derived from the Mozilla Public Suffix List.")
    appendLine(" * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.")
    appendLine(
        " * If a copy of the MPL was not distributed with this file, " +
            "You can obtain one at https://mozilla.org/MPL/2.0/."
    )
    appendLine(" */")
    appendLine()
    appendLine("// Generated by :ktor-client-core:updatePublicSuffixList. Do not edit manually.")
    appendLine("package io.ktor.client.plugins.cookies")
    appendLine()
    appendLine("internal const val PUBLIC_SUFFIX_LIST_SOURCE: String =")
    appendLine("    \"$sourceUrl\"")
    appendLine("internal const val PUBLIC_SUFFIX_LIST_SHA256: String =")
    appendLine("    \"$checksum\"")
    appendLine()
    appendRuleChunks("PUBLIC_SUFFIX_EXACT_RULE_CHUNKS", rules.exact)
    appendRuleChunks("PUBLIC_SUFFIX_WILDCARD_RULE_CHUNKS", rules.wildcards)
    appendRuleChunks("PUBLIC_SUFFIX_EXCEPTION_RULE_CHUNKS", rules.exceptions)
}.trimEnd() + "\n"

private fun StringBuilder.appendRuleChunks(name: String, rules: List<String>) {
    val chunks = rules.chunkByUtf8Size(PUBLIC_SUFFIX_RULE_CHUNK_SIZE)
    appendLine("internal val $name: Array<String> = arrayOf(")
    chunks.indices.forEach { appendLine("    ${name}_$it,") }
    appendLine(")")
    appendLine()

    chunks.forEachIndexed { index, chunk ->
        append("private const val ${name}_$index: String = \"\"\"")
        append(chunk)
        appendLine("\"\"\"")
        appendLine()
    }
}

private fun List<String>.chunkByUtf8Size(maxBytes: Int): List<String> {
    if (isEmpty()) return emptyList()

    val result = mutableListOf<String>()
    val chunk = StringBuilder()
    var chunkBytes = 0

    for (rule in this) {
        val separatorBytes = if (chunk.isEmpty()) 0 else 1
        val ruleBytes = rule.toByteArray(StandardCharsets.UTF_8).size
        require(ruleBytes <= maxBytes) { "Public Suffix List rule is too long: $rule" }

        if (chunkBytes + separatorBytes + ruleBytes > maxBytes) {
            result += chunk.toString()
            chunk.clear()
            chunkBytes = 0
        }

        if (chunk.isNotEmpty()) {
            chunk.append('\n')
            chunkBytes++
        }
        chunk.append(rule)
        chunkBytes += ruleBytes
    }

    result += chunk.toString()
    return result
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}
