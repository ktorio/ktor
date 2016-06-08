package you.kube

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import java.io.*

fun Route.videos(database: Database) {
    get<Index> {
        val session = call.sessionOrNull<Session>()
        val topVideos = database.top()
        val etag = topVideos.joinToString { "${it.id},${it.title}" }.hashCode().toString() + "-" + session?.userId?.hashCode()
        val visibility = if (session == null) CacheControlVisibility.PUBLIC else CacheControlVisibility.PRIVATE

        call.respondDefaultHtml(listOf(EntityTagVersion(etag)), visibility) {
            div("posts") {
            when {
                topVideos.size == 0 -> {
                    h1("content-subhead") { +"No Videos" }
                    div {
                        +"You need to upload some videos to watch them"
                    }
                }
                topVideos.size < 11 -> {
                    h1("content-subhead") { +"Videos" }
                }
                else -> {
                    h1("content-subhead") { +"Top 10 Videos" }
                }
            }
                topVideos.forEach {
                    section("post") {
                        header("post-header") {
                            h3("post-title") {
                                a(href = application.feature(Locations).href(VideoPage(it.id))) { +it.title }
                            }
                            p("post-meta") {
                                +"by ${it.authorId}"
                            }
                        }
                    }
                }
            }
        }
    }

    get<VideoPage> {
        val video = database.videoById(it.id)

        if (video == null) {
            call.respond(HttpStatusCode.NotFound.description("Video ${it.id} doesn't exist"))
        } else {
            call.respondDefaultHtml(listOf(EntityTagVersion(video.hashCode().toString())), CacheControlVisibility.PUBLIC) {

                section("post") {
                    header("post-header") {
                        h3("post-title") {
                            a(href = application.feature(Locations).href(VideoPage(it.id))) { +video.title }
                        }
                        p("post-meta") {
                            +"by ${video.authorId}"
                        }
                    }
                }

                video("pure-u-5-5") {
                    controls = true
                    source {
                        src = call.url(VideoStream(it.id))
                        type = "video/ogg"
                    }
                }
            }
        }
    }

    get<VideoStream> {
        val video = database.videoById(it.id)

        if (video == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val type = ContentTypeByExtension.lookupByPath(video.videoFileName).first { it.contentType == "video" }
            call.respond(LocalFileContent(File(video.videoFileName), contentType = type))
        }
    }
}
