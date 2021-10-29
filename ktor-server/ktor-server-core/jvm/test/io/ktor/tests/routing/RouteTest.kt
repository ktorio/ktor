/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.routing

import io.ktor.http.*
import io.ktor.server.routing.*
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

    @Test
    fun testCreateChildKeepsDevelopmentMode() {
        val root = Route(parent = null, selector = PathSegmentConstantRouteSelector("root"), developmentMode = true)
        val simpleChild = root.createChild(PathSegmentConstantRouteSelector("simpleChild"))
        assertTrue(root.developmentMode)
        assertTrue(simpleChild.developmentMode)
    }

    @Test
    fun `test empty http method routes`() {
        val root = Route(parent = null, selector = RootRouteSelector())
        val methodRoutes = root.getAllHttpMethodRoutes()
        assertTrue(methodRoutes.isEmpty())
    }

    @Test
    fun `test find a few http method routes`() {
        val root = Route(parent = null, selector = RootRouteSelector())
        val firstChild = root.createChild(PathSegmentConstantRouteSelector("firstChild"))
        val firstGrandChild = firstChild.createChild(PathSegmentConstantRouteSelector("firstGrandChild"))
        val lastPathChild = firstGrandChild.createChild(PathSegmentConstantRouteSelector("lastPathChild"))

        val firstMethod = firstChild.createChild(HttpMethodRouteSelector(HttpMethod.Get))
        val secondMethod = lastPathChild.createChild(HttpMethodRouteSelector(HttpMethod.Post))
        val thirdMethod = lastPathChild.createChild(HttpMethodRouteSelector(HttpMethod.Put))

        val methodRoutes = root.getAllHttpMethodRoutes()
        assertEquals(3, methodRoutes.size)
        assertTrue(methodRoutes.contains(firstMethod))
        assertTrue(methodRoutes.contains(secondMethod))
        assertTrue(methodRoutes.contains(thirdMethod))
    }

    @Test
    fun `test find http method routes from a non root route`() {
        val root = Route(parent = null, selector = RootRouteSelector())
        val firstChild = root.createChild(PathSegmentConstantRouteSelector("firstChild"))
        val firstGrandChild = firstChild.createChild(PathSegmentConstantRouteSelector("firstGrandChild"))
        val lastPathChild = firstGrandChild.createChild(PathSegmentConstantRouteSelector("lastPathChild"))

        firstChild.createChild(HttpMethodRouteSelector(HttpMethod.Get))
        val secondMethod = lastPathChild.createChild(HttpMethodRouteSelector(HttpMethod.Post))
        val thirdMethod = lastPathChild.createChild(HttpMethodRouteSelector(HttpMethod.Put))

        val methodRoutes = firstGrandChild.getAllHttpMethodRoutes()
        assertEquals(2, methodRoutes.size)
        assertTrue(methodRoutes.contains(secondMethod))
        assertTrue(methodRoutes.contains(thirdMethod))
    }
}
