package io.ktor.samples.kweet

import io.ktor.application.*
import io.ktor.freemarker.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.samples.kweet.dao.*
import io.ktor.sessions.*

fun Route.viewKweet(dao: DAOFacade, hashFunction: (String) -> String) {
    get<ViewKweet> {
        val user = call.sessions.get<KweetSession>()?.let { dao.user(it.userId) }
        val date = System.currentTimeMillis()
        val code = if (user != null) call.securityCode(date, user, hashFunction) else null

        call.respond(FreeMarkerContent("view-kweet.ftl", mapOf("user" to user, "kweet" to dao.getKweet(it.id), "date" to date, "code" to code), user?.userId ?: ""))
    }
}
