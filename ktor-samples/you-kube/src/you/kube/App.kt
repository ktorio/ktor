package you.kube

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.util.*
import java.io.*

@location("/video/{id}")
data class VideoStream(val id: Long)

@location("/video/page/{id}")
data class VideoPage(val id: Long)

@location("/login")
data class Login(val userName: String = "", val password: String = "")

@location("/upload")
class Upload()

@location("/")
class Index()

data class Session(val userId: String)

class App(config: ApplicationConfig) : Application(config) {
    init {
        install(Locations.LocationsFeature)
        val key = hex("03e156f6058a13813816065")
        val uploadDir = File(System.getProperty("user.home"), "Videos")
        val users = UserHashedTableAuth(table = mapOf(
                "root" to UserHashedTableAuth(table = emptyMap()).digester("root")
        ))

        routing {
            withSessions<Session> {
                withCookieByValue {
                    settings = SessionCookiesSettings(transformers = listOf(
                            SessionCookieTransformerMessageAuthentication(key)
                    ))
                }
            }

            get<Index> {
                val session = sessionOrNull<Session>()
                val topVideos = Database.top()
                val etag = topVideos.joinToString { "${it.id},${it.title}" }.hashCode().toString() + "," + session?.userId?.hashCode()

                withETag(etag) {
                    response.contentType(ContentType.Text.Html.withParameter("charset", "utf-8"))
                    response.write {
                        append("<!DOCTYPE html>\n")
                        appendHTML().html {
                            head {
                                title { +"You Kube" }
                            }
                            body {
                                h1 { +"You kube" }
                                p {
                                    +"Welcome to You Kube"
                                    if (session != null) {
                                        +", ${session.userId}"
                                    }
                                }
                                p("menu") {
                                    if (session == null) {
                                        a(href = application.feature(Locations).href(Login())) { +"Login" }
                                    } else {
                                        a(href = application.feature(Locations).href(Upload())) { +"Upload video" }
                                    }
                                }
                                h2 { +"Top 10" }
                                ul {
                                    topVideos.forEach {
                                        li { a(href = application.feature(Locations).href(VideoPage(it.id))) { +it.title } }
                                    }
                                }
                            }
                        }
                    }
                    ApplicationCallResult.Handled
                }
            }


            method(HttpMethod.Post) {
                location<Login> {
                    auth {
                        formAuth("userName")

                        verifyBatchTypedWith(users)

                        success { authContext, next ->
                            session(Session(authContext.principal<UserIdPrincipal>()!!.name))
                            redirect(Index())
                        }
                        fail {
                            redirect(Login(authContext.credentials<UserPasswordCredential>().firstOrNull()?.name ?: ""))
                        }
                    }

                    handle { ApplicationCallResult.Unhandled }
                }
            }

            get<Login> {
                response.contentType(ContentType.Text.Html.withParameter("charset", "utf-8"))
                response.write {
                    append("<!DOCTYPE html>\n")
                    appendHTML().html {
                        head {
                            title { +"You Kube" }
                        }
                        body {
                            h1 { +"You kube" }
                            p { +"Welcome to You Kube" }
                            h2 { +"Login" }
//                            form(application.feature(Locations).href(Login()), encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                            form("/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                                acceptCharset = "utf-8"

                                ul {
                                    li {
                                        +"Username: "
                                        textInput { name = "userName"; value = it.userName }
                                    }
                                    li {
                                        +"Password: "
                                        passwordInput { name = "password" }
                                    }
                                    li {
                                        submitInput { +"Login" }
                                    }
                                }
                            }
                        }
                    }
                }
                ApplicationCallResult.Handled
            }

            get<Upload> {
                val session = sessionOrNull<Session>()
                if (session == null) {
                    response.sendRedirect(application.feature(Locations).href(Login()))
                } else {
                    response.contentType(ContentType.Text.Html.withParameter("charset", "utf-8"))
                    response.write {
                        append("<!DOCTYPE html>\n")
                        appendHTML().html {
                            head {
                                title { +"You Kube" }
                            }
                            body {
                                h1 { +"You kube" }
                                h2 { +"Upload video" }

                                form(application.feature(Locations).href(Upload()), encType = FormEncType.multipartFormData, method = FormMethod.post) {
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
                    ApplicationCallResult.Handled
                }
            }

            post<Upload> {
                val session = sessionOrNull<Session>()
                if (session == null) {
                    response.sendError(HttpStatusCode.Forbidden, "Not logged in")
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

                    val id = Database.addVideo(title, session.userId, videoFile!!)

                    redirect(VideoPage(id))
                }
            }

            get<VideoPage> {
                val video = Database.videoById(it.id)

                if (video == null) {
                    response.status(HttpStatusCode.NotFound)
                    ApplicationCallResult.Handled
                } else {
                    withETag(video.hashCode().toString()) {
                        response.contentType(ContentType.Text.Html.withParameter("charset", "utf-8"))
                        val localUrl = "${request.host() ?: "localhost"}:${request.port()}"

                        response.write {
                            append("<!DOCTYPE html>\n")
                            appendHTML().html {
                                head {
                                    title { +"${video.title} - You Kube" }
                                }
                                body {
                                    h1 { +video.title }

                                    video {
                                        controls = true
                                        width = "640"
                                        height = "320"

                                        source {
                                            src = "http://$localUrl/video/${it.id}"
                                            type = "video/ogg"
                                        }
                                    }

                                    p { +"Uploaded by ${video.authorId}" }
                                }
                            }
                        }

                        ApplicationCallResult.Handled
                    }
                }
            }

            get<VideoStream> {
                val video = Database.videoById(it.id)

                if (video == null) {
                    response.status(HttpStatusCode.NotFound)
                    ApplicationCallResult.Handled
                } else {
                    val type = ContentTypeByExtension.lookupByPath(video.videoFileName).first { it.contentType == "video" }
                    response.send(LocalFileContent(File(video.videoFileName), contentType = type))
                }
            }
        }
    }

    private fun ApplicationCall.redirect(location: Any): ApplicationCallResult {
        val localUrl = "${request.host() ?: "localhost"}:${request.port()}"
        return response.sendRedirect("http://$localUrl${application.feature(Locations).href(location)}")
    }
}

