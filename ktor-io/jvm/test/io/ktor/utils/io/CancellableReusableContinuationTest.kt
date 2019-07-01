package kotlinx.coroutines.experimental.io

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.intrinsics.*
import io.ktor.utils.io.internal.*
import java.lang.Exception
import kotlin.coroutines.*
import kotlin.test.*

class CancellableReusableContinuationTest {
    @Test
    fun testResumedBefore() = runBlocking<Unit> {
        val mutable = CancellableReusableContinuation<Unit>()
        mutable.resume(Unit)

        suspendCoroutineUninterceptedOrReturn<Unit> {
            val rc = mutable.completeSuspendBlock(it)
            assertSame(Unit, rc)
            rc
        }
    }

    @Test
    fun testResumedAfter() = runBlocking<Unit> {
        val mutable = CancellableReusableContinuation<Unit>()

        suspendCoroutineUninterceptedOrReturn<Unit> {
            val rc = mutable.completeSuspendBlock(it)
            assertSame(COROUTINE_SUSPENDED, rc)

            mutable.resume(Unit)

            COROUTINE_SUSPENDED
        }
    }

    @Test
    fun testResumedBeforeWithException() = runBlocking<Unit> {
        val mutable = CancellableReusableContinuation<Unit>()
        mutable.resumeWithException(MyException())

        suspendCoroutineUninterceptedOrReturn<Unit> {
            assertFailsWith<MyException> {
                mutable.completeSuspendBlock(it)
            }
            Unit
        }
    }

    @Test
    fun testResumedAfterWithException() = runBlocking<Unit> {
        val mutable = CancellableReusableContinuation<Unit>()

        try {
            suspendCoroutineUninterceptedOrReturn<Unit> {
                val rc = mutable.completeSuspendBlock(it)
                assertSame(COROUTINE_SUSPENDED, rc)

                mutable.resumeWithException(MyException())

                COROUTINE_SUSPENDED
            }
            fail("Should fail with MyException")
        } catch (expected: MyException) {
        }
    }

    @Test
    fun testCancellationBefore() = assertFailsWith<CancellationException> {
        runBlocking {
            val mutable = CancellableReusableContinuation<Unit>()
            coroutineContext[Job]!!.cancel()

            try {
                suspendCoroutineUninterceptedOrReturn<Unit> {
                    val rc = mutable.completeSuspendBlock(it)
                    assertSame(COROUTINE_SUSPENDED, rc)
                    rc
                }
                fail("Should be already cancelled")
            } catch (expected: CancellationException) {
            }
        }
    }.let { Unit }

    @Test
    fun testCancellationAfter() = assertFailsWith<CancellationException> {
        runBlocking {
            val mutable = CancellableReusableContinuation<Unit>()

            try {
                suspendCoroutineUninterceptedOrReturn<Unit> {
                    val rc = mutable.completeSuspendBlock(it)
                    assertSame(COROUTINE_SUSPENDED, rc)

                    coroutineContext[Job]!!.cancel()

                    rc
                }
                fail("Should be cancelled before")
            } catch (expected: CancellationException) {
            } catch (other: Throwable) {
                println("Got other")
            }
        }
    }.let { Unit }

    private class MyException : Exception()
}
