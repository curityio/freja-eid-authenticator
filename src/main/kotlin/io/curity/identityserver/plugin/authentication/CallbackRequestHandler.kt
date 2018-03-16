/*
 *  Copyright 2017 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.plugin.authentication

import io.curity.identityserver.plugin.config.VerisecAuthenticatorPluginConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import java.util.*


class VerisecCallbackRequestHandler(config: VerisecAuthenticatorPluginConfig)
    : AuthenticatorRequestHandler<CallbackRequestModel> {
    private val exceptionFactory = config.exceptionFactory
    private val logger: Logger = LoggerFactory.getLogger(VerisecCallbackRequestHandler::class.java)

    init {

    }


    override fun get(requestModel: CallbackRequestModel, response: Response): Optional<AuthenticationResult> {
        throw exceptionFactory.methodNotAllowed()
    }

    override fun preProcess(request: Request, response: Response): CallbackRequestModel {
        return CallbackRequestModel(request)
    }

    override fun post(requestModel: CallbackRequestModel, response: Response): Optional<AuthenticationResult> {
        return Optional.empty();
    }
}