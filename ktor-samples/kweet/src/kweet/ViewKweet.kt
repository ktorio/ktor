package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.viewKweet(dao: DAOFacade, hashFunction: (String) -> String) {
    get<ViewKweet> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        val date = System.currentTimeMillis()
        val code = if (user != null) securityCode(date, user, hashFunction) else null

        response.send(FreeMarkerContent("view-kweet.ftl", mapOf("user" to user, "kweet" to dao.getKweet(it.id), "date" to date, "code" to code), user?.userId ?: ""))
    }
}
