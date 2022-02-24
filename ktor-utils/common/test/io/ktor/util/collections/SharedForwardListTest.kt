/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import io.ktor.util.collections.internal.*
import kotlin.test.*

class SharedForwardListTest {

    @Test
    fun testHeadTailAddRemove() {
        val list = SharedForwardList<Int>()
        val head = list.head
        val tail = list.tail

        assertSame(head, tail)

        val node = list.appendFirst(1)
        assertSame(head, list.head)

        assertNotSame(tail, list.tail)

        node.remove()
        assertSame(head, list.head)
        assertSame(head, list.tail)
    }

    @Test
    fun testAppend() {
        val list = SharedForwardList<Int>()
        val first = list.appendFirst(2)
        assertEquals(list.first(), list.last())

        first.remove()

        val second = list.appendLast(3)
        assertEquals(list.first(), list.last())

        second.remove()

        list.appendFirst(1)
        list.appendLast(4)
        list.appendFirst(0)

        val expected = listOf(0, 1, 4)
        list.forEachIndexed { index, item ->
            assertEquals(expected[index], item)
        }
    }
}
