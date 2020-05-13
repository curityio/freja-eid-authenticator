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

import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import net.glxn.qrgen.QRCode
import net.glxn.qrgen.image.ImageType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.attribute.Attributes
import se.curity.identityserver.sdk.attribute.AuthenticationAttributes
import se.curity.identityserver.sdk.attribute.ContextAttributes
import se.curity.identityserver.sdk.attribute.SubjectAttributes
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpRequest
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.http.HttpStatus
import se.curity.identityserver.sdk.http.RedirectStatusCode
import se.curity.identityserver.sdk.service.WebServiceClient
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.ResponseModel
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Base64
import java.util.Collections
import java.util.Objects
import java.util.Optional


class WaitRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig) : AuthenticatorRequestHandler<RequestModel>
{
    companion object
    {
        const val SESSION_STATUS = "freja-status"
        const val SESSION_NAME = "freja-name"
        const val SESSION_AUTH_REF = "freja-authref"
        const val SESSION_SURNAME = "freja-surname"
        const val SESSION_EMAIL = "freja-email"
        const val SESSION_DATE_OF_BIRTH = "freja-date-of-birth"
        const val SESSION_SSN = "freja-ssn"
        const val SESSION_COUNTRY = "freja-country"
        const val SESSION_RP_USER_ID = "freja-rp-user-id"
        const val SESSION_CUSTOM_IDENTIFIER = "freja-custom-identifier"
        const val SESSION_TIMESTAMP = "freja-timestamp"
        const val SESSION_INTEGRATOR_SPECIFIC_USER_ID = "freja-integrator-specific-user-id"
        const val QR_CODE = "_qrCode"
        const val CSP_OVERRIDE_IMG_SRC = "_cspImgsrc"
        const val CSP_OVERRIDE_IMG_SRC_DATA = "img-src 'self' data:;"
    }

    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)

    private val _json = config.json
    private val _sessionManager = config.sessionManager
    private val _relyingPartyId = config.relyingPartyId.orElse(null)
    private val _requestLogicHelper = RequestLogicHelper(config)

    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> = Optional.empty()
    //if config enables qr code render it, first calling to get the authRef

    override fun preProcess(request: Request, response: Response): RequestModel
    {
        if (request.isGetRequest)
        {
            var qrCode = ""
            if (config.qrCodeEnabled())
            {
                //the following data are proposed from freja documentation on QRCode generation
                val postData = mutableMapOf("userInfoType" to "INFERRED", "userInfo" to "N/A")
                val responseData = _requestLogicHelper.getAuthTransaction(postData)
                val authRef = responseData["authRef"]?.toString()
                config.sessionManager.put(Attribute.of(SESSION_AUTH_REF, authRef))

                qrCode = _requestLogicHelper.generateQRCodeAsDataUri(authRef.toString())
            }

            var viewData = emptyMap<String, String>()
            if (config.qrCodeEnabled())
            {
                viewData = mapOf(QR_CODE to qrCode, CSP_OVERRIDE_IMG_SRC to CSP_OVERRIDE_IMG_SRC_DATA)
            }

            response.setResponseModel(ResponseModel.templateResponseModel(
                    viewData, "authenticate/wait"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(),
                "authenticate/wait"), HttpStatus.BAD_REQUEST)
        return RequestModel(request, null)
    }


    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        val moveOn = when
        {
            requestModel.postRequestModel is WaitModel -> requestModel.postRequestModel.moveOn
            else -> false
        }

        val cancel = when
        {
            requestModel.postRequestModel is WaitModel -> requestModel.postRequestModel.cancel
            else -> false
        }

        if (cancel)
        {
            cancelAuthentication()
            throw config.exceptionFactory.redirectException(config.authenticatorInformationProvider.fullyQualifiedAuthenticationUri,
                    RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
        }
        else if (moveOn)
        {
            val status = _sessionManager.get(SESSION_STATUS)
                    ?: throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid State")

            val statusValue = status.value.toString()
            _sessionManager.remove(SESSION_STATUS)
            _sessionManager.remove(SESSION_AUTH_REF)

            if (statusValue == "APPROVED")
            {
                val subjectAttributes = ArrayList<Attribute>()

                val nameAttribute = _sessionManager.get(SESSION_NAME)
                if (nameAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("name", nameAttribute.value.toString()))
                    _sessionManager.remove(SESSION_NAME)
                }

                val surnameAttribute = _sessionManager.get(SESSION_SURNAME)
                if (surnameAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("surname", surnameAttribute.value.toString()))
                    _sessionManager.remove(SESSION_SURNAME)
                }

                val emailAttribute = _sessionManager.get(SESSION_EMAIL)
                if (emailAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("email", emailAttribute.value.toString()))
                    _sessionManager.remove(SESSION_EMAIL)
                }

                val dateOfBirthAttribute = _sessionManager.get(SESSION_DATE_OF_BIRTH)
                if (dateOfBirthAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("dateOfBirth", dateOfBirthAttribute.value.toString()))
                    _sessionManager.remove(SESSION_DATE_OF_BIRTH)
                }

                val ssnAttribute = _sessionManager.get(SESSION_SSN)
                if (ssnAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("ssn", ssnAttribute.value.toString()))
                    _sessionManager.remove(SESSION_SSN)
                }

                val countryAttribute = _sessionManager.get(SESSION_COUNTRY)
                if (countryAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("country", countryAttribute.value.toString()))
                    _sessionManager.remove(SESSION_COUNTRY)
                }

                val customIdentifierAttribute = _sessionManager.get(SESSION_CUSTOM_IDENTIFIER)
                if (customIdentifierAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("custom-identifier", customIdentifierAttribute.value.toString()))
                    _sessionManager.remove(SESSION_CUSTOM_IDENTIFIER)
                }

                val relyingPartyUserIdAttribute = _sessionManager.get(SESSION_RP_USER_ID)
                if (relyingPartyUserIdAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("relyingPartyUserId", relyingPartyUserIdAttribute.value.toString()))
                    _sessionManager.remove(SESSION_RP_USER_ID)
                }

                val integratorSpecificUserIdAttribute = _sessionManager.get(SESSION_INTEGRATOR_SPECIFIC_USER_ID)
                if (integratorSpecificUserIdAttribute != null)
                {
                    subjectAttributes.add(Attribute.of("integratorSpecificUserId", integratorSpecificUserIdAttribute.value.toString()))
                    _sessionManager.remove(SESSION_INTEGRATOR_SPECIFIC_USER_ID)
                }

                val timestamp = _sessionManager.get(SESSION_TIMESTAMP)
                _sessionManager.remove(SESSION_TIMESTAMP)

                return Optional.of(
                        AuthenticationResult(
                                AuthenticationAttributes.of(
                                        SubjectAttributes.of(config.userPreferencesManager.username,
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
                        "authenticate/get"),
                        Response.ResponseModelScope.NOT_FAILURE)
            }
        }
        else
        {
            val responseData = checkAuthenticationStatus() as Map<*, *>
            val status = responseData["status"]
            _sessionManager.put(Attribute.of(SESSION_STATUS, status.toString()))

            if (status == "APPROVED")
            {
                val jwtParts = Objects.toString(responseData["details"]).split("\\.".toRegex(), 3).toTypedArray()

                if (jwtParts.size < 2)
                {
                    throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid JWS")
                }

                val base64Url = Base64.getUrlDecoder()
                val body = String(base64Url.decode(jwtParts[1]))
                val claimsMap = _json.toAttributes(body)

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
                response.setHttpStatus(HttpStatus.ACCEPTED)
                return Optional.empty()
            }
            else if (status == "REJECTED" || status == "EXPIRED" || status == "CANCELED" || status == "RP_CANCELED")
            {
                response.setHttpStatus(HttpStatus.ACCEPTED)
                return Optional.empty()
            }
        }

        return Optional.empty()
    }

    private fun cancelAuthentication()
    {
        val authRef = _sessionManager.get(SESSION_AUTH_REF)?.let {
            _json.toJson(Collections.singletonMap("authRef", it.value)).toByteArray()
        } ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                "authRef cannot be null")
        _sessionManager.remove(SESSION_AUTH_REF)
        val authResultRequest = "cancelAuthRequest=${Base64.getEncoder().encodeToString(authRef)}"
        val requestBody = _relyingPartyId?.let { "$authResultRequest&relyingPartyId=$it" } ?: authResultRequest
        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/cancel")
                .request()
                .contentType("text/plain")
                .body(HttpRequest.fromString(requestBody, StandardCharsets.UTF_8))
                .method("POST")
                .response()
        val statusCode = httpResponse.statusCode()

        if (statusCode != 200)
        {
            if (_logger.isWarnEnabled)
            {
                _logger.warn("Got error response from cancel endpoint: error = {}, {}", statusCode,
                        httpResponse.body(HttpResponse.asString()))
            }
        }
    }

    private fun checkAuthenticationStatus(): Map<String, Any>
    {
        val authRef = _sessionManager.get(SESSION_AUTH_REF)?.let {
            _json.toJson(Collections.singletonMap("authRef", it.value)).toByteArray()
        }
                ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                        "authRef cannot be null")
        val authResultRequest = "getOneAuthResultRequest=${Base64.getEncoder().encodeToString(authRef)}"
        val requestBody = _relyingPartyId?.let { "$authResultRequest&relyingPartyId=$it" } ?: authResultRequest
        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/getOneResult")
                .request()
                .contentType("text/plain")
                .body(HttpRequest.fromString(requestBody, StandardCharsets.UTF_8))
                .method("POST")
                .response()
        val statusCode = httpResponse.statusCode()

        if (statusCode != 200)
        {
            if (_logger.isWarnEnabled)
            {
                _logger.warn("Got error response from token endpoint: error = {}, {}", statusCode,
                        httpResponse.body(HttpResponse.asString()))
            }

            throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR)
        }

        return _json.fromJson(httpResponse.body(HttpResponse.asString()))
    }

    private fun getWebServiceClient(host: String): WebServiceClient = if (config.httpClient.isPresent)
    {
        config.webServiceClientFactory.create(config.httpClient.get()).withHost(host)
    }
    else
    {
        config.webServiceClientFactory.create(URI.create("https://$host"))
    }
}