val kotlinx_html_version: String by extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinx_html_version")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
        }
    }
}
