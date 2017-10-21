package io.ktor.samples.youkube

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.html.*
import java.io.*

fun Route.videos(database: Database) {
    get<Index> {
        val session = call.sessions.get<YouKubeSession>()
        val topVideos = database.top()
        val etag = topVideos.joinToString { "${it.id},${it.title}" }.hashCode().toString() + "-" + session?.userId?.hashCode()
        val visibility = if (session == null) CacheControl.Visibility.Public else CacheControl.Visibility.Private

        call.respondDefaultHtml(listOf(EntityTagVersion(etag)), visibility) {
            div("posts") {
            when {
                topVideos.isEmpty() -> {
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
                                a(href = locations.href(VideoPage(it.id))) { +it.title }
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
            call.respondDefaultHtml(listOf(EntityTagVersion(video.hashCode().toString())), CacheControl.Visibility.Public) {

                section("post") {
                    header("post-header") {
                        h3("post-title") {
                            a(href = locations.href(VideoPage(it.id))) { +video.title }
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
            val type = ContentType.fromFilePath(video.videoFileName).first { it.contentType == "video" }
            call.respond(LocalFileContent(File(video.videoFileName), contentType = type))
        }
    }
}
