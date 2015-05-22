package ktor.samples.hello

import ktor.application.Application
import ktor.application.ApplicationConfig
import ktor.routing.get
import ktor.routing.routing

class HelloApplication(config: ApplicationConfig, classLoader: ClassLoader) : Application(config, classLoader) {
    init {
        routing {
            get("/") {
              response {
                  content("Hello World!")
                  send()
              }
            }
            get("/bye") {
                response {
                    content("Goodbye World!")
                    send()
                }
            }
        }
    }
}
