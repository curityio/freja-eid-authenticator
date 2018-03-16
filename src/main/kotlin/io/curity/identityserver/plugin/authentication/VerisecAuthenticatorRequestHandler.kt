package io.curity.identityserver.plugin.authentication

import io.curity.identityserver.plugin.config.VerisecAuthenticatorPluginConfig
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import java.util.Optional

class VerisecAuthenticatorRequestHandler(config : VerisecAuthenticatorPluginConfig)
    : AuthenticatorRequestHandler<RequestModel>
{
    override fun preProcess(request: Request, response: Response): RequestModel = RequestModel(request)
    
    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        TODO("not implemented")
    }
    
    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        TODO("not implemented")
    }
}