package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.login(dao: DAOFacade, hash: (String) -> String) {
    get<Login> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }

        if (user != null) {
            redirect(UserPage(user.userId))
        } else {
            response.send(FreeMarkerContent("login.ftl", mapOf("userId" to it.userId, "error" to it.error), ""))
        }
    }
    post<Login> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }

        if (user != null) {
            redirect(UserPage(user.userId))
        } else {
            val login = when {
                it.userId.length < 4 -> null
                it.password.length < 6 -> null
                !userNameValid(it.userId) -> null
                else -> dao.user(it.userId, hash(it.password))
            }

            if (login == null) {
                redirect(it.copy(password = "", error = "Invalid username or password"))
            } else {
                session(Session(login.userId))
                redirect(UserPage(login.userId))
            }
        }
    }
    get<Logout> {
        clearSession()
        redirect(Index())
    }
}
