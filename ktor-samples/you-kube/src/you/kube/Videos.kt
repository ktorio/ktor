package you.kube

import kotlinx.html.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import java.io.*

fun RoutingEntry.videos(database: Database) {
    get<Index> {
        val session = sessionOrNull<Session>()
        val topVideos = database.top()
        val etag = topVideos.joinToString { "${it.id},${it.title}" }.hashCode().toString() + "," + session?.userId?.hashCode()
        val visibility = if (session == null) CacheControlVisibility.PUBLIC else CacheControlVisibility.PRIVATE

        respondDefaultHtml(listOf(EntityTagVersion(etag)), visibility) {
            p {
                +"Welcome to You Kube"
                if (session != null) {
                    +", ${session.userId}"
                }
            }
            p("menu") {
                if (session == null) {
                    a(href = application.feature(Locations).href(Login())) { +"Login" }
                } else {
                    a(href = application.feature(Locations).href(Upload())) { +"Upload video" }
                }
            }
            h2 { +"Top 10" }
            ul {
                topVideos.forEach {
                    li { a(href = application.feature(Locations).href(VideoPage(it.id))) { +it.title } }
                }
            }
        }
    }

    get<VideoPage> {
        val video = database.videoById(it.id)

        if (video == null) {
            respondStatus(HttpStatusCode.NotFound, "Video ${it.id} doesn't exist")
        } else {
            respondDefaultHtml(listOf(EntityTagVersion(video.hashCode().toString())), CacheControlVisibility.PUBLIC, video.title) {
                video {
                    controls = true
                    width = "640"
                    height = "320"

                    source {
                        src = url(VideoStream(it.id))
                        type = "video/ogg"
                    }
                }

                p { +"Uploaded by ${video.authorId}" }
            }
        }
    }

    get<VideoStream> {
        val video = database.videoById(it.id)

        if (video == null) {
            respondStatus(HttpStatusCode.NotFound)
        } else {
            val type = ContentTypeByExtension.lookupByPath(video.videoFileName).first { it.contentType == "video" }
            respond(LocalFileContent(File(video.videoFileName), contentType = type))
        }
    }
}
