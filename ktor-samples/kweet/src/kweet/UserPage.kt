package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.userPage(dao: DAOFacade) {
    get<UserPage> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        val pageUser = dao.user(it.user)

        if (pageUser == null) {
            respondStatus(HttpStatusCode.NotFound, "User ${it.user} doesn't exist")
        } else {
            val kweets = dao.userKweets(it.user).map { dao.getKweet(it) }

            respond(FreeMarkerContent("user.ftl", mapOf("user" to user, "pageUser" to pageUser, "kweets" to kweets), user?.userId ?: ""))
        }
    }
}
