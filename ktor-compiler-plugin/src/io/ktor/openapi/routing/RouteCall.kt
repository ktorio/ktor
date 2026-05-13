package io.ktor.openapi.routing

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.text

/**
 * Returns an instance of [RouteCall], unless the [fir] expression has no source location information.
 *
 * @param fir the FIR expression to create a route call from
 * @param fields the route fields to include in the route call
 * @param isLeaf whether the route call represents a leaf node in the routing tree
 * @return a [RouteCall] instance or null if the FIR expression has no source location information
 */
context(context: CheckerContext)
fun RouteCall(
    fir: FirExpression,
    fields: RouteFieldList,
    isLeaf: Boolean,
): RouteCall? {
    val filePath = context.containingFilePath ?: return null
    val startOffset = fir.source?.startOffset ?: return null
    val endOffset = fir.source?.endOffset ?: return null
    return RouteCall(
        filePath,
        startOffset,
        endOffset,
        fir.source.text,
        fields,
        isLeaf,
    )
}

/**
 * Code references to the Ktor routing API.  These recorded, then merged to form the model of the OpenAPI specification.
 */
class RouteCall(
    override val filePath: String,
    override val startOffset: Int,
    override val endOffset: Int,
    val sourceText: CharSequence?,
    val fields: RouteFieldList,
    val isLeaf: Boolean,
): SourceCoordinates {
    val id: String get() = filePath + startOffset.toString()

    override fun toString(): String = sourceText?.toString() ?: locationString()

    fun locationString(): String =
        "${filePath.substringAfterLast('/')}($startOffset..$endOffset)"

    override fun equals(other: Any?): Boolean =
        other is RouteCall && id == other.id

    override fun hashCode(): Int = id.hashCode()
}