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

import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import java.util.Optional

class WaitRequestModel(request : Request)

class WaitRequestHandler : AuthenticatorRequestHandler<WaitRequestModel>
{
    override fun get(requestModel: WaitRequestModel, response: Response): Optional<AuthenticationResult>
    {
        TODO("poll Freja using the auth request transaction ID from the session")
    }
    
    override fun preProcess(request: Request, response: Response) = WaitRequestModel(request)
    
    override fun post(requestModel: WaitRequestModel, response: Response): Optional<AuthenticationResult>
    {
        TODO("polling is done and the form was submitted")
    }
}