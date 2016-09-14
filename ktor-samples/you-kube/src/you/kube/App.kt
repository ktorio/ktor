package you.kube

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
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

data class Session(val userId: String)

class App : ApplicationModule() {
    override fun install(application: Application) {
        with(application) {
            install(DefaultHeaders)
            install(CallLogging)
            install(Locations)
            install(ConditionalHeaders)
            install(PartialContentSupport)
            install(Compression) {
                default()
                excludeMimeTypeMatch(ContentType.Video.Any)
            }

            val key = hex("03e156f6058a13813816065")
            val uploadDir = File("ktor-samples/you-kube/.video")
            if (!uploadDir.mkdirs() && !uploadDir.exists()) {
                throw IOException("Failed to create directory ${uploadDir.absolutePath}")
            }
            val database = Database(uploadDir)

            val users = UserHashedTableAuth(table = mapOf(
                    "root" to UserHashedTableAuth(table = emptyMap()).digester("root")
            ))

            withSessions<Session> {
                withCookieByValue {
                    settings = SessionCookiesSettings(transformers = listOf(
                            SessionCookieTransformerMessageAuthentication(key)
                    ))
                }
            }

            routing {
                login(users)
                upload(database, uploadDir)
                videos(database)
                styles()
            }
        }
    }

}

fun ApplicationCall.respondRedirect(location: Any): Nothing = respondRedirect(url(location), permanent = false)

