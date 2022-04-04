description = "Ktor JSON Content Negotiation via kotlinx.serialization support"

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.kotlinx.serialization.json)
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-tests"))
        }
    }
}
