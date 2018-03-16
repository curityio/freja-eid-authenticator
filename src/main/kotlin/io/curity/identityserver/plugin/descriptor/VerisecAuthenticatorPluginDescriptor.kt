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