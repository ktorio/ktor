val ideaActive: Boolean by project.extra

if (ideaActive) {
    kotlin {
        sourceSets {
            val posixIde by creating {
                kotlin.srcDir("posixIde/src")
            }

            get("posixMain").dependsOn(posixIde)
        }
    }
}
