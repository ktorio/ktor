package kweet

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*

@location("/styles/main.css")
class MainCss()

fun Route.styles() {
    get<MainCss> {
        call.respond(call.resolveResource("blog.css")!!)
    }
}
