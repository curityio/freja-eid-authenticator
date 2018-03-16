package io.curity.identityserver.plugin.descriptor

import io.curity.identityserver.plugin.authentication.VerisecAuthenticatorRequestHandler
import io.curity.identityserver.plugin.config.VerisecAuthenticatorPluginConfig
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.plugin.descriptor.AuthenticatorPluginDescriptor

class VerisecAuthenticatorPluginDescriptor : AuthenticatorPluginDescriptor<VerisecAuthenticatorPluginConfig>
{
    override fun getAuthenticationRequestHandlerTypes(): Map<String, Class<out AuthenticatorRequestHandler<*>>> =
            mapOf("index" to VerisecAuthenticatorRequestHandler::class.java)
    
    override fun getConfigurationType(): Class<out VerisecAuthenticatorPluginConfig> =
        VerisecAuthenticatorPluginConfig::class.java
    
    override fun getPluginImplementationType(): String = "verisec"
}