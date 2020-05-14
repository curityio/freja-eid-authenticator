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

package io.curity.identityserver.plugin.frejaeid.config;

import se.curity.identityserver.sdk.config.Configuration
import se.curity.identityserver.sdk.config.annotation.DefaultBoolean
import se.curity.identityserver.sdk.config.annotation.Description
import se.curity.identityserver.sdk.config.annotation.Name
import se.curity.identityserver.sdk.service.*
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider
import java.util.*

interface FrejaEidAuthenticatorPluginConfig : Configuration
{
    @get:Description("The HTTP client with any proxy and TLS settings")
    val httpClient: Optional<HttpClient>
    
    @get:Description("The environment to use for authentication")
    val environment: PredefinedEnvironment

    @get:Description("Minimum required registration level of the user")
    val minimumRegistrationLevel: RegistrationLevel
    
    @get:Description("User Identifier Type")
    val userInfoType: UserInfoType

    @get:Description("The relying party ID")
    val relyingPartyId : Optional<String>

    @Name("qr-code-enabled")
    @Description("QRCode can be used to authenticate")
    @DefaultBoolean(false)
    fun qrCodeEnabled(): Boolean
    
    val sessionManager: SessionManager
    
    val exceptionFactory: ExceptionFactory
    
    val userPreferencesManager: UserPreferenceManager
    
    val webServiceClientFactory: WebServiceClientFactory
    
    val json: Json
    
    val authenticatorInformationProvider: AuthenticatorInformationProvider

    val attributesToReturn: List<AttributesToReturn>
}

enum class PredefinedEnvironment
{
    @Description("Non-production environment for testing and verification")
    PRE_PRODUCTION,
    
    @Description("The production environment should be use")
    PRODUCTION;
    
    fun getHost(): String
    {
        return when (this)
        {
            PRE_PRODUCTION -> "services.test.frejaeid.com"
            PRODUCTION     -> "services.prod.frejaeid.com"
        }
    }
}

enum class AttributesToReturn
{
    BASIC_USER_INFO,
    EMAIL_ADDRESS,
    DATE_OF_BIRTH,
    SSN,
    CUSTOM_IDENTIFIER,
    RELYING_PARTY_USER_ID,
    INTEGRATOR_SPECIFIC_USER_ID
}

enum class RegistrationLevel
{
    BASIC,
    EXTENDED,
    PLUS
}

enum class UserInfoType
{
    EMAIL,
    SSN,
    PHONE
}
