
dependencies {
    expectedBy(project(":ktor-client:ktor-client-features:ktor-client-logging"))

    implementation("org.slf4j:slf4j-simple:1.6.1")
    compile(project(":ktor-client:ktor-client-core:ktor-client-core-jvm"))

    testCompile(project(":ktor-client:ktor-client-tests"))
    testCompile(project(":ktor-client:ktor-client-cio"))
}
