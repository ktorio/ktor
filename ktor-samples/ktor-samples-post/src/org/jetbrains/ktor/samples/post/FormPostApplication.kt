package org.jetbrains.ktor.samples.post

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

@location("/") class index()
@location("/form") class post()

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
                    form(feature(Locations).href(post()), encType = FormEncType.multipartFormData, method = FormMethod.post) {
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
                    multipart.parts.forEach { part ->
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
