package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.lang.reflect.*
import java.math.*
import java.time.*
import java.time.temporal.*
import java.util.*
import java.util.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

interface SessionTracker<S : Any> {
    fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus
    fun assign(context: ApplicationRequestContext, session: S)
    fun unassign(context: ApplicationRequestContext)
}

class CookiesSettings {
    val expireIn: TemporalAmount = Duration.ofDays(30)
    val requireHttps: Boolean = false
}

private fun CookiesSettings.newCookie(name: String, value: String) = Cookie(name, value, httpOnly = true, secure = requireHttps, expires = LocalDateTime.now().plus(expireIn))

class CookieByValueSessionTracker<S : Any>(val settings: CookiesSettings, val cookieName: String, val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override fun assign(context: ApplicationRequestContext, session: S) {
        context.response.cookies.append(settings.newCookie(cookieName, serializer.serialize(session)))
    }

    override fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val cookie = context.request.cookies[cookieName]
        if (cookie != null) {
            injectSession(serializer.deserialize(cookie))
        }
        return next(context)
    }

    override fun unassign(context: ApplicationRequestContext) {
        context.response.cookies.appendExpired(cookieName)
    }
}

class CookieByIdSessionTracker<S : Any>(val exec: ExecutorService, val settings: CookiesSettings, val cookieName: String = "SESSION_ID", val serializer: SessionSerializer<S>, val storage: SessionStorage) : SessionTracker<S> {

    private val SessionIdKey = AttributeKey<String>()

    override fun assign(context: ApplicationRequestContext, session: S) {
        val sessionId = context.attributes.computeIfAbsent(SessionIdKey) { nextNonce() }
        storage.save(sessionId) { out ->
            out.bufferedWriter().use { writer ->
                writer.write(serializer.serialize(session))
            }
        }
        context.response.cookies.append(settings.newCookie(cookieName, sessionId))
    }

    override fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val sessionId = context.request.cookies[cookieName]
        return if (sessionId == null) {
            next(context)
        } else {
            context.attributes.put(SessionIdKey, sessionId)
            context.handleAsync(exec, {
                val session = serializer.deserialize(storage.read(sessionId) { input -> input.bufferedReader().readText() })
                injectSession(session)
                next(context)
            }, {
            })
        }
    }

    override fun unassign(context: ApplicationRequestContext) {
        context.attributes.remove(SessionIdKey)

        context.request.cookies[cookieName]?.let { sessionId ->
            context.response.cookies.appendExpired(cookieName)
            storage.invalidate(sessionId)
        }
    }
}

interface SessionSerializer<T : Any> {
    fun serialize(session: T): String
    fun deserialize(s: String): T
}

inline fun <reified T: Any> autoSerializerOf(): SessionSerializer<T> = autoSerializerOf(T::class)
fun <T: Any> autoSerializerOf(type: KClass<T>): SessionSerializer<T> = AutoSessionSerializer(type)

private class AutoSessionSerializer<T : Any>(val type: KClass<T>) : SessionSerializer<T> {
    val properties by lazy { type.memberProperties.sortedBy { it.name } }

    override fun deserialize(s: String): T {
        val bundle = parseQueryString(s)
        val instance = newInstance(bundle)

        for (p in properties) {
            val encodedValue = bundle[p.name]
            if (encodedValue != null) {
                val value = deserializeValue(encodedValue)
                assignValue(instance, p, value)
            }
        }

        return instance
    }

    override fun serialize(session: T): String = properties.map { it.name to serializeValue(it.get(session)) }.formUrlEncode()

    private fun newInstance(bundle: ValuesMap): T {
        val constructor = findConstructor(bundle)
        val params = constructor.parameters.toMap({it}, { coerceType(it.type, deserializeValue(bundle[it.name!!]!!)) })
        return constructor.callBy(params)
    }

