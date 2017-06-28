package kweet

import kweet.dao.*
import kweet.model.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun Route.register(dao: DAOFacade, hashFunction: (String) -> String) {
    post<Register> {
        val user = call.currentSessionOf<KweetSession>()?.let { dao.user(it.userId) }
        if (user != null) {
            call.redirect(UserPage(user.userId))
        } else {
            if (it.password.length < 6) {
                call.redirect(it.copy(error = "Password should be at least 6 characters long", password = ""))
            } else if (it.userId.length < 4) {
                call.redirect(it.copy(error = "Login should be at least 4 characters long", password = ""))
            } else if (!userNameValid(it.userId)) {
                call.redirect(it.copy(error = "Login should be consists of digits, letters, dots or underscores", password = ""))
            } else if (dao.user(it.userId) != null) {
                call.redirect(it.copy(error = "User with the following login is already registered", password = ""))
            } else {
                val hash = hashFunction(it.password)
                val newUser = User(it.userId, it.email, it.displayName, hash)

                try {
                    dao.createUser(newUser)
                } catch (e: Throwable) {
                    if (dao.user(it.userId) != null) {
                        call.redirect(it.copy(error = "User with the following login is already registered", password = ""))
                    } else if (dao.userByEmail(it.email) != null) {
                        call.redirect(it.copy(error = "User with the following email ${it.email} is already registered", password = ""))
                    } else {
                        application.log.error("Failed to register user", e)
                        call.redirect(it.copy(error = "Failed to register", password = ""))
                    }
                }

                call.setSession(KweetSession(newUser.userId))
                call.redirect(UserPage(newUser.userId))
            }
        }
    }
    get<Register> {
        val user = call.currentSessionOf<KweetSession>()?.let { dao.user(it.userId) }
        if (user != null) {
            call.redirect(UserPage(user.userId))
        } else {
            call.respond(FreeMarkerContent("register.ftl", mapOf("pageUser" to User(it.userId, it.email, it.displayName, ""), "error" to it.error), ""))
        }
    }
}
