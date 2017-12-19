package io.ktor.samples.kweet

import com.mchange.v2.c3p0.*
import freemarker.cache.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.samples.kweet.dao.*
import io.ktor.samples.kweet.model.*
import io.ktor.sessions.*
import io.ktor.util.*
import org.h2.*
import org.jetbrains.exposed.sql.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

@Location("/")
class Index()

@Location("/post-new")
class PostNew()

@Location("/kweet/{id}/delete")
class KweetDelete(val id: Int)

@Location("/kweet/{id}")
data class ViewKweet(val id: Int)

@Location("/user/{user}")
data class UserPage(val user: String)

@Location("/register")
data class Register(val userId: String = "", val displayName: String = "", val email: String = "", val error: String = "")

@Location("/login")
data class Login(val userId: String = "", val error: String = "")

@Location("/logout")
class Logout()

data class KweetSession(val userId: String)

class KweetApp {

    val hashKey = hex("6819b57a326945c1968f45236589")
    val dir = File("build/db")
    val pool = ComboPooledDataSource().apply {
        driverClass = Driver::class.java.name
        jdbcUrl = "jdbc:h2:file:${dir.canonicalFile.absolutePath}"
        user = ""
        password = ""
    }

    val hmacKey = SecretKeySpec(hashKey, "HmacSHA1")
    val dao: DAOFacade = DAOFacadeCache(DAOFacadeDatabase(Database.connect(pool)), File(dir.parentFile, "ehcache"))

    fun Application.install() {
        dao.init()
        environment.monitor.subscribe(ApplicationStopped) { pool.close() }

        install(DefaultHeaders)
        install(CallLogging)
        install(ConditionalHeaders)
        install(PartialContent)
        install(Locations)
        install(FreeMarker) {
            templateLoader = ClassTemplateLoader(KweetApp::class.java.classLoader, "templates")
        }

        install(Sessions) {
            cookie<KweetSession>("SESSION") {
                transform(SessionTransportTransformerMessageAuthentication(hashKey))
            }
        }

        val hashFunction = { s: String -> hash(s) }

        install(Routing) {
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

    fun hash(password: String): String {
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(hmacKey)
        return hex(hmac.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

}

suspend fun ApplicationCall.redirect(location: Any) {
    val host = request.host() ?: "localhost"
    val portSpec = request.port().let { if (it == 80) "" else ":$it" }
    val address = host + portSpec

    respondRedirect("http://$address${application.locations.href(location)}")
}

fun ApplicationCall.securityCode(date: Long, user: User, hashFunction: (String) -> String) =
        hashFunction("$date:${user.userId}:${request.host()}:${refererHost()}")

fun ApplicationCall.verifyCode(date: Long, user: User, code: String, hashFunction: (String) -> String) =
        securityCode(date, user, hashFunction) == code
                && (System.currentTimeMillis() - date).let { it > 0 && it < TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS) }

fun ApplicationCall.refererHost() = request.header(HttpHeaders.Referrer)?.let { URI.create(it).host }

private val userIdPattern = "[a-zA-Z0-9_\\.]+".toRegex()
internal fun userNameValid(userId: String) = userId.matches(userIdPattern)
