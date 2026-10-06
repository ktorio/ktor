/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.kdoc

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.nio.file.Path

fun annotatePublicApiKDocs(file: KtFile, filePath: Path, link: String) {
    val updater = KDocUpdater()

    traversePublicApi(file) { declaration, fqname: String ->
        updater.updateKDoc(declaration, fqname)
    }

    updater.executeUpdate(filePath, link)
}

fun removeFeedbackLinksFromKDocs(file: KtFile, filePath: Path) {
    val originalContent = filePath.toFile().readText()
    var updatedContent = originalContent
    val kdocs = PsiTreeUtil.collectElementsOfType(file, KDoc::class.java)

    for (kdoc in kdocs.sortedByDescending { it.startOffset }) {
        updatedContent = updatedContent.replaceRange(
            kdoc.startOffset,
            kdoc.endOffset,
            removeFeedbackLinksFromKDoc(kdoc.text)
        )
    }

    if (updatedContent != originalContent) {
        filePath.toFile().writeText(updatedContent)
    }
}

fun traversePublicApi(file: KtFile, block: (KtDeclaration, String) -> Unit) {
    traversePublicApi(file.declarations, name = file.packageFqName.asString(), block = block)
}

fun traversePublicApi(declarations: List<KtDeclaration>, name: String, block: (KtDeclaration, String) -> Unit) {
    if (name.isBlank()) return

    for (declaration: KtDeclaration in declarations) {
        if (!declaration.isPublic) continue
        val fqname = "$name.${declaration.name}"
        block(declaration, fqname)

        if (declaration !is KtDeclarationContainer) continue

        traversePublicApi(declaration.declarations, fqname, block)
    }
}

data class KDocLocation(val startOffset: Int, val endOffset: Int, val fqname: String)

fun KDocUpdater.updateKDoc(declaration: KtDeclaration, fqname: String) {
    val kdoc: KDoc = declaration.docComment ?: return
    collectForUpdate(kdoc.lineStartOffset, kdoc.endOffset, fqname)
}

val PsiElement.lineStartOffset: Int
    get() = containingFile.text.lastIndexOf('\n', startOffset - 1) + 1
