import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.net.*

description = "Common tests for client"

val junit_version: String by project.extra
val kotlin_version: String by project.extra
val logback_version: String by project.extra
val coroutines_version: String by project

val ideaActive: Boolean by project.extra

plugins {
    id("kotlinx-serialization")
}

open class KtorTestServer : DefaultTask() {
    var server: Closeable? = null
    lateinit var main: String
    lateinit var classpath: FileCollection

    @TaskAction
    fun exec() {
        try {
            println("[TestServer] start")
            val urlClassLoaderSource = classpath.map { file -> file.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(main)
            val main = mainClass.getMethod("startServer")
            server = main.invoke(null) as Closeable
        } catch (cause: Throwable) {
        }
    }

}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-test-dispatcher"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-serialization"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-features:ktor-client-auth"))
            api(project(":ktor-client:ktor-client-features:ktor-client-encoding"))
        }
    }
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-features:ktor-auth"))
            api(project(":ktor-features:ktor-websockets"))
            api(project(":ktor-features:ktor-serialization"))
            api("ch.qos.logback:logback-classic:$logback_version")
            api("junit:junit:$junit_version")
            api("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")
        }
    }
    jvmTest {
        dependencies {
            runtimeOnly(project(":ktor-client:ktor-client-apache"))
            runtimeOnly(project(":ktor-client:ktor-client-cio"))
            runtimeOnly(project(":ktor-client:ktor-client-android"))
            runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
//            runtimeOnly(project(":ktor-client:ktor-client-jetty"))
        }
    }
    jsTest {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }

    if (!ideaActive) {
        listOf("linuxX64Test", "mingwX64Test", "macosX64Test").map { getByName(it) }.forEach {
            it.dependencies {
                api(project(":ktor-client:ktor-client-curl"))
            }
        }
        listOf("iosX64Test", "macosX64Test").map { getByName(it) }.forEach {
            it.dependencies {
                // api(project(":ktor-client:ktor-client-ios"))
            }
        }
    } else {
        posixTest {
            dependencies {
                // api(project(":ktor-client:ktor-client-ios"))
                api(project(":ktor-client:ktor-client-curl"))
            }
        }
    }
}

val startTestServer = task<KtorTestServer>("startTestServer") {
    dependsOn(tasks.jvmJar)

    main = "io.ktor.client.tests.utils.TestServerKt"
    val kotlinCompilation = kotlin.targets.getByName("jvm").compilations["test"]
    classpath = (kotlinCompilation as KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles
}

val testTasks = mutableListOf(
    "jvmTest",
    "jvmBenchmark",

    // 1.4.x JS tasks
    "jsLegacyNodeTest",
    "jsIrNodeTest",
    "jsLegacyBrowserTest",
    "jsIrBrowserTest",

    "posixTest",
    "darwinTest"
)

if (!ideaActive) {
    testTasks += listOf(
        "macosX64Test",
        "linuxX64Test",
        "iosX64Test",
        "mingwX64Test"
    )
}

rootProject.allprojects {
    if (path.contains("ktor-client")) {
        val tasks = tasks.matching { it.name in testTasks }
        configure(tasks) {
            dependsOn(startTestServer)
        }
    }
}

gradle.buildFinished {
    if (startTestServer.server != null) {
        startTestServer.server?.close()
        println("[TestServer] stop")
    }
}

