package org.jetbrains.ktor.http.auth.ldap

import com.sun.jndi.ldap.*
import org.jetbrains.ktor.http.auth.simple.*
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

fun ldapSimpleLoginVerify(ldapURL: String,
                          userDNFormat: String,
                          credentials: SimpleUserPassword,
                          ldapLoginConfigurator: (MutableMap<String, Any?>) -> Unit = {}): SimpleUserPrincipal? {

    return ldapVerifyBase(ldapURL, { env ->
        env[Context.SECURITY_AUTHENTICATION] = "simple"
        env[Context.SECURITY_PRINCIPAL] = userDNFormat.format(credentials.name)
        env[Context.SECURITY_CREDENTIALS] = credentials.password

        ldapLoginConfigurator(env)
    },
            { SimpleUserPrincipal(credentials.name) })
}
