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

import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.CSP_OVERRIDE_IMG_SRC
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.QR_CODE
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.QR_CODE_GENERATE_URL_PROD
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.QR_CODE_GENERATE_URL_TEST
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_AUTH_REF
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_COUNTRY
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_CUSTOM_IDENTIFIER
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_DATE_OF_BIRTH
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_EMAIL
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_INTEGRATOR_SPECIFIC_USER_ID
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_NAME
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_RP_USER_ID
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_SSN
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_STATUS
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_SURNAME
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_TIMESTAMP
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SESSION_USERNAME
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.THIS_DEVICE_LINK
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import io.curity.identityserver.plugin.frejaeid.config.PredefinedEnvironment
import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.*
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpStatus
import se.curity.identityserver.sdk.http.RedirectStatusCode
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.ResponseModel
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

class WaitRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig) : AuthenticatorRequestHandler<RequestModel>
{
    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)

    private val _sessionManager = config.sessionManager
    private val _requestLogicHelper = RequestLogicHelper(config)

    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> = Optional.empty()
    //if config enables qr code render it, first calling to get the authRef

    override fun preProcess(request: Request, response: Response): RequestModel
    {

        var viewData = emptyMap<String, String>()
        if (config.qrCodeEnabled())
        {
            val authRef : String?
            if (config.sessionManager.get(SESSION_AUTH_REF) == null && request.isGetRequest) {
                //the following data are proposed from freja documentation on QRCode generation
                val postData = _requestLogicHelper.createQRCodePostData()
                val responseData = _requestLogicHelper.doAuthenticate(postData)
                authRef = responseData["authRef"]?.toString()
                config.sessionManager.put(Attribute.of(SESSION_AUTH_REF, authRef))
            } else {
                authRef = config.sessionManager.get(SESSION_AUTH_REF).getValueOfType(String::class.java)
            }
            val baseUrl = if (config.environment == PredefinedEnvironment.PRODUCTION) QR_CODE_GENERATE_URL_PROD else QR_CODE_GENERATE_URL_TEST

            val cspOverride = "img-src 'self' $baseUrl;"
            val appLink = _requestLogicHelper.generateAppLink(authRef.toString())
            val qrCode = _requestLogicHelper.generateQRCodeLink(baseUrl, appLink, config.environment)
            viewData = mapOf(QR_CODE to qrCode, CSP_OVERRIDE_IMG_SRC to cspOverride, THIS_DEVICE_LINK to appLink)
        }

        response.setResponseModel(ResponseModel.templateResponseModel(
                viewData, "authenticate/wait"),
                Response.ResponseModelScope.NOT_FAILURE)

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(),
                "authenticate/wait"), Response.ResponseModelScope.ANY)
        response.putViewData("_haapiMoveOn", false, Response.ResponseModelScope.ANY)

        return RequestModel(request, null)
    }

    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        val moveOn = when (requestModel.postRequestModel)
        {
            is WaitModel -> requestModel.postRequestModel.moveOn
            else -> false
        }

        val cancel = when (requestModel.postRequestModel)
        {
            is WaitModel -> requestModel.postRequestModel.cancel
            else -> false
        }

        if (cancel)
        {
            val authRefAttribute: Attribute = _sessionManager.get(SESSION_AUTH_REF)
                    ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                            "$SESSION_AUTH_REF cannot be null")
            _requestLogicHelper.cancelAuthenticationRequest(authRefAttribute)
            redirectOnCancelOrError()
        }
        else if (moveOn)
        {
            val status = _sessionManager.remove(SESSION_STATUS)
                    ?: throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid State")

            val statusValue = status.value.toString()
            var subject: String? = null
            _sessionManager.remove(SESSION_AUTH_REF)

            if (statusValue == "APPROVED")
            {
                val subjectAttributes = ArrayList<Attribute>()

                val nameAttribute = _sessionManager.remove(SESSION_NAME)
                if (nameAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("name", nameAttribute.value.toString()))
                }

                val surnameAttribute = _sessionManager.remove(SESSION_SURNAME)
                if (surnameAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("surname", surnameAttribute.value.toString()))
                }

                val emailAttribute = _sessionManager.remove(SESSION_EMAIL)
                if (emailAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("email", emailAttribute.value.toString()))
                    subject = emailAttribute.value.toString()
                }

                val dateOfBirthAttribute = _sessionManager.remove(SESSION_DATE_OF_BIRTH)
                if (dateOfBirthAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("dateOfBirth", dateOfBirthAttribute.value.toString()))
                }

                val ssnAttribute = _sessionManager.remove(SESSION_SSN)
                if (ssnAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("ssn", ssnAttribute.value.toString()))
                    subject = ssnAttribute.value.toString()
                }

                val countryAttribute = _sessionManager.remove(SESSION_COUNTRY)
                if (countryAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("country", countryAttribute.value.toString()))
                }

                val relyingPartyUserIdAttribute = _sessionManager.remove(SESSION_RP_USER_ID)
                if (relyingPartyUserIdAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("relyingPartyUserId", relyingPartyUserIdAttribute.value.toString()))
                    subject = relyingPartyUserIdAttribute.value.toString()
                }

                val integratorSpecificUserIdAttribute = _sessionManager.remove(SESSION_INTEGRATOR_SPECIFIC_USER_ID)
                if (integratorSpecificUserIdAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("integratorSpecificUserId", integratorSpecificUserIdAttribute.value.toString()))
                    subject = integratorSpecificUserIdAttribute.value.toString()
                }

                val customIdentifierAttribute = _sessionManager.remove(SESSION_CUSTOM_IDENTIFIER)
                if (customIdentifierAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("custom-identifier", customIdentifierAttribute.value.toString()))
                    subject = customIdentifierAttribute.value.toString()
                }

                val timestamp = _sessionManager.remove(SESSION_TIMESTAMP)

                if (_sessionManager.get(SESSION_USERNAME) != null)
                {
                    subject = _sessionManager.remove(SESSION_USERNAME).value.toString()
                }
                else if (subject == null)
                {
                    _logger.info("Subject could not be resolved, authentication will fail. " +
                            "Make sure that the required attributes-to-return are configured. " +
                            "Redirecting to the previous view.")
                    redirectOnCancelOrError()
                }

                return Optional.of(
                        AuthenticationResult(
                                AuthenticationAttributes.of(
                                        SubjectAttributes.of(subject,
                                                Attributes.of(subjectAttributes)),
                                        ContextAttributes.of(Attributes.of(
                                                Attribute.of("timestamp", timestamp.value.toString()))))))
            }
            else if (statusValue == "REJECTED" || statusValue == "EXPIRED" || statusValue == "CANCELED" || statusValue == "RP_CANCELED")
            {
                val dataMap: HashMap<String, Any> = HashMap(2)

                dataMap["userInfoType"] = config.userInfoType.toString().toLowerCase()
                dataMap["error"] = "error.request.${status.value.toString().toLowerCase()}"

                // GET request
                if (config.userPreferencesManager.username != null)
                {
                    if (config.userInfoType == UserInfoType.SSN)
                    {
                        dataMap["username"] = config.userPreferencesManager.username
                    }
                    else
                    {
                        dataMap["email"] = config.userPreferencesManager.username
                    }
                }

                response.setResponseModel(ResponseModel.templateResponseModel(dataMap,
                        "authenticate/error"),
                        Response.ResponseModelScope.NOT_FAILURE)
            }
        }
        else
        {
            val responseData = _requestLogicHelper.checkAuthenticationStatus(
                    _sessionManager.get(SESSION_AUTH_REF))
            val status = responseData["status"]
            _sessionManager.put(Attribute.of(SESSION_STATUS, status.toString()))

            if (status == "APPROVED")
            {
                val claimsMap = _requestLogicHelper.extractAttributesFromJwt(responseData)

                val requestedAttributes = when
                {
                    claimsMap["requestedAttributes"] != null -> claimsMap["requestedAttributes"] as Attribute
                    responseData["requestedAttributes"] != null -> responseData["requestedAttributes"] as Attribute
                    else -> null
                }

                if (requestedAttributes != null)
                {
                    val requestedAttributesMap = requestedAttributes.getValueOfType(HashMap::class.java)
                    if (requestedAttributesMap.containsKey("basicUserInfo"))
                    {
                        val basicUserInfo = requestedAttributesMap["basicUserInfo"] as HashMap<*, *>
                        _sessionManager.put(Attribute.of(SESSION_NAME, basicUserInfo["name"].toString()))
                        _sessionManager.put(Attribute.of(SESSION_SURNAME, basicUserInfo["surname"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("emailAddress"))
                    {
                        _sessionManager.put(Attribute.of(SESSION_EMAIL, requestedAttributesMap["emailAddress"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("dateOfBirth"))
                    {
                        _sessionManager.put(Attribute.of(SESSION_DATE_OF_BIRTH, requestedAttributesMap["dateOfBirth"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("ssn"))
                    {
                        val ssn = requestedAttributesMap["ssn"] as HashMap<*, *>
                        _sessionManager.put(Attribute.of(SESSION_SSN, ssn["ssn"].toString()))
                        _sessionManager.put(Attribute.of(SESSION_COUNTRY, ssn["country"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("relyingPartyUserId"))
                    {
                        _sessionManager.put(Attribute.of(SESSION_RP_USER_ID, requestedAttributesMap["relyingPartyUserId"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("integratorSpecificUserId"))
                    {
                        _sessionManager.put(Attribute.of(SESSION_INTEGRATOR_SPECIFIC_USER_ID,
                                requestedAttributesMap["integratorSpecificUserId"].toString()))
                    }

                    if (requestedAttributesMap.containsKey("customIdentifier"))
                    {
                        _sessionManager.put(Attribute.of(SESSION_CUSTOM_IDENTIFIER, requestedAttributesMap["customIdentifier"].toString()))
                    }
                }

                _sessionManager.put(Attribute.of(SESSION_TIMESTAMP, claimsMap["timestamp"].toString()))

                //Tell the poller we're ready
                response.putViewData("_haapiMoveOn", true, Response.ResponseModelScope.ANY)
                response.setHttpStatus(HttpStatus.ACCEPTED)
                return Optional.empty()
            }
            else if (status == "REJECTED" || status == "EXPIRED" || status == "CANCELED" || status == "RP_CANCELED")
            {
                response.putViewData("_haapiMoveOn", true, Response.ResponseModelScope.ANY)
                response.setHttpStatus(HttpStatus.ACCEPTED)
                return Optional.empty()
            }
        }
        return Optional.empty()
    }

    private fun redirectOnCancelOrError()
    {
        val uri = if (config.qrCodeEnabled()) config.authenticatorInformationProvider.authenticationBaseUri else config.authenticatorInformationProvider.fullyQualifiedAuthenticationUri

        throw config.exceptionFactory.redirectException(uri, RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
    }
}