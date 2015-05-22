package ktor.samples.hello

import ktor.application.Application
import ktor.application.ApplicationConfig

class HelloApplication(config: ApplicationConfig, classLoader: ClassLoader) : Application(config, classLoader) {
    init {
        intercept { request, next ->
            request.response {
                content("Hello World!")
                send()
            }
            next(request)
            true
        }
    }
}
