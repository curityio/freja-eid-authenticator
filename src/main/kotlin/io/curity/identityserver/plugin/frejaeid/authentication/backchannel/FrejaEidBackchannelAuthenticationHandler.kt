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

package io.curity.identityserver.plugin.frejaeid.authentication.backchannel

import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_AUTH_REF
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_SIGN_REF
import io.curity.identityserver.plugin.frejaeid.authentication.RequestLogicHelper
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.attribute.AuthenticationAttributes
import se.curity.identityserver.sdk.attribute.ContextAttributes
import se.curity.identityserver.sdk.attribute.SubjectAttributes
import se.curity.identityserver.sdk.authentication.BackchannelAuthenticationHandler
import se.curity.identityserver.sdk.authentication.BackchannelAuthenticationRequest
import se.curity.identityserver.sdk.authentication.BackchannelAuthenticationResult
import se.curity.identityserver.sdk.authentication.BackchannelAuthenticatorState
import se.curity.identityserver.sdk.authentication.BackchannelStartAuthenticationResult
import se.curity.identityserver.sdk.errors.ErrorCode
import java.util.Optional

class FrejaEidBackchannelAuthenticationHandler(private val config: FrejaEidAuthenticatorPluginConfig)
    : BackchannelAuthenticationHandler
{
    private val _logger: Logger = LoggerFactory.getLogger(FrejaEidBackchannelAuthenticationHandler::class.java)

    private val _requestLogicHelper = RequestLogicHelper(config)
    private val _userInfoType = config.userInfoType
    private val _sessionManager = config.sessionManager

    override fun startAuthentication(authReqId: String, authRequest: BackchannelAuthenticationRequest):
            BackchannelStartAuthenticationResult
    {
        _logger.debug("Starting Freja eid backchannel authentication for ${authRequest.subject} with $authReqId")

        // the requested subject must match the _userInfoType, else Freja would respond with error
        val postData = _requestLogicHelper.createPostData(_userInfoType, authRequest.subject).toMutableMap()

        return try
        {
            when
            {
                // when binding message is present, use sign method instead of authenticate
                authRequest.bindingMessage != null -> {
                    if (requestSignature(authReqId, authRequest.bindingMessage, postData)) {
                        BackchannelStartAuthenticationResult.ok()
                    }
                    else {
                        BackchannelStartAuthenticationResult.error("server_error",
                                "Failed Freja signature request")
                    }
                }

                else -> {
                    if (requestAuthentication(authReqId, postData)) {
                        BackchannelStartAuthenticationResult.ok()
                    }
                    else {
                        BackchannelStartAuthenticationResult.error("server_error",
                                "Failed Freja authentication request")
                    }
                }
            }
        }
        catch (e: Exception)
        {
            _logger.debug("Freja backchannel authentication not started for $authReqId. ${e.message}")
            BackchannelStartAuthenticationResult.error("server_error",
                    "Failed Freja request")
        }
    }

    private fun requestAuthentication(authReqId: String, postData: Map<String, Any>): Boolean
    {
        val responseData = _requestLogicHelper.requestAuthentication(postData)
        val authRef = responseData["authRef"]?.toString()
        if (authRef != null)
        {
            _logger.trace("Freja eid authentication returned $authRef for authReqId : $authReqId")
            config.sessionManager.put(Attribute.of(SESSION_AUTH_REF, authRef))
            return true
        }
        _logger.debug("Freja eid authentication request failed. $responseData")
        return false
    }

    private fun requestSignature(authReqId: String, bindingMessage: String, postData: Map<String, Any>): Boolean
    {
        val postDataWithSignableText = _requestLogicHelper.addSignTextToPostData(bindingMessage, postData)
        val responseData = _requestLogicHelper.requestSignature(postDataWithSignableText)
        val signRef = responseData["signRef"]?.toString()
        if (signRef != null)
        {
            _logger.trace("Freja eid sign request returned $signRef for authReqId : $authReqId")
            config.sessionManager.put(Attribute.of(SESSION_SIGN_REF, signRef))
            return true
        }
        _logger.debug("Freja eid sign request failed. $responseData")
        return false
    }

    override fun checkAuthenticationStatus(authReqId: String): Optional<BackchannelAuthenticationResult>
    {
        _logger.debug("Checking Freja eid backchannel authentication status for $authReqId")
        val authRefAttribute = _sessionManager.get(SESSION_AUTH_REF)

        val responseData = when
        {
            authRefAttribute != null -> _requestLogicHelper.checkAuthenticationStatus(authRefAttribute)
            else ->
            {
                val signRefAttribute = _sessionManager.get(SESSION_SIGN_REF)
                _requestLogicHelper.checkSignatureStatus(signRefAttribute)
            }
        }
        _logger.trace("Authentication service response : $responseData")

        return Optional.of(createBackchannelAuthenticationResult(responseData))
    }

    override fun cancelAuthenticationRequest(authReqId: String)
    {
        _logger.debug("Canceling Freja eid authentication for $authReqId")
        val authRefAttribute: Attribute? = _sessionManager.get(SESSION_AUTH_REF)

        if (authRefAttribute != null)
        {
            _requestLogicHelper.cancelAuthenticationRequest(authRefAttribute)
            config.sessionManager.remove(SESSION_AUTH_REF)
        }
        else
        {
            val signRefAttribute: Attribute? = _sessionManager.get(SESSION_SIGN_REF)
            signRefAttribute?.let { _requestLogicHelper.cancelSignRequest(it) }
                    ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                            "authRef cannot be null")
            config.sessionManager.remove(SESSION_SIGN_REF)
        }
    }

    private fun createBackchannelAuthenticationResult(responseData: Map<String, Any>)
            : BackchannelAuthenticationResult
    {
        return when (responseData["status"])
        {
            // Reference : https://frejaeid.com/rest-api/Authentication%20Service.html
            "APPROVED" ->
            {
                val authnAttributes = extractAuthnAttributesFromSuccessResponse(responseData)
                BackchannelAuthenticationResult(authnAttributes, BackchannelAuthenticatorState.SUCCEEDED)
            }
            "STARTED", "DELIVERED_TO_MOBILE" ->
            {
                BackchannelAuthenticationResult(null, BackchannelAuthenticatorState.STARTED)
            }
            "EXPIRED", "RP_CANCELED" ->
            {
                BackchannelAuthenticationResult(null, BackchannelAuthenticatorState.EXPIRED)
            }
            "REJECTED", "CANCELED" ->
            {
                BackchannelAuthenticationResult(null, BackchannelAuthenticatorState.FAILED)
            }
            else ->
            {
                _logger.info("Unknown status received : ${responseData["status"]}")
                BackchannelAuthenticationResult(null, BackchannelAuthenticatorState.UNKNOWN)
            }
        }
    }

    private fun extractAuthnAttributesFromSuccessResponse(successResponseData: Map<String, Any>)
            : AuthenticationAttributes
    {
        val claimsMap = _requestLogicHelper.extractAttributesFromJwt(successResponseData)
        _logger.trace("Attributes received in response : $claimsMap")

        val subject = claimsMap.getMandatoryValue("userInfo", String::class.java)

        return AuthenticationAttributes.of(
                SubjectAttributes.of(subject, claimsMap),
                ContextAttributes.empty())
    }
}