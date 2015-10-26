package org.jetbrains.ktor.samples.post

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*

@location("/") class index()

class FormPostApplication(config: ApplicationConfig) : Application(config) {
    init {
        locations {
            get<index>() {
                val contentType = ContentType.Text.Html.withParameter("charset", Charsets.UTF_8.name())

                response.status(HttpStatusCode.OK)
                response.contentType(contentType)
                response.write {
                    appendHTML().html {
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
                            form("/form", encType = FormEncType.multipartFormData, method = FormMethod.post) {
                                acceptCharset = "utf-8"
                                textInput { name = "field1" }
                                fileInput { name = "file1" }
                                submitInput { value = "send"  }
                            }
                        }
                    }
                }
                ApplicationRequestStatus.Handled
            }

            post("/form") {
                val multipart = request.content.get<MultiPartData>()

                response.status(HttpStatusCode.OK)
                response.contentType(ContentType.Text.Plain.withParameter("charset", Charsets.UTF_8.name()))
                response.write {
                    if (!request.isMultipart()) {
                        appendln("Not a multipart request")
                    } else {
                        multipart.parts.forEach { part ->
                            when (part) {
                                is PartData.FormItem -> appendln("Form field: ${part.partName} = ${part.value}")
                                is PartData.FileItem -> appendln("File field: ${part.partName} -> ${part.originalFileName}")
                            }
                            part.dispose()
                        }
                    }
                }

                ApplicationRequestStatus.Handled
            }
        }
    }
}
