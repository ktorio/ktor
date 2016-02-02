package kweet

import kweet.dao.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.delete(dao: DAOFacade, hashFunction: (String) -> String) {
    post<KweetDelete> {
        val user = sessionOrNull<Session>()?.let { dao.user(it.userId) }
        val kweet = dao.getKweet(it.id)

        if (user == null || kweet.userId != user.userId || !verifyCode(it.date, user, it.code, hashFunction)) {
            redirect(ViewKweet(it.id))
        } else {
            dao.deleteKweet(it.id)
            redirect(Index())
        }
    }
}
