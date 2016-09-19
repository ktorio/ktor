package kweet

import com.mchange.v2.c3p0.*
import freemarker.cache.*
import kweet.dao.*
import kweet.model.*
import org.h2.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

@location("/")
class Index()

@location("/post-new")
data class PostNew(val text: String = "", val date: Long = 0L, val code: String = "")

@location("/kweet/{id}/delete")
data class KweetDelete(val id: Int, val date: Long, val code: String)

@location("/kweet/{id}")
data class ViewKweet(val id: Int)

@location("/user/{user}")
data class UserPage(val user: String)

@location("/register")
data class Register(val userId: String = "", val displayName: String = "", val email: String = "", val password: String = "", val error: String = "")

@location("/login")
data class Login(val userId: String = "", val password: String = "", val error: String = "")

@location("/logout")
class Logout()

data class Session(val userId: String)

class KweetApp : ApplicationModule(), AutoCloseable {

    val hashKey = hex("6819b57a326945c1968f45236589")
    val dir = File("target/db")
    val pool = ComboPooledDataSource().apply {
        driverClass = Driver::class.java.name
        jdbcUrl = "jdbc:h2:file:${dir.canonicalFile.absolutePath}"
        user = ""
        password = ""
    }

    val hmacKey = SecretKeySpec(hashKey, "HmacSHA1")
    val dao: DAOFacade = DAOFacadeCache(DAOFacadeDatabase(Database.connect(pool)), File(dir.parentFile, "ehcache"))

    override fun Application.install() {
        dao.init()

        install(DefaultHeaders)
        install(CallLogging)
        install(ConditionalHeaders)
        install(PartialContentSupport)
        install(Locations)
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(KweetApp::class.java.classLoader, "templates")
        }

        withSessions<Session> {
            withCookieByValue {
                settings = SessionCookiesSettings(transformers = listOf(SessionCookieTransformerMessageAuthentication(hashKey)))
            }
        }

        val hashFunction = { s: String -> hash(s) }

        routing {
            styles()
            index(dao)
            postNew(dao, hashFunction)
            delete(dao, hashFunction)
            userPage(dao)
            viewKweet(dao, hashFunction)

            login(dao, hashFunction)
            register(dao, hashFunction)
        }
    }

    override fun close() {
        pool.close()
    }

    fun hash(password: String): String {
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(hmacKey)
        return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

}

fun ApplicationCall.redirect(location: Any): Nothing {
    val host = request.host() ?: "localhost"
    val portSpec = request.port().let { if (it == 80) "" else ":$it" }
    val address = host + portSpec

    respondRedirect("http://$address${application.feature(Locations).href(location)}")
}

fun ApplicationCall.securityCode(date: Long, user: User, hashFunction: (String) -> String) =
        hashFunction("$date:${user.userId}:${request.host()}:${refererHost()}")

fun ApplicationCall.verifyCode(date: Long, user: User, code: String, hashFunction: (String) -> String) =
        securityCode(date, user, hashFunction) == code
                && (System.currentTimeMillis() - date).let { it > 0 && it < TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS) }

fun ApplicationCall.refererHost() = request.header(HttpHeaders.Referrer)?.let { URI.create(it).host }

private val userIdPattern = "[a-zA-Z0-9_\\.]+".toRegex()
internal fun userNameValid(userId: String) = userId.matches(userIdPattern)
