kotlin.sourceSets {
    jvmAndPosixMain {
        dependencies {
            api(libs.kotlinx.html)
        }
    }
    jvmAndPosixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
        }
    }
}
