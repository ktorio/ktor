description = "Ktor http server legacy code and deprecations"

val typesafe_config_version: String by extra
val kotlin_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))
            api(project(":ktor-shared:ktor-shared-serialization"))
            api(project(":ktor-shared:ktor-shared-events"))

            api("com.typesafe:config:$typesafe_config_version")
            api("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
        }
    }
}
