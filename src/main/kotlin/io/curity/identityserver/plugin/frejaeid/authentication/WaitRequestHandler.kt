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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.attribute.*
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpRequest
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.http.HttpStatus
import se.curity.identityserver.sdk.service.WebServiceClient
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.ResponseModel
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.HashMap


class WaitRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig) : AuthenticatorRequestHandler<Request>
{
    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)

    private val _json = config.json
    private val _sessionManager = config.sessionManager
    private val _relyingPartyId = config.relyingPartyId.orElse(null)

    override fun get(requestModel: Request, response: Response): Optional<AuthenticationResult> = Optional.empty()

    override fun preProcess(request: Request, response: Response): Request
    {
        if (request.isGetRequest)
        {
            response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(), "authenticate/wait"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(),
                "authenticate/wait"), HttpStatus.BAD_REQUEST)
        return request
    }

    override fun post(requestModel: Request, response: Response): Optional<AuthenticationResult>
    {
        val responseData = checkAuthenticationStatus() as Map<*, *>
        val status = responseData["status"]

        if (status == "APPROVED")
        {
            val jwtParts = Objects.toString(responseData["details"]).split("\\.".toRegex(), 3).toTypedArray()

            if (jwtParts.size < 2)
            {
                throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid JWS")
            }

            val base64Url = Base64.getUrlDecoder()
            val body = String(base64Url.decode(jwtParts[1]))
            val claimsMap = _json.fromJson(body)
            val subjectAttributes = ArrayList<Attribute>()

            if (config.userInfoType == UserInfoType.SSN)
            {
                val basicUserInfo = when
                {
                    claimsMap["basicUserInfo"] != null    -> claimsMap["basicUserInfo"] as HashMap<*, *>
                    responseData["basicUserInfo"] != null -> responseData["basicUserInfo"] as HashMap<*, *>
                    else                                  -> null
                }

                if (basicUserInfo != null)
                {
                    subjectAttributes.add(Attribute.of("name", basicUserInfo["name"].toString()))
                    subjectAttributes.add(Attribute.of("surname", basicUserInfo["surname"].toString()))
                }
            }

            subjectAttributes.add(Attribute.of(config.userInfoType.toString(), config.userPreferencesManager.username))

            return Optional.of(
                    AuthenticationResult(
                            AuthenticationAttributes.of(
                                    SubjectAttributes.of(config.userPreferencesManager.username,
                                            Attributes.of(subjectAttributes)),
                                    ContextAttributes.of(Attributes.of(
                                            Attribute.of("timestamp", claimsMap["timestamp"].toString()))))))
        }
        else if (status == "REJECTED" || status == "EXPIRED" || status == "CANCELED")
        {
            val dataMap: HashMap<String, Any> = HashMap(2)

            dataMap["userInfoType"] = config.userInfoType.toString().toLowerCase()
            dataMap["error"] = "The authorization request has been $status."

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
        else
        {
            response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(), "authenticate/wait"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        return Optional.empty()
    }

    private fun checkAuthenticationStatus(): Map<String, Any>
    {
        val authRef = _sessionManager.get("authRef")?.let {
            _json.toJson(Collections.singletonMap("authRef", it.value)).toByteArray()
        }
                ?: throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE,
                        "authRef cannot be null")
        val authResultRequest = "getOneAuthResultRequest=${Base64.getEncoder().encodeToString(authRef)}"
        val requestBody = _relyingPartyId?.let { "$authResultRequest&relyingPartyId=$it"} ?: authResultRequest
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