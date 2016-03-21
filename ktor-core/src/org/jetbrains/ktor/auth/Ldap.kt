package org.jetbrains.ktor.auth.ldap

import com.sun.jndi.ldap.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.interception.*
import java.util.*
import javax.naming.*
import javax.naming.directory.*

fun <K : Credential, P : Any> ldapAuthenticate(credential: K,
                                               ldapServerURL: String,
                                               ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit = {},
                                               doVerify: InitialDirContext.(K) -> P?): P? {
    try {
        val root = ldapLogin(ldapServerURL, ldapEnvironmentBuilder)
        try {
            return root.doVerify(credential)
        } finally {
            root.close()
        }
    } catch (ne: NamingException) {
        return null
    }
}

fun ldapAuthenticate(credential: UserPasswordCredential,
                     ldapServerURL: String,
                     userDNFormat: String): UserIdPrincipal? {

    val configurator: (MutableMap<String, Any?>) -> Unit = { env ->
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(credential.name)
        env[Context.SECURITY_CREDENTIALS] = credential.password
    }

    return ldapAuthenticate(credential, ldapServerURL, configurator) {
        UserIdPrincipal(it.name)
    }
}

private fun ldapLogin(ldapURL: String, ldapEnvironmentBuilder: (MutableMap<String, Any?>) -> Unit): InitialDirContext {
    val env = Hashtable<String, Any?>()
    env.put(Context.INITIAL_CONTEXT_FACTORY, LdapCtxFactory::class.qualifiedName!!);
    env.put(Context.PROVIDER_URL, ldapURL);

    ldapEnvironmentBuilder(env)

    return InitialDirContext(env)
}

inline fun <reified K : Credential, reified P : Principal> InterceptApplicationCall.verifyWithLdap(
        ldapUrl: String,
        noinline ldapLoginConfigurator: (K, MutableMap<String, Any?>) -> Unit = { k, env -> },
        noinline verifyBlock: InitialDirContext.(K) -> P?
) {
    intercept { call ->
        val auth = call.authentication
        val principals = auth.credentials<K>().mapNotNull { cred ->
            ldapAuthenticate(cred, ldapUrl, { config -> ldapLoginConfigurator(cred, config) }, verifyBlock)
        }
        auth.addPrincipals(principals)
    }
}

inline fun <reified K : Credential, reified P : Principal> InterceptApplicationCall.verifyWithLdapLoginWithUser(
        ldapUrl: String,
        userDNFormat: String,
        noinline userNameExtractor: (K) -> String,
        noinline userPasswordExtractor: (K) -> String,
        noinline ldapLoginConfigurator: (K, MutableMap<String, Any?>) -> Unit = { k, env -> },
        noinline verifyBlock: InitialDirContext.(K) -> P?
) {

    val configurator: (K, MutableMap<String, Any?>) -> Unit = { credentials, env ->
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(userNameExtractor(credentials))
        env[Context.SECURITY_CREDENTIALS] = userPasswordExtractor(credentials)

        ldapLoginConfigurator(credentials, env)
    }

    verifyWithLdap(ldapUrl, configurator, verifyBlock)
}

fun InterceptApplicationCall.verifyWithLdapLoginWithUser(
        ldapUrl: String,
        userDNFormat: String,
        ldapLoginConfigurator: (UserPasswordCredential, MutableMap<String, Any?>) -> Unit = { cred, env -> },
        verifyBlock: InitialDirContext.(UserPasswordCredential) -> Boolean = { true }
) {
    verifyWithLdapLoginWithUser(ldapUrl,
            userDNFormat,
            { it.name }, { it.password },
            ldapLoginConfigurator,
            verifyBlock = { cred ->
                if (verifyBlock(cred)) {
                    UserIdPrincipal(cred.name)
                } else {
                    null
                }
            })
}


