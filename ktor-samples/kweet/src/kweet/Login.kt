package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.util.*

fun Route.login(dao: DAOFacade, hash: (String) -> String) {
    get<Login> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }

        if (user != null) {
            call.redirect(UserPage(user.userId))
        } else {
            call.respond(FreeMarkerContent("login.ftl", mapOf("userId" to it.userId, "error" to it.error), ""))
        }
    }
    post<Login> {
        val post = call.receive<ValuesMap>()
        val userId = post["userId"] ?: return@post call.redirect(it)
        val password = post["password"] ?: return@post call.redirect(it)

        val error = Login(userId)

        val login = when {
            userId.length < 4 -> null
            password.length < 6 -> null
            !userNameValid(it.userId) -> null
            else -> dao.user(it.userId, hash(password))
        }

        if (login == null) {
            call.redirect(error.copy(error = "Invalid username or password"))
        } else {
            call.sessions.set(KweetSession(login.userId))
            call.redirect(UserPage(login.userId))
        }
    }
    get<Logout> {
        call.sessions.clear<KweetSession>()
        call.redirect(Index())
    }
}
