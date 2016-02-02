package kweet

import kweet.dao.*
import kweet.model.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.register(dao: DAOFacade, hashFunction: (String) -> String) {
    post<Register> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        if (user != null) {
            redirect(UserPage(user.userId))
        } else {
            if (it.password.length < 6) {
                redirect(it.copy(error = "Password should be at least 6 characters long", password = ""))
            } else if (it.userId.length < 4) {
                redirect(it.copy(error = "Login should be at least 4 characters long", password = ""))
            } else if (!userNameValid(it.userId)) {
                redirect(it.copy(error = "Login should be consists of digits, letters, dots or underscores", password = ""))
            } else if (dao.user(it.userId) != null) {
                redirect(it.copy(error = "User with the following login is already registered", password = ""))
            } else {
                val hash = hashFunction(it.password)
                val newUser = User(it.userId, it.email, it.displayName, hash)

                try {
                    dao.createUser(newUser)

                    session(Session(newUser.userId))
                    redirect(UserPage(newUser.userId))
                } catch (e: Throwable) {
                    if (dao.user(it.userId) != null) {
                        redirect(it.copy(error = "User with the following login is already registered", password = ""))
                    } else {
                        application.config.log.error("Failed to register user", e)
                        redirect(it.copy(error = "Failed to register", password = ""))
                    }
                }
            }
        }
    }
    get<Register> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        if (user != null) {
            redirect(UserPage(user.userId))
        } else {
            response.send(FreeMarkerContent("register.ftl", mapOf("pageUser" to User(it.userId, it.email, it.displayName, ""), "error" to it.error), ""))
        }
    }
}
