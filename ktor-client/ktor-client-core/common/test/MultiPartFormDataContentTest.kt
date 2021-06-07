import io.ktor.client.request.forms.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

class MultiPartFormDataContentTest {

    @Test
    fun testMultiPartFormDataContentHasCorrectPrefix() = testSuspend {
        val formData = MultiPartFormDataContent(
            formData {
                append("Hello", "World")
            }
        )

        val channel = ByteChannel()
        formData.writeTo(channel)
        channel.close()

        val actual = channel.readRemaining().readBytes()

        assertNotEquals('\r'.toByte(), actual[0])
        assertNotEquals('\n'.toByte(), actual[1])
        assertNotEquals('\r'.toByte(), actual[2])
        assertNotEquals('\n'.toByte(), actual[3])
    }
}
