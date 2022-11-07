package io.ktor.network.selector

import kotlinx.coroutines.*

@Suppress("KDocMissingDocumentation")
public class ClosedChannelCancellationException : CancellationException("Closed channel.")
