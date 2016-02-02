package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.postNew(dao: DAOFacade, hashFunction: (String) -> String) {
    get<PostNew> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }

        if (user == null) {
            redirect(Login())
        } else {
            val date = System.currentTimeMillis()
            val code = securityCode(date, user, hashFunction)

            response.send(FreeMarkerContent("new-kweet.ftl", mapOf("user" to user, "date" to date, "code" to code), user.userId))
        }
    }
    post<PostNew> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        if (user == null || !verifyCode(it.date, user, it.code, hashFunction)) {
            redirect(Login())
        } else {
            val id = dao.createKweet(user.userId, it.text, null)
            redirect(ViewKweet(id))
        }
    }
}