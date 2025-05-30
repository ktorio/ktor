/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.html.*
import java.io.*

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the OpenAPI file located at [swaggerFile].
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 */
public fun Route.swaggerUI(
    path: String,
    swaggerFile: String = "openapi/documentation.yaml",
    block: SwaggerConfig.() -> Unit = {}
) {
    val resource = environment.classLoader.getResourceAsStream(swaggerFile)
        ?.bufferedReader()

    if (resource != null) {
        swaggerUI(path, swaggerFile.takeLastWhile { it != '/' }, resource.readText(), block)
        return
    }

    swaggerUI(path, File(swaggerFile), block)
}

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the [apiFile].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 */
public fun Route.swaggerUI(path: String, apiFile: File, block: SwaggerConfig.() -> Unit = {}) {
    if (!apiFile.exists()) {
        throw FileNotFoundException("Swagger file not found: ${apiFile.absolutePath}")
    }

    val content = apiFile.readText()
    swaggerUI(path, apiFile.name, content, block)
}

/**
 * Configures a route to serve Swagger UI and its corresponding API specification.
 *
 * This function sets up a given path to serve a Swagger UI interface based on the provided API specification.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 *
 * @param path The base path where the Swagger UI will be accessible.
 * @param apiUrl The relative URL for the Swagger API JSON file.
 * @param api The content of the Swagger API specification.
 * @param block A configuration block to apply additional Swagger configuration settings.
 */
public fun Route.swaggerUI(
    path: String,
    apiUrl: String,
    api: String,
    block: SwaggerConfig.() -> Unit = {}
) {
    val config = SwaggerConfig().apply(block)

    route(path) {
        get(apiUrl) {
            call.respondText(api, ContentType.fromFilePath(apiUrl).firstOrNull())
        }
        get("oauth2-redirect.html") {
            call.respondHtml {
                head {
                    title { +"Swagger UI: OAuth2 Redirect" }
                }
                body {
                    script {
                        unsafe {
                            +"""
'use strict';
(function () {
    function run () {
        var oauth2 = window.opener.swaggerUIRedirectOauth2;
        var sentState = oauth2.state;
        var redirectUrl = oauth2.redirectUrl;
        var isValid, qp, arr;

        if (/code|token|error/.test(window.location.hash)) {
            qp = window.location.hash.substring(1).replace('?', '&');
        } else {
            qp = location.search.substring(1);
        }

        arr = qp.split("&")
        arr.forEach(function (v,i,_arr) { _arr[i] = '"' + v.replace('=', '":"') + '"';})
        qp = qp ? JSON.parse('{' + arr.join() + '}',
                function (key, value) {
                    return key === "" ? value : decodeURIComponent(value)
                }
        ) : {}

        isValid = qp.state === sentState

        if ((
          oauth2.auth.schema.get("flow") === "accessCode" ||
          oauth2.auth.schema.get("flow") === "authorizationCode" ||
          oauth2.auth.schema.get("flow") === "authorization_code"
        ) && !oauth2.auth.code) {
            if (!isValid) {
                oauth2.errCb({
                    authId: oauth2.auth.name,
                    source: "auth",
                    level: "warning",
                    message: "Authorization may be unsafe, passed state was changed in server Passed state wasn't returned from auth server"
                });
            }

            if (qp.code) {
                delete oauth2.state;
                oauth2.auth.code = qp.code;
                oauth2.callback({auth: oauth2.auth, redirectUrl: redirectUrl});
            } else {
                let oauthErrorMsg
                if (qp.error) {
                    oauthErrorMsg = "["+qp.error+"]: " +
                        (qp.error_description ? qp.error_description+ ". " : "no accessCode received from the server. ") +
                        (qp.error_uri ? "More info: "+qp.error_uri : "");
                }

                oauth2.errCb({
                    authId: oauth2.auth.name,
                    source: "auth",
                    level: "error",
                    message: oauthErrorMsg || "[Authorization failed]: no accessCode received from the server"
                });
            }
        } else {
            oauth2.callback({auth: oauth2.auth, token: qp, isValid: isValid, redirectUrl: redirectUrl});
        }
        window.close();
    }

    if( document.readyState !== 'loading' ) {
      run();
    } else {
      document.addEventListener('DOMContentLoaded', function () {
        run();
      });
    }
})();
                            """.trimIndent()
                        }
                    }
                }
            }
        }
        get {
            val fullPath = call.request.path()
            val docExpansion = runCatching {
                call.request.queryParameters.getOrFail<String>("docExpansion")
            }.getOrNull()
            call.respondHtml {
                head {
                    title { +"Swagger UI" }
                    link(
                        href = "${config.packageLocation}@${config.version}/swagger-ui.css",
                        rel = "stylesheet"
                    )
                    config.customStyle?.let {
                        link(href = it, rel = "stylesheet")
                    }
                    link(
                        href = config.faviconLocation,
                        rel = "icon",
                        type = "image/x-icon"
                    )
                }
                body {
                    div { id = "swagger-ui" }
                    script(src = "${config.packageLocation}@${config.version}/swagger-ui-bundle.js") {
                        attributes["crossorigin"] = "anonymous"
                    }

                    val src = "${config.packageLocation}@${config.version}/swagger-ui-standalone-preset.js"
                    script(src = src) {
                        attributes["crossorigin"] = "anonymous"
                    }

                    script {
                        unsafe {
                            +"""
window.onload = function() {
    window.ui = SwaggerUIBundle({
        url: '$fullPath/$apiUrl',
        dom_id: '#swagger-ui',
        deepLinking: ${config.deepLinking},
        oauth2RedirectUrl: window.location.origin + '$fullPath/oauth2-redirect.html',
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ],
        layout: 'StandaloneLayout'${docExpansion?.let { ",\n        docExpansion: '$it'" } ?: ""}
    });
}
                            """.trimIndent()
                        }
                    }
                }
            }
        }
    }
}
