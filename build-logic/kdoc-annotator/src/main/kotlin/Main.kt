import java.nio.file.Path
import kotlin.io.path.Path as createPath

fun main(args: Array<String>) {
    if (args.size != 2) {
        printHelp()
        return
    }

    val projectSources = args[0]
    val link = args[1]

    updateKDocsInDirectory(createPath(projectSources), link)
}

fun updateKDocsInDirectory(directory: Path, link: String) {
    forEachKtFileInDirectory(directory) { ktFile, path ->
        if (path.isInTestSourceSet()) {
            removeFeedbackLinksFromKDocs(ktFile, path)
        } else {
            annotatePublicApiKDocs(ktFile, path, link)
        }
    }
}

internal fun Path.isInTestSourceSet(): Boolean {
    val parent = parent ?: return false
    return parent.iterator().asSequence()
        .map { it.toString() }
        .any { sourceSet ->
            sourceSet == "test" ||
                sourceSet.endsWith("Test") ||
                sourceSet.startsWith("test") && sourceSet.getOrNull("test".length)?.isUpperCase() == true
        }
}

fun printHelp() {
    println("KDoc annotator is a tool to add a feedback link for the KDoc documentation if this link is not present yet.")
    println("Usage:")
    println("kdoc-annotator <path-to-project sources> <link>")
}
