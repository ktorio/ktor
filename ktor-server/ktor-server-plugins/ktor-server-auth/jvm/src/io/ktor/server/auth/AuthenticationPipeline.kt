/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

/**
 * Represents authentication [Pipeline] for checking and requesting authentication
 */
public class AuthenticationPipeline(
    override val developmentMode: Boolean = false
) : Pipeline<AuthenticationContext, ApplicationCall>(CheckAuthentication, RequestAuthentication) {

    public companion object {
        /**
         * Phase for checking if user is already authenticated before all mechanisms kicks in
         */
        public val CheckAuthentication: PipelinePhase = PipelinePhase("CheckAuthentication")

        /**
         * Phase for authentications mechanisms to plug into
         */
        public val RequestAuthentication: PipelinePhase = PipelinePhase("RequestAuthentication")
    }
}
