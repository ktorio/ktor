/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SwaggerTest {
    @Test
    fun testSwaggerFromResources() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>

            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerAllowDeepLinking() = testApplication {
        routing {
            swaggerUI("swagger") {
                deepLinking = true
            }
        }

        val response = client.get("/swagger").bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: true,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFromResourcesWithDocExpansion() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "list")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/favicon-32x32.png" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout',
                    docExpansion: 'list'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFileIsServed() = testApplication {
        routing {
            swaggerUI("openapi")
        }

        val response = client.get("/openapi/documentation.yaml")
        val body = response.bodyAsText()
        assertEquals("application/yaml", response.contentType().toString())
        assertEquals("hello:\n  world".filter { it.isLetterOrDigit() }, body.filter { it.isLetterOrDigit() })
    }

    @Test
    fun testCustomFavicon() = testApplication {
        routing {
            swaggerUI("swagger") {
                faviconLocation = "https://www.google.com/favicon.ico"
            }
        }

        val response = client.get("/swagger") {
            parameter("docExpansion", "list")
        }.bodyAsText()
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <title>Swagger UI</title>
                <link href="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui.css" rel="stylesheet">
                <link href="https://www.google.com/favicon.ico" rel="icon" type="image/x-icon">
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-bundle.js" crossorigin="anonymous"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.17.12/swagger-ui-standalone-preset.js" crossorigin="anonymous"></script>
                <script>window.onload = function() {
                window.ui = SwaggerUIBundle({
                    url: '/swagger/documentation.yaml',
                    dom_id: '#swagger-ui',
                    deepLinking: false,
                    oauth2RedirectUrl: window.location.origin + '/swagger/oauth2-redirect.html',
                    presets: [
                        SwaggerUIBundle.presets.apis,
                        SwaggerUIStandalonePreset
                    ],
                    layout: 'StandaloneLayout',
                    docExpansion: 'list'
                });
            }</script>
              </body>
            </html>
            
            """.trimIndent(),
            response
        )
    }

    @Test
    fun testSwaggerFromResources2() = testApplication {
        routing {
            swaggerUI("swagger")
        }

        val response = client.get("/swagger/oauth2-redirect.html").bodyAsText()
        assertEquals(
            """<!DOCTYPE html>
<html>
  <head>
    <title>Swagger UI: OAuth2 Redirect</title>
  </head>
  <body>
    <script>'use strict';
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
})();</script>
  </body>
</html>

            """.trimIndent(),
            response
        )
    }
}