    private fun findConstructor(bundle: ValuesMap): KFunction<T> =
        type.constructors
                .filter { it.parameters.all { it.name != null && it.name!! in bundle } }
                .maxBy { it.parameters.size }
                ?: throw IllegalArgumentException("Couldn't instantiate type $type for parameters ${bundle.names()}")

    private fun assignValue(instance: T, p: KProperty1<T, *>, value: Any?) {
        val originalValue = p.get(instance)

        when {
            isListType(p.returnType) -> when {
                value !is List<*> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<T, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableList<*> -> {
                    originalValue.clear()
                    originalValue.addAll(value)
                }
                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }
            isSetType(p.returnType) -> when {
                value !is Set<*> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<T, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableSet<*> -> {
                    originalValue.clear()
                    originalValue.addAll(value)
                }
                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }
            isMapType(p.returnType) -> when {
                value !is Map<*, *> -> assignValue(instance, p, coerceType(p.returnType, value))
                p is KMutableProperty1<T, *> -> p.setter.call(instance, coerceType(p.returnType, value))
                originalValue is MutableMap<*, *> -> {
                    originalValue.clear()
                    originalValue.putAll(value)
                }
                else -> throw IllegalStateException("Couldn't inject property ${p.name} from value $value")
            }
            p is KMutableProperty1<T, *> -> when {
                value == null && !p.returnType.isMarkedNullable -> throw IllegalArgumentException("Couldn't inject null to property ${p.name}")
                else -> p.setter.call(instance, coerceType(p.returnType, value))
            }
            else -> {}
        }
    }

    private fun coerceType(type: KType, value: Any?): Any? =
        when {
            value == null -> null
            isListType(type) -> when {
                value !is List<*> && value is Iterable<*> -> coerceType(type, value.toList())
                value !is List<*> -> throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")

                else -> {
                    listOf(type.toJavaClass().kotlin, ArrayList::class)
                        .toTypedList<MutableList<*>>()
                        .filterAssignable(type)
                        .firstHasNoArgConstructor()
                        ?.callNoArgConstructor()
                        ?.apply { addAll(value) }
                        ?: throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")
                }
            }
            isSetType(type) -> when {
                value !is Set<*> && value is Iterable<*> -> coerceType(type, value.toSet())
                value !is Set<*> -> throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")

                else -> {
                    listOf(type.toJavaClass().kotlin, LinkedHashSet::class, HashSet::class, TreeSet::class)
                            .toTypedList<MutableSet<*>>()
                            .filterAssignable(type)
                            .firstHasNoArgConstructor()
                            ?.callNoArgConstructor()
                            ?.apply { addAll(value) }
                            ?: throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")
                }
            }
            isMapType(type) -> when {
                value !is Map<*, *> -> throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")

                else -> {
                    listOf(type.toJavaClass().kotlin, LinkedHashMap::class, HashMap::class, TreeMap::class, ConcurrentHashMap::class)
                            .toTypedList<MutableMap<*, *>>()
                            .filterAssignable(type)
                            .firstHasNoArgConstructor()
                            ?.callNoArgConstructor()
                            ?.apply { putAll(value) }
                            ?: throw IllegalArgumentException("Couldn't coerce type ${value.javaClass} to $type")
                }
            }
            type.toJavaClass() == Float::class.java && value is Number -> value.toFloat()
            else -> value
        }

    private fun <T: Any> List<KClass<*>>.toTypedList() = this as List<KClass<T>>

    private fun KType.toJavaClass() = javaType.toJavaClass()

    private fun Type.toJavaClass(): Class<*> =
        when (this) {
            is ParameterizedType -> this.rawType.toJavaClass()
            is Class<*> -> this
            else -> throw IllegalArgumentException("Bad type $this")
        }

    private fun <T: Any> List<KClass<T>>.filterAssignable(type: KType): List<KClass<T>> =
            filter { type.toJavaClass().isAssignableFrom(it.java) }

    private fun <T: Any> List<KClass<T>>.firstHasNoArgConstructor() =
            firstOrNull { it.constructors.any { it.parameters.isEmpty() } }

