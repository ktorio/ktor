package io.ktor.openapi.ir.generators

import io.ktor.openapi.ir.*
import io.ktor.openapi.ir.CallDescribeTransformer.Companion.OPENAPI_PACKAGE
import io.ktor.openapi.ir.inference.*
import io.ktor.openapi.model.SchemaAttribute
import io.ktor.openapi.model.SchemaAttribute.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val jsonSchemaCallableId = CallableId(
    packageName = FqName(OPENAPI_PACKAGE),
    callableName = Name.identifier("jsonSchema")
)

context(context: LambdaBuilderContext)
fun generateMediaTypeContent(contentField: RouteField.Content) {
    // encoding, examples?
    assignProperty("description", contentField.description)
    generateExtensionProperties(contentField)

    // when a type reference is present, we can assume there _should_ be a content type
    // otherwise, it could be an empty response (i.e., no content type)
    val contentType = contentField.contentType
    if (contentType != null) {
        contentType.evaluateToContentType()?.let { contentType ->
            +callFunctionWithScope("invoke", contentType) {
                contentField.typeReference?.let { typeReference ->
                    assignSchemaProperty(
                        typeReference,
                        contentField.schemaAttributes
                    )
                }
            }
        }
    } else {
        // directly assign the schema property on the response builder,
        // this will be used for default content types from content negotiation
        contentField.typeReference?.let { typeReference ->
            assignSchemaProperty(
                typeReference,
                contentField.schemaAttributes
            )
        }
    }
}

context(context: LambdaBuilderContext)
fun contentTextPlain() {
    contentTypeReference(
        context.parentDeclaration.symbol,
        contentTypeText,
        contentTypePlaintext
    )?.evaluateToContentType()?.let { contentType ->
        +callFunctionWithScope("invoke", contentType) {}
    }
}

context(context: LambdaBuilderContext)
fun generateExtensionProperties(contentField: RouteField.SchemaHolder) {
    for ((key, value) in contentField.extensionAttributes) {
        +callFunctionNamed(
            "extension",
            key.name.toConst(),
            value.toConst()
        )
    }
}

context(context: LambdaBuilderContext)
internal fun assignSchemaProperty(
    typeReference: TypeReference,
    schemaAttributes: Map<SchemaAttribute, String>,
) {
    val irType = typeReference.asIrType() ?: run {
        context.log("Could not resolve type reference for schema $typeReference")
        return
    }
    val jsonSchemaFunction: IrSimpleFunction = contextOf<LambdaBuilderContext>()
        .referenceFunctions(jsonSchemaCallableId)
        .single().owner
    val jsonSchemaCall = callFunctionWithTypeArg(jsonSchemaFunction, irType).let { schemaCall ->
        if (schemaAttributes.isNotEmpty())
            chainSchemaAttributes(schemaCall, schemaAttributes)
        else schemaCall
    }

    assignProperty("schema", jsonSchemaCall)
}

context(context: LambdaBuilderContext)
private fun chainSchemaAttributes(
    jsonSchemaCall: IrFunctionAccessExpression,
    schemaAttributes: Map<SchemaAttribute, String>
): IrFunctionAccessExpression {
    val schemaClass = jsonSchemaCall.symbol.owner.returnType.classOrNull?.owner
        ?: error("Return type of jsonSchema must be a class")
    val copyFunction = schemaClass.functions.first { it.name.asString() == "copy" }
    val builder = DeclarationIrBuilder(context, context.parentDeclaration.symbol)
    val copyCall = builder.irCall(copyFunction)
    val receiver = copyFunction.dispatchReceiverParameter!!
    copyCall.arguments[receiver.indexInParameters] = jsonSchemaCall
    for ((attr, value) in schemaAttributes) {
        val parameter = copyFunction.parameters
            .firstOrNull { it.name.asString() == attr.name }
            ?: continue
        val argumentValue = when (attr) {
            id,
            anchor,
            title,
            description,
            format,
            pattern -> value.toConst()

            required,
            nullable,
            deprecated,
            readOnly,
            writeOnly,
            uniqueItems,
            recursiveAnchor -> value.toBooleanLenient()?.toConst()

            maxItems,
            minItems,
            maxLength,
            minLength,
            maxProperties,
            minProperties -> value.toIntOrNull()?.toConst()

            maximum,
            minimum,
            multipleOf -> value.toDoubleOrNull()?.toConst()

            exclusiveMaximum,
            exclusiveMinimum -> value.toBooleanLenient()?.toConst()

            example,
            default,
            // TODO support examples and defaults
            examples,
            enum,
            // TODO support examples and defaults

            allOf,
            oneOf,
            not,
            anyOf,
            properties,
            additionalProperties,
            discriminator,
            xml,
            externalDocs,
            items -> {
                context.log("Unsupported schema attribute: ${attr.name}")
                null
            }
        }
        if (argumentValue == null) {
            context.log("Ignoring invalid attribute ${attr.name}: $value")
            continue
        }
        copyCall.arguments[parameter.indexInParameters] = argumentValue

    }
    return copyCall
}