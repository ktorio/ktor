package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import kotlinx.coroutines.*
import java.security.*

@Deprecated(
    "Use DSL builder form",
    replaceWith = ReplaceWith("basic { this.realm = realm\n validate(validate)}"),
    level = DeprecationLevel.ERROR
)
fun Authentication.Configuration.basicAuthentication(realm: String, validate: suspend (UserPasswordCredential) -> Principal?) {
    basic {
        this.realm = realm
        validate { validate(it) }
    }
}


@Deprecated("Use DSL builder form", replaceWith = ReplaceWith("digest {\n" +
        "        this.realm = realm\n" +
        "        this.digester = digesterProvider(digestAlgorithm)\n" +
        "        this.userNameRealmPasswordDigestProvider = userNameRealmPasswordDigestProvider\n" +
        "    }"), level = DeprecationLevel.ERROR)
fun Authentication.Configuration.digestAuthentication(
        realm: String = "ktor",
        digestAlgorithm: String = "MD5",
        digesterProvider: (String) -> MessageDigest = { MessageDigest.getInstance(it) },
        userNameRealmPasswordDigestProvider: suspend (String, String) -> ByteArray?) {
    digest {
        this.realm = realm
        this.digester = digesterProvider(digestAlgorithm)
        this.userNameRealmPasswordDigestProvider = userNameRealmPasswordDigestProvider
    }

}


@Deprecated("Use DSL builder form", replaceWith = ReplaceWith("form {\n" +
        "        this.userParamName = userParamName\n" +
        "        this.passwordParamName = passwordParamName\n" +
        "        this.challenge = challenge\n" +
        "        this.validate(validate)\n" +
        "    }"), level = DeprecationLevel.ERROR)
fun Authentication.Configuration.formAuthentication(userParamName: String = "user",
                                                    passwordParamName: String = "password",
                                                    challenge: FormAuthChallenge = FormAuthChallenge.Unauthorized,
                                                    validate: suspend (UserPasswordCredential) -> Principal?) {
    form {
        this.userParamName = userParamName
        this.passwordParamName = passwordParamName
        this.challenge = challenge
        this.validate { validate(it) }
    }

}

@Deprecated("Use DSL builder form", replaceWith = ReplaceWith("oauth {\n" +
        "        this.client = client\n" +
        "        this.providerLookup = providerLookup\n" +
        "        this.urlProvider = urlProvider\n" +
        "    }"), level = DeprecationLevel.ERROR)
fun Authentication.Configuration.oauth(client: HttpClient, @Suppress("UNUSED_PARAMETER") dispatcher: CoroutineDispatcher,
                                       providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                       urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    oauth {
        this.client = client
        this.providerLookup = providerLookup
        this.urlProvider = urlProvider
    }
}

