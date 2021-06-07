val jackson_version: String by extra
val jackson_kotlin_version: String by extra

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
                api("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_kotlin_version")
            }
        }
    }
}
