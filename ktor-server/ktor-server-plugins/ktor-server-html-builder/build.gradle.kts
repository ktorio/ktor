val kotlinx_html_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(libs.kotlinx.html)
        }
    }
    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
        }
    }
}
