tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        // micrometer uses jdk 1.8 features, so we need to use that version here
        jvmTarget = "1.8"
    }
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(libs.micrometer)
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
    }
}

