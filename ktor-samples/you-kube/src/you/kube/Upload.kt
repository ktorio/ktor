package you.kube

import kotlinx.html.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import java.io.*

fun RoutingEntry.upload(database: Database, uploadDir: File) {
    get<Upload> {
        val session = sessionOrNull<Session>()
        if (session == null) {
            redirect(Login())
        } else {
            respondDefaultHtml(emptyList(), CacheControlVisibility.PRIVATE) {
                h2 { +"Upload video" }

                form(url(Upload()), encType = FormEncType.multipartFormData, method = FormMethod.post) {
                    acceptCharset = "utf-8"

                    label { for_ = "title"; +"Title:" }
                    textInput { name = "title"; id = "title" }
                    br {}


                    fileInput { name = "file" }

                    br {}

                    submitInput { value = "Upload" }
                }
            }
        }
    }

    post<Upload> {
        val session = sessionOrNull<Session>()
        if (session == null) {
            respondStatus(HttpStatusCode.Forbidden, "Not logged in")
        } else {
            val multipart = request.content.get<MultiPartData>()
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

            redirect(VideoPage(id))
        }
    }
}
