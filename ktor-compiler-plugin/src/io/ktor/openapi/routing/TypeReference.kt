package io.ktor.openapi.routing

import io.ktor.openapi.JsonPrimitive
import io.ktor.openapi.ir.CodeGenContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

sealed interface TypeReference {
    companion object {
        context(context: CodeGenContext)
        fun IrType.asReference() =
            Resolved(context.inferConcreteType(this))
    }

    context(context: IrPluginContext)
    fun asIrType(): IrType?

    data class Resolved(val type: IrType) : TypeReference {
        context(context: IrPluginContext)
        override fun asIrType(): IrType = type
    }

    object StringType: TypeReference {
        context(context: IrPluginContext)
        override fun asIrType(): IrType =
            context.irBuiltIns.stringType
    }

    sealed interface Link: TypeReference {
        val name: String

        data class Reference(override val name: String) : Link {
            context(context: IrPluginContext)
            override fun asIrType(): IrType? =
                context.referenceClass(
                    ClassId.topLevel(FqName(name))
                )?.defaultType
        }
        data class Primitive(override val name: String, val jsonPrimitive: JsonPrimitive) : Link {
            context(context: IrPluginContext)
            override fun asIrType(): IrType =
                when(jsonPrimitive) {
                    JsonPrimitive.STRING -> context.irBuiltIns.stringType
                    JsonPrimitive.NUMBER -> context.irBuiltIns.doubleType
                    JsonPrimitive.INTEGER -> context.irBuiltIns.intType
                    JsonPrimitive.BOOLEAN -> context.irBuiltIns.booleanType
                }
        }
        data class Array(val element: Link) : Link by element {
            context(context: IrPluginContext)
            override fun asIrType(): IrType? {
                val elementType = element.asIrType() ?: return null
                return context.irBuiltIns.listClass.typeWith(elementType)
            }
        }
        data class Map(val valueType: Link) : Link by valueType {
            context(context: IrPluginContext)
            override fun asIrType(): IrType? {
                val valueIrType = valueType.asIrType() ?: return null
                return context.irBuiltIns.mapClass.typeWith(
                    context.irBuiltIns.stringType,
                    valueIrType
                )
            }
        }
        data class Optional(val delegate: Link) : Link by delegate {
            context(context: IrPluginContext)
            override fun asIrType(): IrType? {
                return delegate.asIrType()?.makeNullable()
            }
        }
    }
}
