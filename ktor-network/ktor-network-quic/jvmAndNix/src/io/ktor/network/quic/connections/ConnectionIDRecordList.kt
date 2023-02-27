/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

/**
 * Manages list of available CIDs, insures capacity restrictions, tracks removed CIDs
 */
internal class ConnectionIDRecordList(private var capacity: Int) {
    private val pool = mutableListOf<ConnectionIDRecord>()
    private val removed = mutableSetOf<Long>()

    /**
     * All CIDs with sequence number lower than [threshold] has been retired
     */
    var threshold: Long = -1
        private set

    /**
     * Adds [ConnectionIDRecord] to pool, insuring capacity
     *
     * @return false, if capacity constraint violated, true otherwise
     */
    fun add(record: ConnectionIDRecord): Boolean {
        if (pool.size < capacity) {
            pool.add(record)
            return true
        }
        return false
    }

    fun isRemoved(sequenceNumber: Long): Boolean {
        return removed.contains(sequenceNumber)
    }

    operator fun get(sequenceNumber: Long): ConnectionIDRecord? {
        return pool.find { it.sequenceNumber == sequenceNumber }
    }

    operator fun get(connectionID: ConnectionID): ConnectionIDRecord? {
        return pool.find { it.connectionID eq connectionID }
    }

    fun remove(sequenceNumber: Long) {
        if (pool.removeIf { it.sequenceNumber == sequenceNumber }) {
            removed.add(sequenceNumber)
        }
    }

    fun removePriorToAndSetThreshold(newThreshold: Long): List<Long> {
        val removed = mutableListOf<Long>()
        if (threshold < newThreshold) {
            pool.removeIf { cid ->
                (cid.sequenceNumber < newThreshold).also { lower ->
                    if (lower) {
                        removed.add(cid.sequenceNumber)
                    }
                }
            }
            threshold = newThreshold
        }
        return removed
    }

    fun increaseCapacity(newCapacity: Int) {
        if (newCapacity > capacity) {
            capacity = newCapacity
        }
    }

    private var nextCounter = 0L

    /**
     * Used to get available CID for using in package header
     */
    fun nextConnectionID(): ConnectionID {
        return pool[(nextCounter % pool.size).toInt()].connectionID.also { nextCounter++ }
    }
}
