plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.run.configure {
    args(rootProject.projectDir.parentFile, "https://ktor.io/feedback/")
}

kotlin {
    jvmToolchain(21)
}
