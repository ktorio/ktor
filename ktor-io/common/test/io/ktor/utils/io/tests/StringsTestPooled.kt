package io.ktor.utils.io.tests

import io.ktor.utils.io.core.internal.*

class StringsTestPooled : StringsTest() {
    override val pool: VerifyingObjectPool<ChunkBuffer> = VerifyingObjectPool(ChunkBuffer.Pool)
}
