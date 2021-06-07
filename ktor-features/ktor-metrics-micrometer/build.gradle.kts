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
                // 1.1.3 is the latest version that works on older Android so we are unable to upgrade
                api("io.micrometer:micrometer-core:1.7.0")
                implementation(project(":ktor-server:ktor-server-host-common"))
            }
        }
    }
}

