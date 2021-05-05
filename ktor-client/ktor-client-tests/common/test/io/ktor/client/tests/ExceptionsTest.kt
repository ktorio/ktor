/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.reflect.*
import kotlin.test.*

class ExceptionsTest : ClientLoader() {

    @Test
    fun testReadResponseFromException() = testSuspend {
        if (PlatformUtils.IS_NATIVE) return@testSuspend

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(HttpStatusCode.BadRequest)
                }
            }
        }

        try {
            client.get<String>("www.google.com")
        } catch (exception: ResponseException) {
            val text = exception.response?.readText()
            assertEquals(HttpStatusCode.BadRequest.description, text)
        }
    }

    @Test
    fun testTextInResponseException() = testTextInException(
        code = HttpStatusCode.BadRequest,
        message = "Some request",
        exceptionType = ResponseException::class
    )

    @Test
    fun testTextInRedirectResponseException() =
        testTextInException(
            code = HttpStatusCode.PermanentRedirect,
            message = "Some redirect",
            exceptionType = RedirectResponseException::class
        )

    @Test
    fun testTextInClientRequestException() =
        testTextInException(
            code = HttpStatusCode.Conflict,
            message = "Some conflict",
            exceptionType = ClientRequestException::class
        )

    @Test
    fun testTextInServerRequestException() =
        testTextInException(
            code = HttpStatusCode.VariantAlsoNegotiates,
            message = "Some variant",
            exceptionType = ServerResponseException::class
        )

    @Test
    fun testBinaryGarbageInExceptionMessage() = testTextInException(
        code = HttpStatusCode.BadRequest,
        message = """
            .Q
            build.gradle�Q�J�0��+B��NwŃ<�A����C7m��${'$'}]���n�v��������7o&��+��B�4�]���^�,����
            9a�j�Ȯ�@��˰uZ��x��g(�'������g�r��|��+Z�b��K�X�)��L�<
                                                    Ol�o�;7~B�Z!+
            j�m�`=��'��xm9
              ן�]�̇��%��ȓ��=�t ����M�)A�C�7H�|ƾ��rs��ʺ��}��8��!�M��O!�g�
                             \"\"                                           ���a����7D\X6��E��
                             "   {}';a
            ?��������~u��ڿqa҂C3����o异>u~�����W���U�N���9��j1_L_�c��PS_��9�P϶�r�f.Q�2Cj�
                                                                  build.gradlf.Qƻ�>"${'$'}�settings.gradlf.Q&&
                                                                                                                 %
        """.trimIndent(),
        exceptionType = ResponseException::class
    )

    private fun testTextInException(
        code: HttpStatusCode,
        message: String,
        exceptionType: KClass<out ResponseException>
    ) = testSuspend {
        if (PlatformUtils.IS_NATIVE) return@testSuspend

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respondError(code, message)
                }
            }
            followRedirects = false
        }

        try {
            client.get<String>("www.google.com")
        } catch (exception: ResponseException) {
            assertTrue(
                exceptionType.isInstance(exception),
                "Exception must be of type ${exceptionType.simpleName} but is of type ${exception::class.simpleName}"
            )
            assertNotNull(exception.message, "Message must be specified")
            assertTrue(
                exception.message!!.endsWith("Text: \"$message\""),
                "Exception message must contain response text"
            )
        }
    }

    @Test
    fun testErrorOnResponseCoroutine() = clientTests(listOf("Curl", "CIO")) {
        test { client ->
            val requestBuilder = HttpRequestBuilder()
            requestBuilder.url.takeFrom("$TEST_SERVER/download/infinite")

            assertFailsWith<IllegalStateException> {
                client.get<HttpStatement>(requestBuilder).execute { response ->
                    try {
                        CoroutineScope(response.coroutineContext)
                            .launch { throw IllegalStateException("failed on receive") }
                            .join()
                    } catch (cause: Exception) {
                    }
                    response.receive<String>()
                }
            }

            assertTrue(requestBuilder.executionContext[Job]!!.isActive)
        }
    }
}
