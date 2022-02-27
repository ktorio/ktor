description = ""

val coroutines_version: String by extra
val logback_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(kotlin("test"))

            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            implementation(project(":ktor-server:ktor-server-test-host"))
        }
    }

    jvmMain {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))

            implementation(kotlin("test-junit"))

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutines_version")

            // https://github.com/Kotlin/kotlinx.coroutines/issues/3001
            val jna_version = "5.9.0"
            api("net.java.dev.jna:jna:$jna_version")
            api("net.java.dev.jna:jna-platform:$jna_version")
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            api("ch.qos.logback:logback-classic:$logback_version")
        }
    }
}
