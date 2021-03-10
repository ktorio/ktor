kotlin.sourceSets.jsMain {
    dependencies {
        api(project(":ktor-client:ktor-client-js-node"))
        api(project(":ktor-client:ktor-client-js-browser"))
    }
}
