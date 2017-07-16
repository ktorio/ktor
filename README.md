<img src="http://ktor.io/images/docs/ktor.png" alt="Ktor" width="600" style="max-width:100%;">

[ ![Download](https://api.bintray.com/packages/kotlin/ktor/ktor/images/download.svg) ](https://bintray.com/kotlin/ktor/ktor/_latestVersion)
[![TeamCity (simple build status)](https://img.shields.io/teamcity/http/teamcity.jetbrains.com/s/KotlinTools_Ktor_Build.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Ktor_Build&branch_KotlinTools_Ktor=%3Cdefault%3E&tab=buildTypeStatusDiv)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

Ktor is a framework for quickly creating web applications in Kotlin with minimal effort.

```kotlin
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        routing {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
        }
    }.start(wait = true)
}
```

* Runs embedded web server on `localhost:8080`
* Installs routing and responds with `Hello, world!` when receiving GET http request for root path

## Documentation

Please visit [Ktor.io](http://ktor.io) for Quick Start and detailed explanations of
features, usage and machinery. 

## Principles

### Unopinionated

Ktor Framework doesn't impose a lot of constraints on what technology a project is going to use – logging, templating, messaging, persistent, serializing, dependency injection, etc. Rarely it may be required to implement pretty simple interface, but usually it is a matter of writing a transforming or intercepting function. Features are installed into application using unified *interception* mechanism which allows building arbitrary pipelines. 

Ktor Application can be hosted in any servlet container with Servlet 3.0+ API support such as Tomcat, or standalone using Netty or Jetty. Support for other hosts can be added, though admittedly it's not an easy task.

Ktor APIs are mostly functions calls with lambdas. Thanks to Kotlin DSL capabilities, code looks declarative. Application composition is entirely developer's choice – with functions or classes, using dependency injection framework or doing it all manually in main function. 

### Asynchronous

Ktor pipeline machinery and API is utilising a number of Kotlin features to provide easy-to-use asynchronous programming model without making it too cumbersome. 

### Testable

Ktor application can be hosted in a [TestHost](https://github.com/Kotlin/ktor/wiki/Testing), which emulates to some 
extent web server without actually doing any networking. It provides easy way to test an application without mocking 
too much stuff, and still achieve good performance while validating application calls. Integration tests with real 
embedded web server are of course possible, too.

## Features

In addition to core HTTP request processing and response building, Ktor provides a number of features out of the box, all implemented through its extensibility:

* Routing: attaches code to specific path/query/method/header and extract parameters from placeholders
* Sessions: stores and retrieves additional information attached to client session
* Content transformations: transforms response content on the fly and utilise unified mechanism to build a response
* Authentication: authenticates client using Basic, Digest, Form, OAuth (1a & 2)
* Custom status pages: sends custom content for specific status responses such as 404 Not Found
* Content type mapping: maps file extension to mime type for static file serving
* Template engines: uses content transformation to enable transparent template engine usage
* Static content: serves streams of data from local file system with full asynchronous support
* HTTP core features
    * Compression: enables gzip/deflate compression when client accepts it
    * Conditional Headers: sends 304 Not Modified response when if-modified-since/etag indicate content is the same
    * Partial Content: sends partial content for streaming ranges, like in video streams
    * Automatic HEAD response: responds to HEAD requests by running through pipeline and dropping response body
    * CORS: verifies and sends headers according to cross-origin resource sharing control
    * HSTS and https redirect: supports strict transport security

## Maven

Add a repository

```
<repository>
    <snapshots>
        <enabled>false</enabled>
    </snapshots>
    <id>bintray-kotlin-ktor</id>
    <name>bintray</name>
    <url>https://dl.bintray.com/kotlin/ktor</url>
</repository>
```

Add a dependency:

```
<dependency>
    <groupId>org.jetbrains.ktor</groupId>
    <artifactId>ktor-core</artifactId>
    <version>${ktor.version}</version>
</dependency>

<!-- you also may need to include host implementation as well, for example

<dependency>
    <groupId>org.jetbrains.ktor</groupId>
    <artifactId>ktor-jetty</artifactId>
    <version>${ktor.version}</version>
</dependency>

-->
```

## Gradle

```
repositories {
    jcenter()
    maven { url "https://dl.bintray.com/kotlin/kotlinx" }
    maven { url "https://dl.bintray.com/kotlin/ktor" }
}
```

dependency:

```
dependencies {
    compile "org.jetbrains.ktor:ktor-core:$ktorVersion"
    // you may also need to include host implementation as well, for example
    // compile "org.jetbrains.ktor:ktor-jetty:$ktorVersion"
}
```

## Inspirations

Wasabi, Kara

