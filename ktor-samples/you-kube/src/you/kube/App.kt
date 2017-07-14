package you.kube

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
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

data class YouKubeSession(val userId: String)

fun Application.youKubeApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(ConditionalHeaders)
    install(PartialContentSupport)
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

    install(Routing) {
        login(users)
        upload(database, uploadDir)
        videos(database)
        styles()
    }
}

suspend fun ApplicationCall.respondRedirect(location: Any) = respondRedirect(url(location), permanent = false)
