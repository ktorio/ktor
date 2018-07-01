package io.ktor.letsencrypt

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.*

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8889, host = "0.0.0.0") {
        install(LetsEncrypt) {
            email = "youremail@host.com"
            setProduction()
            addDomainSet("yourdomain.com")
            certFolder = File("./certs")
            sslPort = 443
            sslHost = "0.0.0.0"
            keySize = 4096
        }
        routing {
            get("/") {
                call.respondText("HELLO!")
            }
        }
    }.start(wait = true)
    /*
    embeddedServer(Netty, applicationEngineEnvironment {
        this.log = LoggerFactory.getLogger("ktor.application")
        this.module {
            install(LetsEncrypt) {
                email = "youremail@email.com"
                setProduction()
                addDomainSet("yourdomain.com")
                certFolder = File("./certs")
                sslPort = 443
                sslHost = "0.0.0.0"
                keySize = 4096
            }
            routing {
                get("/") {
                    call.respondText("HELLO!")
                }
            }
        }

        connector {
            this.port = 8889
            this.host = "0.0.0.0"
        }
        //sslConnector(LetsEncryptCerts.keyStore, LetsEncryptCerts.alias, { charArrayOf() }, { charArrayOf() }) {
        //    this.port = 8890
        //    this.host = "0.0.0.0"
        //}
    }) {
        // Netty config
    }.start(wait = true)
    */
}
