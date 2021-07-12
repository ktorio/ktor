val isMacosHost: Boolean by project.extra

kotlin {
    sourceSets {
        if (KtorBuildProperties.ideaActive) {
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
