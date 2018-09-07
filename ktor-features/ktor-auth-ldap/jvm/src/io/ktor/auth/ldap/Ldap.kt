package io.ktor.auth.ldap

import io.ktor.auth.*
import io.ktor.util.*
import java.util.*
import javax.naming.*
import javax.naming.directory.*

/**
 * Do LDAP authentication and verify [credential] by [doVerify] function
 */
@KtorExperimentalAPI
fun <K : Credential, P : Any> ldapAuthenticate(
    credential: K,
    ldapServerURL: String,
    ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit = {},
    doVerify: InitialDirContext.(K) -> P?
): P? {
    return try {
        val root = ldapLogin(ldapServerURL, ldapEnvironmentBuilder)
        try {
            root.doVerify(credential)
        } finally {
            root.close()
        }
    } catch (ne: NamingException) {
        null
    }
}

/**
 * Do LDAP authentication and verify [UserPasswordCredential] by [validate] function and construct [UserIdPrincipal]
 */
fun ldapAuthenticate(
    credential: UserPasswordCredential,
    ldapServerURL: String,
    userDNFormat: String,
    validate: InitialDirContext.(UserPasswordCredential) -> UserIdPrincipal?
): UserIdPrincipal? {

    val configurator: (MutableMap<String, Any?>) -> Unit = { env ->
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(credential.name)
        env[Context.SECURITY_CREDENTIALS] = credential.password
    }

    return ldapAuthenticate(credential, ldapServerURL, configurator, validate)
}

/**
 * Do LDAP authentication and verify [UserPasswordCredential] by [userDNFormat] and construct [UserIdPrincipal]
 */
fun ldapAuthenticate(
    credential: UserPasswordCredential,
    ldapServerURL: String,
    userDNFormat: String
): UserIdPrincipal? {
    return ldapAuthenticate(credential, ldapServerURL, userDNFormat) { UserIdPrincipal(it.name) }
}

private fun ldapLogin(ldapURL: String, ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit): InitialDirContext {
    val env = Hashtable<String, Any?>()
    env[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.ldap.LdapCtxFactory"
    env[Context.PROVIDER_URL] = ldapURL

    ldapEnvironmentBuilder(env)

    return InitialDirContext(env)
}


