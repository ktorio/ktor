package kweet

import kweet.dao.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun Route.index(dao: DAOFacade) {
    get<Index> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }
        val top = dao.top(10).map { dao.getKweet(it) }
        val latest = dao.latest(10).map { dao.getKweet(it) }
        val etagString = user?.userId + "," + top.joinToString { it.id.toString() } + latest.joinToString { it.id.toString() }
        val etag = etagString.hashCode()

        call.respond(FreeMarkerContent("index.ftl", mapOf("top" to top, "latest" to latest, "user" to user), etag.toString()))
    }
}
