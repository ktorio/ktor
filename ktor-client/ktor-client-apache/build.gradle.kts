description = "Apache backend for ktor http client"

val apache_version: String by project.extra
val apache_core_version: String by project.extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api("org.apache.httpcomponents:httpasyncclient:$apache_version")
            implementation("org.apache.httpcomponents:httpcore-nio:$apache_core_version") {
                because("https://github.com/ktorio/ktor/issues/1018")
            }
            implementation("org.apache.httpcomponents:httpcore:$apache_core_version") {
                because("https://github.com/ktorio/ktor/issues/1018")
            }
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
