package io.ktor.openapi.routing

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import kotlin.String

interface SourceCoordinates {
    val filePath: String?
    val startOffset: Int
    val endOffset: Int

    fun coordinates() = SourceKey(filePath, startOffset, endOffset)

    operator fun contains(other: SourceCoordinates): Boolean =
        filePath == other.filePath && other.startOffset in startOffset ..< endOffset && other != this
}

context(context: CheckerContext)
fun FirExpression.sourceKey(): SourceKey =
    SourceKey(
        context.containingFilePath,
        source?.startOffset ?: -1,
        source?.endOffset ?: -1
    )

data class SourceKey(
    override val filePath: String?,
    override val startOffset: Int,
    override val endOffset: Int,
): SourceCoordinates