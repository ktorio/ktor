/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ContentDispositionTest {
    @Test
    fun testEscapeFilename() {
        val attachment = ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileNameAsterisk,
            "''malicious.sh%00'normal.txt"
        ).toString()

        assertEquals("attachment; filename*=utf-8''%27%27malicious.sh%2500%27normal.txt", attachment)
    }
}
