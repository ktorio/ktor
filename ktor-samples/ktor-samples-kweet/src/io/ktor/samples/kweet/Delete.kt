package io.ktor.samples.kweet

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.samples.kweet.dao.*
import io.ktor.sessions.*
import io.ktor.util.*

fun Route.delete(dao: DAOFacade, hashFunction: (String) -> String) {
    post<KweetDelete> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }

        val post = call.receive<Parameters>()
        val date = post["date"]?.toLongOrNull() ?: return@post call.redirect(ViewKweet(it.id))
        val code = post["code"] ?: return@post call.redirect(ViewKweet(it.id))
        val kweet = dao.getKweet(it.id)

        if (user == null || kweet.userId != user.userId || !call.verifyCode(date, user, code, hashFunction)) {
            call.redirect(ViewKweet(it.id))
        } else {
            dao.deleteKweet(it.id)
            call.redirect(Index())
        }
    }
}
