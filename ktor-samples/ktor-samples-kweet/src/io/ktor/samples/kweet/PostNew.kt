package io.ktor.samples.kweet

import io.ktor.application.*
import io.ktor.freemarker.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.samples.kweet.dao.*
import io.ktor.sessions.*
import io.ktor.util.*

fun Route.postNew(dao: DAOFacade, hashFunction: (String) -> String) {
    get<PostNew> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }

        if (user == null) {
            call.redirect(Login())
        } else {
            val date = System.currentTimeMillis()
            val code = call.securityCode(date, user, hashFunction)

            call.respond(FreeMarkerContent("new-kweet.ftl", mapOf("user" to user, "date" to date, "code" to code), user.userId))
        }
    }
    post<PostNew> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }

        val post = call.receive<StringValues>()
        val date = post["date"]?.toLongOrNull() ?: return@post call.redirect(it)
        val code = post["code"] ?: return@post call.redirect(it)
        val text = post["text"] ?: return@post call.redirect(it)

        if (user == null || !call.verifyCode(date, user, code, hashFunction)) {
            call.redirect(Login())
        } else {
            val id = dao.createKweet(user.userId, text, null)
            call.redirect(ViewKweet(id))
        }
    }
}