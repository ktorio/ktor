package io.ktor.samples.kweet

import io.ktor.application.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.samples.kweet.dao.*
import io.ktor.sessions.*

fun Route.userPage(dao: DAOFacade) {
    get<UserPage> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }
        val pageUser = dao.user(it.user)

        if (pageUser == null) {
            call.respond(HttpStatusCode.NotFound.description("User ${it.user} doesn't exist"))
        } else {
            val kweets = dao.userKweets(it.user).map { dao.getKweet(it) }
            val etag = (user?.userId ?: "") + "_" + kweets.map { it.text.hashCode() }.hashCode().toString()

            call.respond(FreeMarkerContent("user.ftl", mapOf("user" to user, "pageUser" to pageUser, "kweets" to kweets), etag))
        }
    }
}
