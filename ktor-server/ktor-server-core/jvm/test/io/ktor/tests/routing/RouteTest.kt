/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.routing

import io.ktor.routing.*
import kotlin.test.*

class RouteTest {

    @Test
    fun testToStringSimple() {
        val root = Route(parent = null, selector = PathSegmentConstantRouteSelector("root"))
        val simpleChild = Route(parent = root, selector = PathSegmentConstantRouteSelector("simpleChild"))
        val simpleGrandChild =
            Route(parent = simpleChild, selector = PathSegmentConstantRouteSelector("simpleGrandChild"))

        val slashChild = Route(parent = root, selector = TrailingSlashRouteSelector)
        val slashGrandChild = Route(parent = slashChild, selector = TrailingSlashRouteSelector)
        val simpleChildInSlash = Route(parent = slashGrandChild, PathSegmentConstantRouteSelector("simpleChildInSlash"))
        val slashChildInSimpleChild = Route(parent = simpleChildInSlash, TrailingSlashRouteSelector)

        assertEquals("/root", root.toString())
        assertEquals("/root/simpleChild", simpleChild.toString())
        assertEquals("/root/simpleChild/simpleGrandChild", simpleGrandChild.toString())
        assertEquals("/root/", slashChild.toString())
        assertEquals("/root/", slashGrandChild.toString())
        assertEquals("/root/simpleChildInSlash", simpleChildInSlash.toString())
        assertEquals("/root/simpleChildInSlash/", slashChildInSimpleChild.toString())
    }
}
