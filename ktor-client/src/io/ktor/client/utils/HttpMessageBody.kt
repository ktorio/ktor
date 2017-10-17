package io.ktor.client.utils

import java.io.InputStream
import java.io.OutputStream


sealed class HttpMessageBody
class InputStreamBody(val stream: InputStream) : HttpMessageBody()
class OutputStreamBody(val block: (OutputStream) -> Unit) : HttpMessageBody()

object EmptyBody : HttpMessageBody()