package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun Route.postNew(dao: DAOFacade, hashFunction: (String) -> String) {
    get<PostNew> {
        val user = call.currentSessionOf<KweetSession>()?.let { dao.user(it.userId) }

        if (user == null) {
            call.redirect(Login())
        } else {
            val date = System.currentTimeMillis()
            val code = call.securityCode(date, user, hashFunction)

            call.respond(FreeMarkerContent("new-kweet.ftl", mapOf("user" to user, "date" to date, "code" to code), user.userId))
        }
    }
    post<PostNew> {
        val user = call.currentSessionOf<KweetSession>()?.let { dao.user(it.userId) }
        if (user == null || !call.verifyCode(it.date, user, it.code, hashFunction)) {
            call.redirect(Login())
        } else {
            val id = dao.createKweet(user.userId, it.text, null)
            call.redirect(ViewKweet(id))
        }
    }
}