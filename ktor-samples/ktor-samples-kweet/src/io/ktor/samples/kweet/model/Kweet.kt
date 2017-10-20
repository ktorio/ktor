package io.ktor.samples.kweet.model

import org.joda.time.*
import java.io.*

data class Kweet(val id: Int, val userId: String, val text: String, val date: DateTime, val replyTo: Int?) : Serializable