    private fun <T: Any> KClass<T>.callNoArgConstructor() = constructors.first { it.parameters.isEmpty() }.call()

    private fun deserializeValue(value: String): Any? =
            if (!value.startsWith("#")) throw IllegalArgumentException("Bad serialized value")
            else when (value.getOrNull(1)) {
                null, 'n' -> null
                'i' -> value.drop(2).toInt()
                'l' -> value.drop(2).toLong()
                'f' -> value.drop(2).toDouble()
                'b' -> when (value.getOrNull(2)) {
                    'o' -> when (value.getOrNull(3)) {
                        't' -> true
                        'f' -> false
                        else -> throw IllegalArgumentException("Unsupported bo-value ${value.take(4)}")
                    }
                    'd' -> BigDecimal(value.drop(3))
                    'i' -> BigInteger(value.drop(3))
                    else -> throw IllegalArgumentException("Unsupported b-type ${value.take(3)}")
                }
                'o' -> when (value.getOrNull(2)) {
                    'm' -> Optional.empty<Any?>()
                    'p' -> Optional.ofNullable(deserializeValue(value.drop(3)))
                    else -> throw IllegalArgumentException("Unsupported o-value ${value.take(3)}")
                }
                's' -> value.drop(2)
                'c' -> when (value.getOrNull(2)) {
                    'l' -> deserializeCollection(value.drop(3))
                    's' -> deserializeCollection(value.drop(3)).toSet()
                    'h' -> value.drop(3).first()
                    else -> throw IllegalArgumentException("Unsupported c-type ${value.take(3)}")
                }
                'm' -> deserializeMap(value.drop(2))
                else -> throw IllegalArgumentException("Unsupported type ${value.take(2)}")
            }

    private fun serializeValue(value: Any?): String =
            when (value) {
                null -> "#n"
                is Int -> "#i$value"
                is Long -> "#l$value"
                is Float -> "#f$value"
                is Double -> "#f$value"
                is Boolean -> "#bo${value.toString().first()}"
                is Char -> "#ch$value"
                is BigDecimal -> "#bd${value.toString()}"
                is BigInteger -> "#bi${value.toString()}"
                is Optional<*> -> when {
                    value.isPresent -> "#op${serializeValue(value.get())}"
                    else -> "#om"
                }
                is String -> "#s$value"
                is List<*> -> "#cl${serializeCollection(value)}"
                is Set<*> -> "#cs${serializeCollection(value)}"
                is Map<*, *> -> "#m${serializeMap(value)}"
                else -> throw IllegalArgumentException("Unsupported value type ${value.javaClass.name}")
            }

    private fun deserializeCollection(value: String): List<*> = value.decodeURL().split("&").map { deserializeValue(it.decodeURL()) }
    private fun serializeCollection(value: Collection<*>): String = value.map { serializeValue(it).encodeURL() }.joinToString("&").encodeURL()

    private fun deserializeMap(value: String): Map<*, *> = value.decodeURL().split("&").toMap(
            { deserializeValue(it.substringBefore('=').decodeURL()) },
            { deserializeValue(it.substringAfter('=').decodeURL()) }
    )
    private fun serializeMap(value: Map<*, *>): String = value.map { serializeValue(it.key).encodeURL() + "=" + serializeValue(it.value).encodeURL() }.joinToString("&").encodeURL()

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isListType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.List::class.java.isAssignableFrom(it) } ?: false
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isSetType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.Set::class.java.isAssignableFrom(it) } ?: false
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun isMapType(type: KType): Boolean {
        return getRawType(type)?.let { java.util.Map::class.java.isAssignableFrom(it) } ?: false
    }

    private fun getRawType(type: KType): Class<*>? =
            type.javaType.let { javaType ->
                if (javaType is ParameterizedType) {
                    javaType.rawType as? Class<*>
                } else if (javaType is Class<*>) {
                    javaType
                } else null
            }
}
