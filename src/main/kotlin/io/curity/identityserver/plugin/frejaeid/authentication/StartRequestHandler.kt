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

import io.curity.identityserver.plugin.frejaeid.authentication.WaitRequestHandler.Companion.SESSION_AUTH_REF
import io.curity.identityserver.plugin.frejaeid.config.AttributesToReturn
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import io.curity.identityserver.plugin.frejaeid.config.RegistrationLevel
import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.authentication.AuthenticatedState
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
import se.curity.identityserver.sdk.web.ResponseModel.templateResponseModel
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Collections
import java.util.Optional

class StartRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig,
                          private val authenticatedState: AuthenticatedState)
    : AuthenticatorRequestHandler<RequestModel>
{
    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)
    private val _json = config.json
    private val _userInfoType = config.userInfoType
    private val _minRegistrationLevel = config.minimumRegistrationLevel
    private val _attributesToReturn = config.attributesToReturn
    private val _exceptionFactory = config.exceptionFactory
    private val _userPreferencesManager = config.userPreferencesManager
    private val _httpClient = config.httpClient
    private val _relyingPartyId = config.relyingPartyId.orElse(null)

    override fun preProcess(request: Request, response: Response): RequestModel
    {
        val dataMap = HashMap<String, Any>(2)

        dataMap["userInfoType"] = _userInfoType.toString().toLowerCase()

        if (request.isGetRequest)
        {
            val username = _userPreferencesManager.username

            if (username != null)
            {
                dataMap["username"] = username
            }

            response.setResponseModel(templateResponseModel(dataMap, "authenticate/get"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(templateResponseModel(dataMap, "authenticate/get"), HttpStatus.BAD_REQUEST)

        return RequestModel(request, _userInfoType)
    }

    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            if (authenticatedState.isAuthenticated)
            {
                startAuthentication(requestModel, response)
            }
            else
            {
                Optional.empty()
            }

    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            startAuthentication(requestModel, response)

    private fun startAuthentication(requestModel: RequestModel, response: Response): Optional<AuthenticationResult>
    {
        val username = when
        {
            requestModel.postRequestModel is SSNRequestModel -> requestModel.postRequestModel.username
            requestModel.postRequestModel is EmailModel -> requestModel.postRequestModel.username
            requestModel.postRequestModel is PhoneRequestModel -> requestModel.postRequestModel.username
            else ->
                if (!authenticatedState.isAuthenticated)
                {
                    throw _exceptionFactory.internalServerException(ErrorCode.CONFIGURATION_ERROR)
                }
                else
                {
                    _userPreferencesManager.username
                }
        }

        _userPreferencesManager.saveUsername(username)

        val postData = createPostData(_userInfoType, username)
        val responseData = getAuthTransaction(postData)
        val authRef = responseData["authRef"]?.toString()

        if (authRef != null)
        {
            config.sessionManager.put(Attribute.of(SESSION_AUTH_REF, authRef))

            throw _exceptionFactory.redirectException(getWaitHandlerUrl(),
                    RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
        }

        return Optional.empty()
    }

    private fun getAuthTransaction(postData: Map<String, Any>): Map<String, Any>
    {
        val initialRequest = "initAuthRequest=${Base64.getEncoder().encodeToString(buildJsonAuthRequest(postData).toByteArray())}"
        val requestBody = _relyingPartyId?.let { "$initialRequest&relyingPartyId=$it" } ?: initialRequest
        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/initAuthentication")
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
                else -> it.value
            }
            "\"" + it.key + "\"" + ":" + value
        }.joinToString() + "}"
    }

    private fun createPostData(userInfoType: UserInfoType, username: String): Map<String, Any>
    {
        val dataMap = HashMap<String, Any>(3)

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

        if (_minRegistrationLevel == RegistrationLevel.BASIC && _attributesToReturn.contains(AttributesToReturn.EMAIL_ADDRESS))
        {
            dataMap["attributesToReturn"] = Collections.singletonList(
                    "{\"attribute\":\"" + AttributesToReturn.EMAIL_ADDRESS + "\"}")
        }
        else if (_attributesToReturn.isNotEmpty() && _minRegistrationLevel != RegistrationLevel.BASIC)
        {
            dataMap["attributesToReturn"] = _attributesToReturn.map { "{\"attribute\":\"$it\"}" }
        }

        return dataMap
    }

    private fun getWaitHandlerUrl(): String
    {
        try
        {
            val authUri = config.authenticatorInformationProvider.fullyQualifiedAuthenticationUri

            return URL(authUri.toURL(), authUri.path + "/wait").toString()
        }
        catch (e: MalformedURLException)
        {
            throw _exceptionFactory.internalServerException(ErrorCode.INVALID_REDIRECT_URI,
                    "Could not create redirect URI")
        }
    }

    private fun getWebServiceClient(host: String): WebServiceClient = if (_httpClient.isPresent)
    {
        config.webServiceClientFactory.create(_httpClient.get()).withHost(host)
    }
    else
    {
        config.webServiceClientFactory.create(URI.create("https://$host"))
    }
}