package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.CodeGenContext
import io.ktor.openapi.ir.LambdaBuilderContext
import io.ktor.openapi.routing.LocalReference
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val contentTypeClass = ClassId(
    packageFqName = FqName("io.ktor.http"),
    topLevelName = Name.identifier("ContentType"),
)
internal val contentTypeText = ClassId(
    packageFqName = FqName("io.ktor.http"),
    relativeClassName = FqName("ContentType.Text"),
    isLocal = false,
)
internal val contentTypeApplication = ClassId(
    packageFqName = FqName("io.ktor.http"),
    relativeClassName = FqName("ContentType.Application"),
    isLocal = false,
)
internal val contentTypePlaintext = CallableId(
    packageName = FqName("io.ktor.http"),
    className = FqName("ContentType.Text"),
    callableName = Name.identifier("Plain")
)
internal val contentTypeHtml = CallableId(
    packageName = FqName("io.ktor.http"),
    className = FqName("ContentType.Text"),
    callableName = Name.identifier("Html")
)
internal val contentTypeOctetStream = CallableId(
    packageName = FqName("io.ktor.http"),
    className = FqName("ContentType.Application"),
    callableName = Name.identifier("OctetStream")
)

/**
 * When reading content type from KDoc, we need to convert String constants to ContentType.
 */
context(context: LambdaBuilderContext)
internal fun LocalReference.evaluateToContentType(): IrExpression? =
    when(this) {
        // Call something like ContentType("text", "plain")
        is LocalReference.StringValue -> {
            val (category, subType) = key.split('/', limit = 2)
            val declaration = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
            val constructor = context.referenceConstructors(contentTypeClass).minByOrNull {
                it.owner.parameters.size
            } ?: return null

            declaration.irCall(constructor).apply {
                arguments[0] = declaration.irString(category)
                arguments[1] = declaration.irString(subType)
            }
        }
        is LocalReference.Expression -> asExpression()
        else -> null
    }

/**
 * Get a reference to `ContentType` instances like `ContentType.Application.Json`
 *
 * This is a call to a property getter on an object.
 */
context(context: CodeGenContext)
internal fun contentTypeReference(
    parentSymbol: IrSymbol,
    classId: ClassId,
    callableId: CallableId,
): LocalReference? {
    val propertyGetter: IrSimpleFunctionSymbol = context.referenceProperties(callableId)
        .firstOrNull()?.owner?.getter?.symbol
        ?: error("Property $callableId not found")
    val objectClass: IrClassSymbol = context.referenceClass(classId)
        ?: error("Class $classId not found")
    val builder = DeclarationIrBuilder(context, parentSymbol)
    val callToProperty = builder.irCall(propertyGetter).apply {
        dispatchReceiver = builder.irGetObject(objectClass)
    }

    return LocalReference.of(callToProperty)
}

context(context: CodeGenContext)
internal fun contentTypeAny(parentSymbol: IrSymbol): LocalReference? {
    val contentTypeClass: IrClassSymbol = context.referenceClass(contentTypeClass)
        ?: error("ContentType class not found")
    val companionObj = contentTypeClass.owner.companionObject()
        ?: error("ContentType companion object not found")
    val anyPropertyGetter = companionObj.properties.firstOrNull { it.name.asString() == "Any" }?.getter?.symbol
        ?: error("Property 'Any' not found on ContentType.Companion")
    val builder = DeclarationIrBuilder(context, parentSymbol)
    val callToProperty = builder.irCall(anyPropertyGetter).apply {
        dispatchReceiver = builder.irGetObject(companionObj.symbol)
    }

    return LocalReference.of(callToProperty)
}

context(context: CodeGenContext)
fun getContentTypeArgument(call: IrCall): LocalReference? {
    for (i in 0 until call.arguments.size) {
        val arg = call.arguments[i] ?: continue
        val argClass = arg.type.classOrNull?.owner
        if (argClass?.name?.asString() == "ContentType") {
            return LocalReference.of(arg)
        }
    }
    return null
}