val kotlinx_html_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinx_html_version")
        }
    }
}
