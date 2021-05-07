/*
 *  Copyright 2020 Curity AB
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

import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.AUTH_REF_KEY
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.QR_CODE_GENERATE_PATH
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SIGN_REF_KEY
import io.curity.identityserver.plugin.frejaeid.authentication.FrejaConstants.Companion.SIMPLE_UTF8_TEXT
import io.curity.identityserver.plugin.frejaeid.config.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.attribute.Attributes
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpRequest
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.service.WebServiceClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

class RequestLogicHelper(private val config: FrejaEidAuthenticatorPluginConfig)
{
    private val _json = config.json
    private val _httpClient = config.httpClient
    private val _frejaClient = getWebServiceClient(config.environment.getHost())
    private val _exceptionFactory = config.exceptionFactory
    private val _relyingPartyId = config.relyingPartyId.orElse(null)
    private val _minRegistrationLevel = config.minimumRegistrationLevel
    private val _attributesToReturn = config.attributesToReturn
    private val _logger: Logger = LoggerFactory.getLogger(RequestLogicHelper::class.java)

    fun createPostData(userInfoType: UserInfoType, username: String): Map<String, Any>
    {
        val dataMap = HashMap<String, Any>(4)

        dataMap["userInfoType"] = userInfoType.toString()
        dataMap["minRegistrationLevel"] = _minRegistrationLevel.toString()

        if (userInfoType == UserInfoType.SSN)
        {
            val userInfo = HashMap<String, Any>(2)
            userInfo["country"] = "SE"
            userInfo["ssn"] = username
            dataMap["userInfo"] = Base64.getEncoder().encodeToString(_json.toJson(userInfo).toByteArray())
        }
        else
        {
            dataMap["userInfo"] = username
        }

        dataMap["attributesToReturn"] = createAttributesToReturn()

        return dataMap
    }

    fun addSignTextToPostData(signText: String, postData: Map<String, Any>): Map<String, Any>
    {
        val postDataWithSignText = HashMap(postData)
        postDataWithSignText["dataToSignType"] = SIMPLE_UTF8_TEXT

        val dataToSign = HashMap<String, Any>()
        dataToSign["text"] = Base64.getEncoder().encodeToString(signText.toByteArray())
        postDataWithSignText["dataToSign"] = dataToSign

        postDataWithSignText["signatureType"] = "SIMPLE"

        return postDataWithSignText
    }

    fun createQRCodePostData(): Map<String, Any>
    {
        val dataMap = HashMap<String, Any>(4)

        dataMap["userInfoType"] = "INFERRED"
        dataMap["minRegistrationLevel"] = _minRegistrationLevel.toString()
        dataMap["userInfo"] = "N/A"
        dataMap["attributesToReturn"] = createAttributesToReturn()

        return dataMap
    }

    private fun createAttributesToReturn(): List<Map<String, AttributesToReturn>>
    {
        if (_minRegistrationLevel == RegistrationLevel.BASIC)
        {
            return _attributesToReturn.filter { attr ->
                AttributesToReturn.EMAIL_ADDRESS == attr || AttributesToReturn.INTEGRATOR_SPECIFIC_USER_ID == attr
            }.map { mapOf("attribute" to it) }
        }
        else if (_attributesToReturn.isNotEmpty() && _minRegistrationLevel != RegistrationLevel.BASIC)
        {
            return _attributesToReturn.map { mapOf("attribute" to it) }
        }
        return emptyList()
    }

    fun requestAuthentication(postData: Map<String, Any>): Map<String, Any>
    {
        _logger.trace("Posting an authentication request to Freja")
        return callFrejaService("/authentication/1.0/initAuthentication", "initAuthRequest", postData)
    }

    fun requestSignature(postData: Map<String, Any>): Map<String, Any>
    {
        _logger.trace("Posting a request to sign")
        return callFrejaService("/sign/1.0/initSignature", "initSignRequest", postData)
    }

    fun checkAuthenticationStatus(authRef: Attribute?): Map<String, Any>
    {
        val authRequestMap = toMapOrError(AUTH_REF_KEY, authRef)
        return callFrejaService("/authentication/1.0/getOneResult",
                "getOneAuthResultRequest", authRequestMap)
    }

    fun checkSignatureStatus(signRef: Attribute?): Map<String, Any>
    {
        val signRequestMap = toMapOrError(SIGN_REF_KEY, signRef)
        return callFrejaService("/sign/1.0/getOneResult",
                "getOneSignResultRequest", signRequestMap)
    }

    fun cancelAuthenticationRequest(authRef: Attribute)
    {
        callFrejaService("/authentication/1.0/cancel",
                "cancelAuthRequest",
                toMapOrError(AUTH_REF_KEY, authRef)
        )
        _logger.trace("Canceled Freja authentication request : ${authRef.attributeValue}")
    }

    fun cancelSignRequest(signRef: Attribute)
    {
        callFrejaService("/sign/1.0/cancel",
                "cancelSignRequest",
                toMapOrError(SIGN_REF_KEY, signRef)
        )
        _logger.trace("Canceled Freja authentication request : ${signRef.attributeValue}")
    }

    private fun toMapOrError(key : String, attribute: Attribute?) : Map<String, Any>
    {
        return attribute?.let { Collections.singletonMap(key, it.value) }
                ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                        "$key cannot be null")
    }

    private fun callFrejaService(path: String, paramName: String, requestParams: Map<String, Any>): Map<String, Any>
    {
        val httpResponse = encodeAndPost(path, paramName, requestParams)

        val statusCode = httpResponse.statusCode()
        _logger.debug("Response from Freja endpoint {}: status = {}, response = {}", path, statusCode,
                httpResponse.body(HttpResponse.asString()))

        return when (statusCode)
        {
            200 -> _json.fromJson(httpResponse.body(HttpResponse.asString()))
            in 400..499 -> throw _exceptionFactory.unauthorizedException(ErrorCode.AUTHENTICATION_FAILED)
            else ->
            {
                _logger.info("Unexpected response code $statusCode. ${httpResponse.body(HttpResponse.asString())}")
                throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR)
            }
        }
    }

    private fun encodeAndPost(path: String, paramName: String, requestParams: Map<String, Any>) : HttpResponse
    {
        val requestJson = _json.toJson(requestParams)
        val encodedRequest = Base64.getEncoder().encodeToString(requestJson.toByteArray())
        val authResultRequest = "$paramName=${encodedRequest}"
        val requestBody = _relyingPartyId?.let { "$authResultRequest&relyingPartyId=$it" } ?: authResultRequest

        _logger.trace("Calling Freja service at $path, with $paramName = base64 encoded $requestJson")
        return _frejaClient
                .withPath(path)
                .request()
                .contentType("text/plain")
                .body(HttpRequest.fromString(requestBody, StandardCharsets.UTF_8))
                .post()
                .response()
    }

    private fun getWebServiceClient(host: String): WebServiceClient = if (_httpClient.isPresent)
    {
        config.webServiceClientFactory.create(_httpClient.get()).withHost(host)
    }
    else
    {
        config.webServiceClientFactory.create(URI.create("https://$host"))
    }

    fun generateQRCodeLink(baseUrl: String, appLink: String, environment: PredefinedEnvironment): String
    {
        val builder = StringBuilder(baseUrl)
        builder.append(QR_CODE_GENERATE_PATH)
        builder.append(URLEncoder.encode(appLink, "utf-8"))

        return builder.toString()
    }

    fun generateAppLink(authRef: String): String
    {
        return "frejaeid://bindUserToTransaction?transactionReference=$authRef"
    }

    fun extractAttributesFromJwt(responseData: Map<String, Any>): Attributes
    {
        val jwtParts = Objects.toString(responseData["details"]).split("\\.".toRegex(), 3).toTypedArray()

        if (jwtParts.size < 2)
        {
            throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid JWS")
        }

        val base64Url = Base64.getUrlDecoder()
        val body = String(base64Url.decode(jwtParts[1]))
        return _json.toAttributes(body)
    }
}