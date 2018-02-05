*ktor-samples-gson*
===================

## Usage

### Embedded Server

#### Dependencies

##### Gradle

```
compile "io.ktor:ktor-gson:$ktor_version"
compile "io.ktor:ktor-server-netty:$ktor_version"
```

#### Main Method

```kotlin
import io.ktor.application.install
import io.ktor.application.call
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respond
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.routing.get
import io.ktor.routing.routing

data class Person(val firstname: String, val lastname: String)

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        routing {
            get("/person") {
                val alBundy = Person("Al", "Bundy")
                call.respond(alBundy)
            }
        }
    }.start(wait = true)
}
```

#### Test

Run in a terminal:
```bash
$ curl http://localhost:8080/person
```

Output should look like:
```json
{
  "firstname": "Al",
  "lastname": "Bundy"
}
```

