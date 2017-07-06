package kweet

import kweet.dao.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun Route.delete(dao: DAOFacade, hashFunction: (String) -> String) {
    post<KweetDelete> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }
        val kweet = dao.getKweet(it.id)

        if (user == null || kweet.userId != user.userId || !call.verifyCode(it.date, user, it.code, hashFunction)) {
            call.redirect(ViewKweet(it.id))
        } else {
            dao.deleteKweet(it.id)
            call.redirect(Index())
        }
    }
}
