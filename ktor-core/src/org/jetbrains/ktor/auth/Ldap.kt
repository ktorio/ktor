package org.jetbrains.ktor.auth.ldap

import com.sun.jndi.ldap.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import java.util.*
import javax.naming.*
import javax.naming.directory.*

fun <P : Any> ldapVerifyBase(ldapURL: String,
                             ldapLoginConfigurator: (MutableMap<String, Any?>) -> Unit = {},
                             doVerify: (InitialDirContext) -> P?): P? {
    try {
        val root = ldapLogin(ldapURL, ldapLoginConfigurator)
        try {
            return doVerify(root)
        } finally {
            root.close()
        }
    } catch (ne: NamingException) {
        return null
    }
}

private fun ldapLogin(ldapURL: String, ldapLoginConfigurator: (MutableMap<String, Any?>) -> Unit = {}): InitialDirContext {
    val env = Hashtable<String, Any?>()
    env.put(Context.INITIAL_CONTEXT_FACTORY, LdapCtxFactory::class.qualifiedName!!);
    env.put(Context.PROVIDER_URL, ldapURL);

    ldapLoginConfigurator(env)

    return InitialDirContext(env)
}

inline fun <C : ApplicationRequestContext, reified K : Credential, reified P : Principal> AuthBuilder<C>.verifyWithLdap(
        ldapUrl: String,
        noinline ldapLoginConfigurator: (K, MutableMap<String, Any?>) -> Unit,
        noinline verifyBlock: InitialDirContext.(K) -> P?
) {
    intercept { next ->
        val auth = AuthContext.from(this)
        auth.addPrincipals(auth.credentials<K>().map { cred ->
            ldapVerifyBase(ldapUrl,
                    ldapLoginConfigurator = { config -> ldapLoginConfigurator(cred, config) },
                    doVerify = { ctx -> ctx.verifyBlock(cred) })
        }.filterNotNull())

        next()
    }
}

inline fun <C : ApplicationRequestContext, reified K : Credential, reified P : Principal> AuthBuilder<C>.verifyWithLdapLoginWithUser(
        ldapUrl: String,
        userDNFormat: String,
        noinline userNameExtractor: (K) -> String,
        noinline userPasswordExtractor: (K) -> String,
        noinline ldapLoginConfigurator: (K, MutableMap<String, Any?>) -> Unit = { k, env -> },
        noinline verifyBlock: InitialDirContext.(K) -> P?
) {
    verifyWithLdap(ldapUrl,
            ldapLoginConfigurator = { credentials, env ->
                env[Context.SECURITY_AUTHENTICATION] = "simple"
                env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(userNameExtractor(credentials))
                env[Context.SECURITY_CREDENTIALS] = userPasswordExtractor(credentials)

                ldapLoginConfigurator(credentials, env)
            }, verifyBlock = verifyBlock)
}

fun <C : ApplicationRequestContext> AuthBuilder<C>.verifyWithLdapLoginWithUser(
        ldapUrl: String,
        userDNFormat: String,
        ldapLoginConfigurator: (UserPasswordCredential, MutableMap<String, Any?>) -> Unit,
        verifyBlock: InitialDirContext.(UserPasswordCredential) -> Boolean = { true }
) {
    verifyWithLdapLoginWithUser(ldapUrl, userDNFormat,
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


