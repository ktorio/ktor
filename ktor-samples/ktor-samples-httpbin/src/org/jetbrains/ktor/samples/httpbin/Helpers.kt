package org.jetbrains.ktor.samples.httpbin

import kotlinx.html.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*

fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    val url = "${origin.scheme}://${origin.host}$port${origin.uri}"
    return url
}

/** /links/:n/:m **/
fun HTML.generateLinks(nbLinks: Int, selectedLink: Int) {
    head {
        title {
            +"Links"
        }
    }
    body {
        ul {
            for (i in 0.rangeTo(nbLinks - 1)) {
                li {
                    a {
                        if (i != selectedLink) {
                            attributes["href"] = "/links/$nbLinks/$i"
                        }
                        +"Link $i"
                    }
                }
            }
        }
    }
}

fun HTML.invalidRequest(message: String) {
    head {
        title {
            +"Invalid Request"
        }
    }
    body {
        h1 {
            +"Invalid request"
        }
        p {
            code { +message }
        }
    }
}


data class ImageConfig(val path: String, val contentType: ContentType, val filename: String)

/** For /deny **/
val ANGRY_ASCII = """
          .-''''''-.
        .' _      _ '.
       /   O      O   \\
      :                :
      |                |
      :       __       :
       \  .-"`  `"-.  /
        '.          .'
          '-......-'
     YOU SHOULDN'T BE HERE
"""



