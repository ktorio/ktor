/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.indexOf

/**
 * Utility class for scanning ByteReadChannel for specific byte sequences using KMP algorithm.
 *
 * @property channel The ByteReadChannel to scan
 * @property matchString The sequence of bytes to look for
 * @property writeChannel The channel to write the read bytes to
 * @property limit The maximum number of bytes to read before throwing an exception
 */
@OptIn(InternalAPI::class)
internal class ByteChannelScanner(
    private val channel: ByteReadChannel,
    private val matchString: ByteString,
    private val writeChannel: ByteWriteChannel,
    private val limit: Long = Long.MAX_VALUE,
) {
    init {
        require(matchString.size > 0) {
            "Empty match string not permitted for scanning"
        }
    }

    private val input = channel.readBuffer
    private val partialMatchTable = buildPartialMatchTable()
    private val partialMatchBuffer = Buffer()
    private var bytesRead = 0L
    private var matchIndex = 0

    /**
     * Searches for the next complete match of a predefined sequence in the input channel using the KMP algorithm.
     * If a match is found, returns the number of bytes read up to and including the match.
     * If no match is found and `ignoreMissing` is false, throws an `IOException`.
     *
     * @param ignoreMissing A flag indicating whether to throw an exception if no match is found
     *                      and the end of input is reached. Defaults to `false`.
     * @return The total number of bytes read up to the match or end of input
     * @throws IOException if the match is not found and `ignoreMissing` is `false`.
     */
    internal suspend fun findNext(ignoreMissing: Boolean = false): Long {
        bytesRead = 0L

        while (!input.exhausted() || channel.awaitContent()) {
            // Quick scan for first byte
            advanceToNextPotentialMatch()

            // Return if a complete match is found
            if (checkFullMatch()) {
                return bytesRead
            }
        }

        // Nothing was found, throw unless ignoreMissing flag is used
        if (!ignoreMissing) {
            throw IOException("Expected \"${matchString.toSingleLineString()}\" but encountered end of input")
        }

        // Write any remaining partial matches and return
        bytesRead += partialMatchBuffer.transferTo(writeChannel.writeBuffer)
        writeChannel.flush()
        return bytesRead
    }

    /**
     * Builds the partial match table (also known as "longest prefix suffix" table)
     * for the KMP algorithm.
     */
    private fun buildPartialMatchTable(): IntArray {
        val table = IntArray(matchString.size)
        var j = 0

        for (i in 1 until matchString.size) {
            while (j > 0 && matchString[i] != matchString[j]) {
                j = table[j - 1]
            }
            if (matchString[i] == matchString[j]) {
                j++
            }
            table[i] = j
        }

        return table
    }

    /**
     * Scans forward in the buffer until finding the first byte of potential match
     */
    private suspend fun advanceToNextPotentialMatch() {
        while (!input.exhausted() || channel.awaitContent()) {
            val nextMatch = input.indexOf(matchString[0])
            when {
                nextMatch == -1L -> {
                    checkBounds((input as Buffer).size)
                    bytesRead += input.transferTo(writeChannel.writeBuffer)
                    writeChannel.flushIfNeeded()
                }
                else -> {
                    checkBounds(nextMatch)
                    bytesRead += input.readAtMostTo(writeChannel.writeBuffer as Buffer, nextMatch)
                    writeChannel.flushIfNeeded()
                    return
                }
            }
        }
    }

    /**
     * Performs sequential byte-by-byte comparison to check for a complete match using KMP algorithm
     *
     * @return true if a complete match was found, false otherwise
     */
    @OptIn(InternalAPI::class)
    private suspend fun checkFullMatch(): Boolean {
        while (!input.exhausted() || channel.awaitContent()) {
            val byte = input.readByte()

            if (matchIndex > 0 && byte != matchString[matchIndex]) {
                // Update match index from our table
                val oldMatchIndex = matchIndex
                while (matchIndex > 0 && byte != matchString[matchIndex]) {
                    matchIndex = partialMatchTable[matchIndex - 1]
                }
                // Write the discarded partial match
                val retained = (oldMatchIndex - matchIndex).toLong()
                checkBounds(retained)
                bytesRead += partialMatchBuffer.readAtMostTo(
                    writeChannel.writeBuffer as Buffer,
                    retained
                )
                // No longer matching, scan for next match
                if (matchIndex == 0 && byte != matchString[matchIndex]) {
                    writeChannel.writeByte(byte)
                    bytesRead++
                    return false
                }
            }

            // Return if complete match, else add to partial buffer
            if (++matchIndex == matchString.size) {
                return true
            }
            partialMatchBuffer.writeByte(byte)
        }
        return false
    }

    /**
     * Checks if adding the specified number of bytes would exceed the limit
     */
    private fun checkBounds(extra: Long) {
        if (bytesRead + extra > limit) {
            throw IOException(
                "Limit of $limit bytes exceeded " +
                    "while searching for \"${matchString.toSingleLineString()}\""
            )
        }
    }

    /**
     * Used in formatting error messages.
     */
    private fun ByteString.toSingleLineString(): String =
        decodeToString().replace("\n", "\\n")
}
