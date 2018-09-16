package io.ktor.client.features.json.serializer

import io.ktor.client.call.TypeInfo
import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

internal actual fun serializerForPlatform(type: TypeInfo): KSerializer<*> = serializerByTypeToken(type.reifiedType)

@Suppress("UNCHECKED_CAST")
fun serializerByTypeToken(type: Type): KSerializer<Any> = when (type) {
    is Class<*> -> if (!type.isArray) {
        serializerByClass(type.kotlin)
    } else {
        val componentType: Class<*> = type.componentType
        val componentSerializer = kotlinx.serialization.serializerByTypeToken(componentType)
        ReferenceArraySerializer(componentType.kotlin as KClass<Any>, componentSerializer) as KSerializer<Any>
    }
    is ParameterizedType -> {
        val rootClass = (type.rawType as Class<*>)
        val args = (type.actualTypeArguments).map { argument ->
            when (argument) {
                is WildcardType -> {
                    argument.upperBounds.first().let { firstUpperBound ->
                        when (firstUpperBound) {
                            is Class<*> -> firstUpperBound
                            else -> null
                        }
                    }
                }
                else -> null
            } ?: argument
        }
        when {
            List::class.java.isAssignableFrom(rootClass) -> ArrayListSerializer(serializerByTypeToken(args[0])) as KSerializer<Any>
            Set::class.java.isAssignableFrom(rootClass) -> HashSetSerializer(serializerByTypeToken(args[0])) as KSerializer<Any>
            Map::class.java.isAssignableFrom(rootClass) -> HashMapSerializer(
                serializerByTypeToken(args[0]), serializerByTypeToken(args[1])
            ) as KSerializer<Any>
            Map.Entry::class.java.isAssignableFrom(rootClass) -> MapEntrySerializer(
                serializerByTypeToken(args[0]), serializerByTypeToken(args[1])
            ) as KSerializer<Any>

            else -> {
                throw IllegalStateException("ParameterizedType '${type.rawType}' is not implemented")
                // Cannot access 'invokeSerializerGetter': it is internal in 'kotlinx.serialization'
                //                    val varargs = args.map { kotlinx.serialization.serializerByTypeToken(it) }.toTypedArray()
                //                    (rootClass.invokeSerializerGetter(*varargs) as? KSerializer<Any>) ?: serializerByClass<Any>(rootClass.kotlin)
            }
        }
    }
    else -> throw IllegalArgumentException("type should be instance of Class<?> or ParametrizedType")
}