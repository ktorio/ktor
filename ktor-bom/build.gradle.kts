plugins {
    id("java-platform")
    id("maven-publish")
}

the<PublishingExtension>().publications {
    create<MavenPublication>("maven") {
        from(components.findByName("javaPlatform"))
    }
}

val name = project.name

dependencies {
    constraints {
        rootProject.subprojects.forEach {
            if (!it.plugins.hasPlugin("maven-publish") || it.name == name) return@forEach
            it.the<PublishingExtension>().publications.forEach { publication ->
                if (publication !is MavenPublication) return@forEach

                val artifactId = publication.artifactId
                if (artifactId.endsWith("-metadata") || artifactId.endsWith("-kotlinMultiplatform")) {
                    return@forEach
                }

                api("${publication.groupId}:${publication.artifactId}:${publication.version}")
            }
        }
    }
}

configurePublication()
