/*
 *  Copyright 2018 Curity AB
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

package io.curity.identityserver.plugin.frejaeid.authentication

import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import se.curity.identityserver.sdk.authentication.AuthenticatedState
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.http.HttpStatus
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.ResponseModel
import java.util.*
import kotlin.collections.HashMap

class StartRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig,
                          private val authenticatedState: AuthenticatedState)
    : AuthenticatorRequestHandler<RequestModel> {

    override fun preProcess(request: Request, response: Response): RequestModel {
        if (request.isGetRequest) {
            // GET request

            val dataMap: HashMap<String, String> = HashMap(2)
            dataMap["userInfoType"] = config.userInfoType.toString().toLowerCase()
            if (config.userPreferencesManager.username != null) {
                if (config.userInfoType.equals(UserInfoType.USERNAME)) {
                    dataMap["username"] = config.userPreferencesManager.username
                } else {
                    dataMap["email"] = config.userPreferencesManager.username
                }
                response.setResponseModel(ResponseModel.templateResponseModel(dataMap as Map<String, Any>?, "authenticate/get"),
                        Response.ResponseModelScope.NOT_FAILURE)
            }
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap<String, Any>(),
                "authenticate/get"), HttpStatus.BAD_REQUEST)
        return RequestModel(request)
    }

    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            if (authenticatedState.isAuthenticated == true) {
                startAuthentication(requestModel, response)
            } else {
                Optional.empty()
            }

    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            startAuthentication(requestModel, response)

    private fun startAuthentication(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> {
        TODO("""
            1. Start authentication at Freja.
            2. Save the resulting authentication transaction ID in the session.
            3. Return HTML that:
                A. Informs the user to login on their Freja mobile device
                B. Has some JavaScript that polls the /wait endpoint using GET (or head)
                C. A form that is POSTed by the poller to the /wait endpoint when the poller is informed that auth is done
                """)

        return Optional.empty()
    }
}