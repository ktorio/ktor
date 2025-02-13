kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-htmx"))
            implementation(project(":ktor-utils"))
        }
    }
    commonTest {
        dependencies {
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-html-builder"))
            implementation(project(":ktor-shared:ktor-htmx:ktor-htmx-html"))
        }
    }
}
