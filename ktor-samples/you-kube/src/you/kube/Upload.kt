package you.kube

import kotlinx.html.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import java.io.*

fun Route.upload(database: Database, uploadDir: File) {
    get<Upload> {
        val session = call.currentSessionOf<YouKubeSession>()
        if (session == null) {
            call.respondRedirect(Login())
        } else {
            call.respondDefaultHtml(emptyList(), CacheControlVisibility.PRIVATE) {
                h2 { +"Upload video" }

                form(call.url(Upload()), classes = "pure-form-stacked", encType = FormEncType.multipartFormData, method = FormMethod.post) {
                    acceptCharset = "utf-8"

                    label {
                        for_ = "title"; +"Title:"
                        textInput { name = "title"; id = "title" }
                    }

                    br()
                    fileInput { name = "file" }
                    br()

                    submitInput(classes = "pure-button pure-button-primary") { value = "Upload" }
                }
            }
        }
    }

    post<Upload> {
        val session = call.currentSessionOf<YouKubeSession>()
        if (session == null) {
            call.respond(HttpStatusCode.Forbidden.description("Not logged in"))
        } else {
            val multipart = call.receiveMultipart()
            var title = ""
            var videoFile: File? = null

            multipart.parts.forEach {
                if (it is PartData.FormItem) {
                    if (it.partName == "title") {
                        title = it.value
                    }
                } else if (it is PartData.FileItem) {
                    val ext = File(it.originalFileName).extension
                    val file = File(uploadDir, "upload-${System.currentTimeMillis()}-${session.userId.hashCode()}-${title.hashCode()}.$ext")
                    it.streamProvider().use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
                    videoFile = file
                }

                it.dispose()
            }

            val id = database.addVideo(title, session.userId, videoFile!!)

            call.respondRedirect(VideoPage(id))
        }
    }
}
