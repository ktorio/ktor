description = "Legacy code and deprecations for Client and Server"

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}
