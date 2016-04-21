package you.kube

import kotlinx.css.port.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*

@location("/styles/main.css")
class MainCss()

fun RoutingEntry.styles() {
    get<MainCss> {
        call.respond(call.resolveClasspathWithPath("", "blog.css")!!)
    }
}

fun Style.fontSize() {
    font.size("14px")
}

fun mainCss() = renderCSS {
    div.style("container") {
        fontSize()
    }
}
