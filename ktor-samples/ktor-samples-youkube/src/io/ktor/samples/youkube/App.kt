package io.ktor.samples.youkube

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import java.io.*

@Location("/video/{id}")
data class VideoStream(val id: Long)

@Location("/video/page/{id}")
data class VideoPage(val id: Long)

@Location("/login")
data class Login(val userName: String = "", val password: String = "")

@Location("/upload")
class Upload()

@Location("/")
class Index()

data class YouKubeSession(val userId: String)

fun Application.youKubeApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(ConditionalHeaders)
    install(PartialContent)
    install(Compression) {
        default()
        excludeContentType(ContentType.Video.Any)
    }
    val youkubeConfig = environment.config.config("youkube")
    val sessionCookieConfig = youkubeConfig.config("session.cookie")
    val key: String = sessionCookieConfig.property("key").getString()
    val sessionkey = hex(key)

    val uploadDirPath: String = youkubeConfig.property("upload.dir").getString()
    val uploadDir = File(uploadDirPath)
    if (!uploadDir.mkdirs() && !uploadDir.exists()) {
        throw IOException("Failed to create directory ${uploadDir.absolutePath}")
    }
    val database = Database(uploadDir)

    val users = UserHashedTableAuth(table = mapOf(
            "root" to UserHashedTableAuth(table = emptyMap()).digester("root")
    ))

    install(Sessions) {
        cookie<YouKubeSession>("SESSION") {
            transform(SessionTransportTransformerMessageAuthentication(sessionkey))
        }
    }

    install(Authentication) {
        form {
            userParamName = Login::userName.name
            passwordParamName = Login::password.name
            challenge = FormAuthChallenge.Redirect { call, c -> call.url(Login(c?.name ?: "")) }
            validate { users.authenticate(it) }
        }
    }

    install(Routing) {
        login()
        upload(database, uploadDir)
        videos(database)
        styles()
    }
}

suspend fun ApplicationCall.respondRedirect(location: Any) = respondRedirect(url(location), permanent = false)
