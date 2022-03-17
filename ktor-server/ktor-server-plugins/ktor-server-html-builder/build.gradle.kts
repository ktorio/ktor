val kotlinx_html_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-html:$kotlinx_html_version")
        }
    }
    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
        }
    }
}
