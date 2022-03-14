
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            implementation(libs.kotlinx.coroutines.jdk8)
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
