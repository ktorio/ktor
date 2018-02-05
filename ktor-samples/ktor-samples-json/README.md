*ktor-samples-json*
===================

## Usage

### Embedded Server

#### Dependencies

##### Gradle

```
compile 'org.jetbrains.ktor:ktor-gson:0.4.0-alpha-13'
compile 'org.jetbrains.ktor:ktor-netty:0.4.0-alpha-13'
```

#### Main Method

```kotlin
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.routing

data class Person(val firstname: String, val lastname: String)

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        install(GsonSupport) {
            setPrettyPrinting()
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

