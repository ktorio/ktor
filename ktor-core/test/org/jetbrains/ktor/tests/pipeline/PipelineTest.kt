package org.jetbrains.ktor.tests.pipeline

import org.jetbrains.ktor.pipeline.*
import org.junit.*
import kotlin.test.*

class PipelineTest {

    @Test
    fun emptyPipeline() {
        val execution = Pipeline<String>().execute("some")
        assert(execution.state.finished())
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
        assert(execution.state.finished())
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
        assert(execution.state.finished())
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
        assert(execution.state.finished())
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
        assert(execution.state.finished())
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
        assert(execution.state.finished())
    }

    @Test
    fun fork() {
        var count = 0
        var max = 0
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
            }
            fork("another", secondary)
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
        assertEquals(0, count)
        assertEquals(2, max)
        assert(execution.state.finished())
    }
}
