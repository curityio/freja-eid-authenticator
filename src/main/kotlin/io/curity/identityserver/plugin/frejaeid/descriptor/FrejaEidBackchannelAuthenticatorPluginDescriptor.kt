/*
 *  Copyright 2021 Curity AB
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

import se.curity.identityserver.sdk.plugin.descriptor.BackchannelAuthenticatorPluginDescriptor
import io.curity.identityserver.plugin.frejaeid.descriptor.FrejaEidBackchannelAuthenticatorPluginDescriptor.EmptyConfig
import se.curity.identityserver.sdk.authentication.BackchannelAuthenticationHandler
import io.curity.identityserver.plugin.frejaeid.authentication.backchannel.FrejaEidBackchannelAuthenticationHandler
import se.curity.identityserver.sdk.plugin.descriptor.AuthenticatorPluginDescriptor
import se.curity.identityserver.sdk.config.Configuration

class FrejaEidBackchannelAuthenticatorPluginDescriptor : BackchannelAuthenticatorPluginDescriptor<EmptyConfig>
{
    override fun getPluginImplementationType(): String = "freja-eid-backchannel"

    override fun getBackchannelAuthenticationHandlerType(): Class<out BackchannelAuthenticationHandler?> =
            FrejaEidBackchannelAuthenticationHandler::class.java

    override fun getFrontchannelPluginDescriptorReference(): Class<out AuthenticatorPluginDescriptor<*>?> =
            FrejaEidAuthenticatorPluginDescriptor::class.java

    // this backchannel plugin reuses the frontchannel authenticator's configuration
    override fun getConfigurationType(): Class<EmptyConfig> = EmptyConfig::class.java

    interface EmptyConfig : Configuration
}