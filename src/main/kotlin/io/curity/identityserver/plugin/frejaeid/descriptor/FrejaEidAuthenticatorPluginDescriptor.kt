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

package io.curity.identityserver.plugin.frejaeid.descriptor

import io.curity.identityserver.plugin.frejaeid.authentication.*
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import se.curity.identityserver.sdk.haapi.RepresentationFunction
import se.curity.identityserver.sdk.plugin.descriptor.AuthenticatorPluginDescriptor
import se.curity.identityserver.sdk.plugin.descriptor.CanSupportHaapi

class FrejaEidAuthenticatorPluginDescriptor : AuthenticatorPluginDescriptor<FrejaEidAuthenticatorPluginConfig>, CanSupportHaapi
{
    override fun getAuthenticationRequestHandlerTypes():
            Map<String, Class<out se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler<*>>> =
            mapOf("index" to StartRequestHandler::class.java,
                    "wait" to WaitRequestHandler::class.java)

    override fun getConfigurationType(): Class<out FrejaEidAuthenticatorPluginConfig> =
            FrejaEidAuthenticatorPluginConfig::class.java

    override fun getPluginImplementationType(): String = "freja-eid"
    override fun getRepresentationFunctions(): Map<String, Class<out RepresentationFunction>>
    {
        return mapOf("authenticate/get" to GetRepresentationFunction::class.java,
                "authenticate/error" to ErrorRepresentationFunction::class.java,
                "authenticate/wait" to WaitRepresentationFunction::class.java)
    }
}