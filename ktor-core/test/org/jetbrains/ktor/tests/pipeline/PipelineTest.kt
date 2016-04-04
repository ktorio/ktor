package org.jetbrains.ktor.tests.pipeline

import org.jetbrains.ktor.pipeline.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

/*
class PipelineTest {

    @Test
    fun emptyPipeline() {
        val execution = Pipeline<String>().execute("some")
        assertTrue(execution.state.finished())
    }

    @Test
    fun singleActionPipeline() {
        var value = false
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            value = true
            assertEquals("some", subject)
        }
        val execution = pipeline.execute("some")
        assertTrue(value)
        assertTrue(execution.state.finished())
    }

    @Test
    fun singleActionPipelineWithFinish() {
        var value = false
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFinish {
                assertTrue(value)
            }
            onFail {
                fail("This pipeline shouldn't fail")
            }
            assertFalse(value)
            value = true
            assertEquals("some", subject)
        }
        val execution = pipeline.execute("some")
        assertTrue(value)
        assertTrue(execution.state.finished())
    }

    @Test
    fun singleActionPipelineWithFail() {
        var failed = false
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFinish {
                fail("This pipeline shouldn't finish")
            }
            onFail {
                assertFalse(failed)
                failed = true
            }
            assertEquals("some", subject)
            throw UnsupportedOperationException()
        }
        val execution = pipeline.execute("some")
        assertTrue(failed)
        assertTrue(execution.state.finished())
    }

    @Test
    fun actionFinishOrder() {
        var count = 0
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFinish {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFinish {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
        }
        val execution = pipeline.execute("some")
        assertEquals(0, count)
        assertTrue(execution.state.finished())
    }

    @Test
    fun actionFailOrder() {
        var count = 0
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
            throw UnsupportedOperationException()
        }
        val execution = pipeline.execute("some")
        assertEquals(0, count)
        assertTrue(execution.state.finished())
    }

    @Test
    fun actionFinishFailOrder() {
        var count = 0
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            onFinish {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFinish {
                assertEquals(2, count)
                count--
                throw UnsupportedOperationException()
            }
            assertEquals(1, count)
            count++
        }
        val execution = pipeline.execute("some")
        assertEquals(0, count)
        assertTrue(execution.state.finished())
    }

    @Test
    fun actionFailFailOrder() {
        var count = 0
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
                assertEquals("1", it.message)
                assertEquals(1, (it as java.lang.Throwable).suppressed.size)
                assertEquals("2", (it as java.lang.Throwable).suppressed[0].message)
            }
            onFinish {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
                assertEquals("1", it.message)
                assertEquals(0, (it as java.lang.Throwable).suppressed.size)
                throw UnsupportedOperationException("2")
            }
            assertEquals(1, count)
            count++
            throw UnsupportedOperationException("1")
        }
        val execution = pipeline.execute("some")
        assertEquals(0, count)
        assertTrue(execution.state.finished())
    }

    @Test
    fun pauseResume() {
        var count = 0
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            assertEquals(0, count)
            count++
            pause()
        }

        pipeline.intercept {
            assertEquals(1, count)
            count++
        }

        val execution = pipeline.execute("some")
        assertEquals(PipelineExecution.State.Pause, execution.state)
        execution.proceed()
        assertEquals(2, count)
        assertTrue(execution.state.finished())
    }

    @Test
    fun fork() {
        var count = 0
        var max = 0
        var secondaryOk = false
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
            max = Math.max(max, count)
        }

        pipeline.intercept {
            val secondary = Pipeline<String>()
            secondary.intercept {
                onFail {
                    fail("This pipeline shouldn't fail")
                }
                assertEquals("another", subject)
                secondaryOk = true
            }
            fork("another", secondary, attach = { p, s -> }, detach = { p, s -> p.proceed() })
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
            max = Math.max(max, count)
            throw UnsupportedOperationException()
        }
        val execution = pipeline.execute("some")
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(0, count)
        assertEquals(2, max)
        assertEquals(PipelineExecution.State.Succeeded, execution.state)
    }

    @Test
    fun forkAndFail() {
        var count = 0
        var max = 0
        var secondaryOk = false
        val pipeline = Pipeline<String>()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            onFinish {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
            max = Math.max(max, count)
        }

        pipeline.intercept {
            val secondary = Pipeline<String>()
            secondary.intercept {
                onFinish {
                    fail("This pipeline shouldn't finish")
                }
                assertEquals("another", subject)
                secondaryOk = true
                throw UnsupportedOperationException()
            }
            fork("another", secondary, attach = { p, s -> }, detach = { p, s -> p.proceed() })
        }

        pipeline.intercept {
            fail("This pipeline shouldn't run")
        }

        val execution = pipeline.execute("some")
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(0, count)
        assertEquals(1, max)
        assertEquals(PipelineExecution.State.Succeeded, execution.state)
    }

    @Test
    fun asyncPipeline() {
        var count = 0
        val pipeline = Pipeline<String>()
        val latch = CountDownLatch(1)
        pipeline.intercept {
            pause()
            CompletableFuture.runAsync {
                assertEquals(0, count)
                count++
                proceed()
            }
        }

        pipeline.intercept {
            assertEquals(1, count)
            count++
            latch.countDown()
        }

        pipeline.execute("some")
        latch.await()
        assertEquals(2, count)
    }

    @Test
    fun asyncFork() {
        var count = 0
        val pipeline = Pipeline<String>()
        var secondaryOk = false
        val latch = CountDownLatch(1)
        pipeline.intercept {
            onFail {
                latch.countDown()
                fail("This pipeline shouldn't fail")
            }
            join(CompletableFuture.runAsync {
                assertEquals(0, count)
                count++
            })
        }

        pipeline.intercept {
            val secondary = Pipeline<String>()
            secondary.intercept {
                assertEquals("another", subject)
                join(CompletableFuture.runAsync {
                    secondaryOk = true
                })
            }
            fork("another", secondary, attach = { p, s -> }, detach = { p, s -> p.proceed() })
        }

        pipeline.intercept {
            assertEquals(1, count)
            count++
            latch.countDown()
        }

        pipeline.execute("some")
        latch.await()
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(2, count)
    }
}
*/
