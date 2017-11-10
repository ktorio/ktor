package io.ktor.http.cio

import io.ktor.http.cio.internals.*

class ConnectionType(val value: String, val hopByHopHeaders: List<String>) {
    companion object {
        val Close = ConnectionType("close", emptyList())
        val KeepAlive = ConnectionType("keep-alive", emptyList())
        val Upgrade = ConnectionType("upgrade", emptyList())

        private val knownTypes = AsciiCharTree.build(
                listOf(Close, KeepAlive, Upgrade), { it.value.length }, { t, idx -> t.value[idx] })

        fun parse(connection: CharSequence?): ConnectionType? {
            if (connection == null) return null
            val known = knownTypes.search(connection, stopPredicate = { _, _ -> false })
            if (known.size == 1) return known[0]
            return parseSlow(connection)
        }

        private fun parseSlow(connection: CharSequence): ConnectionType {
            var idx = 0
            var start = 0
            val length = connection.length
            var connectionType: ConnectionType? = null
            var hopHeadersList: ArrayList<String>? = null

            while (idx < length) {
                do {
                    val ch = connection[idx]
                    if (ch != ' ' && ch != ',') {
                        start = idx
                        break
                    }
                    idx++
                } while (idx < length)

                while (idx < length) {
                    val ch = connection[idx]
                    if (ch == ' ' || ch == ',') break
                    idx++
                }

                val detected = knownTypes.search(connection, start, idx, stopPredicate = { _, _ -> false }).singleOrNull()
                when {
                    detected == null -> {
                        if (hopHeadersList == null) {
                            hopHeadersList = ArrayList()
                        }

                        hopHeadersList.add(connection.substring(start, idx))
                    }
                    connectionType == null -> connectionType = detected
                    connectionType !== detected -> throw IllegalArgumentException("Different connection types were specified: $connectionType and $detected")
                }
            }

            if (connectionType == null) connectionType = KeepAlive

            return if (hopHeadersList == null) connectionType
            else ConnectionType(connectionType.value, hopHeadersList)
        }
    }

    override fun toString(): String {
        return when {
            hopByHopHeaders.isEmpty() -> return value
            else -> hopByHopHeaders.joinToString(", ", "$value; ")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectionType

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
