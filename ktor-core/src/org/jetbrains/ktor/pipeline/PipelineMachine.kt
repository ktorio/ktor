package org.jetbrains.ktor.pipeline

class PipelineMachine() {
    private val executionStack = mutableListOf<PipelineExecution<*>>()

    fun <T : Any> execute(subject: T, pipeline: Pipeline<T>): Nothing {
        val execution = PipelineExecution(this, subject, pipeline.interceptors)
        executionStack.add(execution)
        if (executionStack.size == 1)
            proceed()
        else
            throw PipelineContinue()
    }

    fun proceed(): Nothing {
        loop@while (executionStack.size > 0) {
            val execution = executionStack.last()
            val blockIndex = execution.blockStack.size
            when (execution.state) {
                PipelineState.Executing -> {
                    if (blockIndex >= execution.blocks.size) {
                        execution.state = PipelineState.Succeeded
                        continue@loop
                    }

                    val block = execution.blocks[blockIndex]
                    execution.blockStack.add(block)
                    try {
                        block.call()
                    } catch(f: PipelineControlFlow) {
                        when (f) {
                            is PipelineContinue -> continue@loop
                            else -> throw f
                        }
                    } catch(assertion: AssertionError) {
                        throw assertion // do not prevent tests from failing
                    } catch(exception: Throwable) {
                        registerFail(exception)
                        continue@loop
                    }
                }
                PipelineState.Failed -> {
                    if (blockIndex > 0) {
                        val failureHandlerIndex = blockIndex - 1
                        val item = execution.blockStack.removeAt(failureHandlerIndex)
                        val handlers = item.failures
                        while (handlers.size > 0) {
                            val handler = handlers.removeAt(handlers.lastIndex)
                            try {
                                handler(execution.exception!!)
                            } catch (f: PipelineControlFlow) {
                                if (handlers.isNotEmpty()) {
                                    if (failureHandlerIndex < execution.blockStack.size) {
                                        execution.blockStack.add(failureHandlerIndex, item)
                                    } else {
                                        execution.blockStack.add(item)
                                    }
                                }

                                when (f) {
                                    is PipelineContinue -> continue@loop
                                    else -> throw f
                                }
                            } catch(t: Throwable) {
                                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                                (execution.exception as java.lang.Throwable).addSuppressed(t)
                            }
                        }
                    } else {
                        executionStack.removeAt(executionStack.lastIndex)
                    }
                }
                PipelineState.Succeeded -> {
                    if (blockIndex > 0) {
                        val successIndex = blockIndex - 1
                        val item = execution.blockStack.removeAt(successIndex)
                        val handlers = item.successes
                        while (handlers.size > 0) {
                            val handler = handlers.removeAt(handlers.lastIndex)
                            try {
                                handler()
                            } catch (f: PipelineControlFlow) {
                                if (handlers.isNotEmpty()) {
                                    if (successIndex < execution.blockStack.size) {
                                        execution.blockStack.add(successIndex, item)
                                    } else {
                                        execution.blockStack.add(item)
                                    }
                                }

                                when (f) {
                                    is PipelineContinue -> continue@loop
                                    else -> throw f
                                }
                            } catch(exception: Throwable) {
                                registerFail(exception)
                                continue@loop
                            }
                        }
                    } else {
                        executionStack.removeAt(executionStack.lastIndex)
                    }
                }
            }
        }

        throw PipelineCompleted()
    }

    fun pause(): Nothing {
        throw PipelinePaused()
    }

    fun registerFail(exception: Throwable) {
        executionStack.forEach {
            it.state = PipelineState.Failed
            it.exception = exception
        }
    }

    fun fail(exception: Throwable): Nothing {
        registerFail(exception)
        throw PipelineContinue()
    }

    fun finish(): Nothing {
        executionStack.last().let {
            it.state = PipelineState.Succeeded
        }
        throw PipelineContinue()
    }

    fun finishAll() : Nothing {
        executionStack.forEach {
            it.state = PipelineState.Succeeded
        }
        throw PipelineContinue()
    }
}

