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

import io.curity.identityserver.plugin.frejaeid.config.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val _exceptionFactory = config.exceptionFactory
    private val _relyingPartyId = config.relyingPartyId.orElse(null)
    private val _minRegistrationLevel = config.minimumRegistrationLevel
    private val _attributesToReturn = config.attributesToReturn
    private val _logger: Logger = LoggerFactory.getLogger(RequestLogicHelper::class.java)

    companion object
    {
        const val QR_CODE_GENERATE_URL_TEST = "https://resources.test.frejaeid.com"
        const val QR_CODE_GENERATE_URL_PROD = "https://resources.prod.frejaeid.com"
        const val QR_CODE_GENERATE_PATH = "/qrcode/generate?qrcodedata="
    }

    fun createPostData(userInfoType: UserInfoType, username: String): Map<String, Any>
    {
        val dataMap = HashMap<String, Any>(4)

        dataMap["userInfoType"] = userInfoType.toString()
        dataMap["minRegistrationLevel"] = _minRegistrationLevel.toString()

        if (userInfoType.equals(UserInfoType.SSN))
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

    fun getAuthTransaction(postData: Map<String, Any>): Map<String, Any>
    {
        val initialRequest = "initAuthRequest=${Base64.getEncoder().encodeToString(buildJsonAuthRequest(postData).toByteArray())}"
        val requestBody = _relyingPartyId?.let { "$initialRequest&relyingPartyId=$it" } ?: initialRequest
        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/initAuthentication")
                .request()
                .contentType("text/plain")
                .body(HttpRequest.fromString(requestBody, StandardCharsets.UTF_8))
                .post()
                .response()
        val statusCode = httpResponse.statusCode()

        if (statusCode != 200)
        {
            if (_logger.isWarnEnabled)
            {
                _logger.warn("Got error response from authentication endpoint: error = {}, {}", statusCode,
                        httpResponse.body(HttpResponse.asString()))
            }

            if (_json.fromJson(httpResponse.body(HttpResponse.asString()))["code"] == 2000)
            {
                throw _exceptionFactory.badRequestException(ErrorCode.EXTERNAL_SERVICE_ERROR, "too.many.attempts")
            }

            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR)
        }

        return _json.fromJson(httpResponse.body(HttpResponse.asString()))
    }

    private fun buildJsonAuthRequest(postData: Map<String, Any>): String
    {
        return "{" + postData.map {
            val value = when
            {
                it.value is String -> "\"" + it.value + "\""
                else -> _json.toJson(it.value)
            }
            "\"" + it.key + "\"" + ":" + value
        }.joinToString() + "}"
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
}