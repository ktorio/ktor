package kweet

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

@location("/styles/main.css")
class MainCss()

fun Route.styles() {
    get<MainCss> {
        call.respond(call.resolveResource("blog.css")!!)
    }
}
