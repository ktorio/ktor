description = "Ktor Content Negotiation via kotlinx.serialization support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization"))
            api(libs.kotlinx.serialization.core)
        }
    }
}
