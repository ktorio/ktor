val ideaActive: Boolean by project.extra
val isMacosHost: Boolean by project.extra

kotlin {
    sourceSets {
        if (ideaActive) {
            val srcDir = when {
                isMacosHost -> "macosX64/src"
                else -> "linuxX64/src"
            }

            val posixIde by creating {
                kotlin.srcDir(srcDir)
            }

            get("posixMain").dependsOn(posixIde)
        }
    }
}
