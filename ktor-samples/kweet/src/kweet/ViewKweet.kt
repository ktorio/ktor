package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun Route.viewKweet(dao: DAOFacade, hashFunction: (String) -> String) {
    get<ViewKweet> {
        val user = call.currentSessionOf<KweetSession>()?.let { dao.user(it.userId) }
        val date = System.currentTimeMillis()
        val code = if (user != null) call.securityCode(date, user, hashFunction) else null

        call.respond(FreeMarkerContent("view-kweet.ftl", mapOf("user" to user, "kweet" to dao.getKweet(it.id), "date" to date, "code" to code), user?.userId ?: ""))
    }
}
