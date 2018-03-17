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

package io.curity.identityserver.plugin.authentication

import io.curity.identityserver.plugin.config.FrejaEidAuthenticatorPluginConfig
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import java.util.Optional

class RequestModel(request: Request)

class StartRequestHandler(config : FrejaEidAuthenticatorPluginConfig)
    : AuthenticatorRequestHandler<RequestModel>
{
    override fun preProcess(request: Request, response: Response): RequestModel = RequestModel(request)
    
    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        TODO("show username form unless the authenticated state indicates that the user has already logged " +
                "in (e.g., by a previous authenticator. If the user is already logged in, start authentication " +
                "at Freja")
    }
    
    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            startAuthentication(requestModel, response)
            
    private fun startAuthentication(requestModel: RequestModel, response: Response) : Optional<AuthenticationResult>
    {
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