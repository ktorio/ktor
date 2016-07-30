import com.beust.kobalt.*
import com.beust.kobalt.api.*
import com.beust.kobalt.plugin.packaging.*
import com.beust.kobalt.plugin.application.*
import com.beust.kobalt.plugin.kotlin.*
import com.beust.kobalt.plugin.packaging.*


val repos = repos("http://dl.bintray.com/kotlin/kotlinx.support", "http://dl.bintray.com/kotlinx/kotlinx")

val dokkaVersion = "0.9.8"
val kotlinVersion = "1.0.3"

object Versions {
    val ktor = "0.2.2-SNAPSHOT"
    val kotlin = "1.0.3"
    val dokka = "0.9.8"
    val jetty = "9.3.9.v20160517"
    val tomcat = "8.5.2"
}

/**
 * Common settings shared by all the projects.
 */
fun Project.defineProject(projectName: String, parentDir: String = ".", passedFatJar: Boolean = false) {
    name = projectName
    group = "org.jetbrains.ktor"
    artifactId = name
    version = Versions.ktor
    directory = "$parentDir/$name"

    sourceDirectories {
        path("src", "resources")
    }

    sourceDirectoriesTest {
        path("test")
    }

    test {
        include("Test*.*", "**/*Test.*", "**/*Tests.*")
    }

    assemble {
        jar {
            fatJar = passedFatJar
        }
    }
}

val ktor = project {
    defineProject("ktor")

    dependencies {
        compile("ch.qos.logback:logback-classic:1.1.2",
                "org.slf4j:slf4j-api:1.6.5",
                "com.typesafe:config:1.2.1",
                "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}",
                "org.jetbrains.kotlin:kotlin-runtime:${Versions.kotlin}",
                "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}",
                "org.jetbrains.kotlinx:kotlinx-support-jdk8:0.1-alpha-2")
    }

    dependenciesTest {
        compile("junit:junit:4.12",
                "org.jetbrains.kotlin:kotlin-test-junit:1.0.3")
    }
}

val core = project(ktor) {
    defineProject("ktor-core")

    dependencies {
        compile("com.googlecode.json-simple:json-simple:1.1.1")
    }

    dependenciesTest {
        compile("org.apache.directory.server:apacheds-server-integ:2.0.0-M21",
                "org.apache.directory.server:apacheds-core-integ:2.0.0-M21",
                "junit:junit:4.12",
                "org.jetbrains.kotlin:kotlin-test:${Versions.kotlin}",
                "org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    }
}

val features = project(ktor, core) {
    defineProject("ktor-features")
}

val locations = project(features) {
    defineProject("ktor-locations", "ktor-features")
}

val hosts = project(core) {
    defineProject("ktor-hosts")
}

val freemarker = project(features, ktor) {
    defineProject("ktor-freemarker", "ktor-features")

    dependencies {
        compile("org.freemarker:freemarker:[2.3.20,2.4)")
    }

    dependenciesTest {
        compile("junit:junit:4.12",
                "org.jetbrains.kotlin:kotlin-test:${Versions.kotlin}",
                "org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    }
}


val serverSessions = project(core) {
    defineProject("ktor-server-sessions", "ktor-features")
}

val servlet = project(hosts) {
    defineProject("ktor-servlet", "ktor-hosts")

    dependencies {
        compile("javax.servlet:javax.servlet-api:3.1.0")
    }
}

val jetty = project(servlet, core) {
    defineProject("ktor-jetty", "ktor-hosts")

    dependencies {
        compile("org.eclipse.jetty:jetty-server:${Versions.jetty}",
                "org.eclipse.jetty:jetty-servlet:${Versions.jetty}")
    }
}

val netty = project(core) {
    defineProject("ktor-netty", "ktor-hosts")

    dependencies {
        compile("io.netty:netty-all:4.0.30.Final")
    }
}

val tomcat = project(core, servlet) {
    defineProject("ktor-tomcat", "ktor-hosts")

    dependencies {
        compile("org.apache.tomcat:tomcat-catalina:${Versions.tomcat}",
                "org.apache.tomcat.embed:tomcat-embed-core:${Versions.tomcat}")
    }
}

val websockets = project(core, jetty, netty, tomcat) {
    defineProject("ktor-websockets", "ktor-features")
}

val samples = project(core) {
    defineProject("ktor-samples")

    dependencies {
        compile("org.jetbrains.kotlinx:kotlinx.html.jvm:[0.5,0.6)")
    }
}

/*
val samplesProjects = listOf("hello", "async", "testable").forEach {
    project(samples) {
        defineProject("ktor-samples-$it", "ktor-samples")

        assemble {
            jar {
                fatJar = true
            }
        }

        application {
            mainClass = "com.beust.kobalt.wrapper.Main"
        }
    }
}
*/
val samplesHello = project(samples, jetty) {
    defineProject("ktor-samples-hello", "ktor-samples", passedFatJar = true)

    application {
        mainClass = "org.jetbrains.ktor.jetty.DevelopmentHost"
    }
}

val samplesAsync = project(samples) {
    defineProject("ktor-samples-async", "ktor-samples")
}

val samplesTestable = project(samples) {
    defineProject("ktor-samples-testable", "ktor-samples")
}

val samplesAuth = project(samples, locations) {
    defineProject("ktor-samples-auth", "ktor-samples")
}

val samplesLocations = project(samples, locations) {
    defineProject("ktor-samples-locations", "ktor-samples")
}

val samplesJson = project(samples) {
    defineProject("ktor-samples-json", "ktor-samples")

    dependencies {
        compile("com.google.code.gson:gson:2.3.1")
    }
}

val samplesPost = project(samples, locations) {
    defineProject("ktor-samples-post", "ktor-samples")
}

val samplesEmbedded = project(samples, jetty, netty) {
    defineProject("ktor-samples-embedded", "ktor-samples")
}
