val moshi_version: String by project.extra
val okio_version: String by project.extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api("com.squareup.moshi:moshi:$moshi_version")
            api("com.squareup.okio:okio:$okio_version")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-json-tests"))
            api(project(":ktor-features:ktor-moshi"))
            api("com.squareup.moshi:moshi-kotlin:$moshi_version")
        }
    }
}
