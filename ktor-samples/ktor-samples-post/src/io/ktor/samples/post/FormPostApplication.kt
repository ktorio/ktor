package io.ktor.samples.post

import kotlinx.html.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

@Location("/") class index()
@Location("/form") class post()

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    routing {
        get<index> {
            val contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)

            call.respondHtml {
                head {
                    title { +"POST" }
                    meta {
                        httpEquiv = HttpHeaders.ContentType
                        content = contentType.toString()
                    }
                }
                body {
                    p {
                        +"File upload example"
                    }
                    form(locations.href(post()), encType = FormEncType.multipartFormData, method = FormMethod.post) {
                        acceptCharset = "utf-8"
                        textInput { name = "field1" }
                        fileInput { name = "file1" }
                        submitInput { value = "send" }
                    }
                }
            }
        }

        post<post> {
            val multipart = call.receiveMultipart()
            call.respondWrite {
                if (!call.request.isMultipart()) {
                    appendln("Not a multipart request")
                } else {
                    while (true) {
                        val part = multipart.readPart() ?: break
                        when (part) {
                            is PartData.FormItem -> appendln("Form field: ${part.partName} = ${part.value}")
                            is PartData.FileItem -> appendln("File field: ${part.partName} -> ${part.originalFileName} of ${part.contentType}")
                        }
                        part.dispose()
                    }
                }
            }

        }
    }
}
