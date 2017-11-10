package io.ktor.http.cio.internals

internal class AsciiCharTree<T : Any>(val root: Node<T>) {
    class Node<T>(val ch: Char, val exact: List<T>, val children: List<Node<T>>) {
        val array = Array(0x100) { chi -> children.singleOrNull { it.ch.toInt() == chi } }
    }

    fun search(s: CharSequence, fromIdx: Int = 0, end: Int = s.length, lowerCase: Boolean = false, stopPredicate: (Char, Int) -> Boolean): List<T> {
        if (s.isEmpty()) throw IllegalArgumentException("Couldn't search in char tree for empty string")
        var node = root

        for (idx in fromIdx until end) {
            val ch = s[idx]
            val chi = ch.toInt()

            if (stopPredicate(ch, chi)) break

            val nextNode = node.array[chi] ?: (if (lowerCase) node.array[ch.toLowerCase().toInt()] else null) ?: return emptyList()
            node = nextNode
        }

        return node.exact
    }

    companion object {
        fun <T : CharSequence> build(from: List<T>): AsciiCharTree<T> {
            return build(from, { it.length }, { s, idx -> s[idx] })
        }

        fun <T : Any> build(from: List<T>, length: (T) -> Int, charAt: (T, Int) -> Char): AsciiCharTree<T> {
            val maxLen = from.maxBy(length)?.let(length) ?: throw NoSuchElementException("Unable to build char tree from an empty list")
            if (from.any { length(it) == 0 }) throw IllegalArgumentException("There should be no empty entries")

            val root = ArrayList<Node<T>>()
            build(root, from, maxLen, 0, length, charAt)
            root.trimToSize()
            return AsciiCharTree(Node('\u0000', emptyList(), root))
        }

        private fun <T : Any> build(resultList: MutableList<Node<T>>, from: List<T>, maxLength: Int, idx: Int, length: (T) -> Int, charAt: (T, Int) -> Char) {
            from.groupBy { charAt(it, idx) }.forEach { ch, list ->
                val nextIdx = idx + 1
                val children = ArrayList<Node<T>>()
                build(children, list.filter { length(it) > nextIdx }, maxLength, nextIdx, length, charAt)
                children.trimToSize()
                resultList.add(Node(ch, list.filter { length(it) == nextIdx }, children))
            }
        }
    }
}