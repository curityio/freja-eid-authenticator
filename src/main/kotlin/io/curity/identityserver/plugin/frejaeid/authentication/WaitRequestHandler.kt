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
import java.util.*

class WaitRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig) : AuthenticatorRequestHandler<Request> {

    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)

    override fun get(requestModel: Request, response: Response): Optional<AuthenticationResult> = Optional.empty()

    override fun preProcess(request: Request, response: Response): Request {
        if (request.isGetRequest) {
            response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(), "authenticate/wait"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(),
                "authenticate/wait"), HttpStatus.BAD_REQUEST)
        return request
    }

    override fun post(requestModel: Request, response: Response): Optional<AuthenticationResult> {
        val responseData: Map<String, String> = checkAuthenticationStatus() as Map<String, String>
        if (responseData["status"] == "APPROVED") {
            return Optional.of(
                    AuthenticationResult(
                            AuthenticationAttributes.of(
                                    SubjectAttributes.of(config.userPreferencesManager.username, Attributes.of(
                                            Attribute.of("username", config.userPreferencesManager.username),
                                            Attribute.of("authRef", responseData["authRef"])
                                    )),
                                    ContextAttributes.of(Attributes.of(Attribute.of("iat", Date().time))))))
        }
        response.setResponseModel(ResponseModel.templateResponseModel(emptyMap(), "authenticate/wait"),
                Response.ResponseModelScope.NOT_FAILURE)
        return Optional.empty()
    }

    private fun checkAuthenticationStatus(): Map<String, Any> {
        if (config.sessionManager.get("authRef") == null) {
            throw config.exceptionFactory.badRequestException(ErrorCode.INVALID_SERVER_STATE, "authRef cannot be null");
        }
        val body = HttpRequest.fromByteArray(config.json.toJson(Collections.singletonMap("authRef", config.sessionManager.get("authRef").value)).toByteArray())
        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/getOneResult")
                .request()
                .contentType("application/json")
                .body(body)
                .method("POST")
                .response()
        val statusCode = httpResponse.statusCode()

        if (statusCode != 200) {
            if (_logger.isErrorEnabled) {
                _logger.error("Got error response from token endpoint: error = {}, {}", statusCode,
                        httpResponse.body(HttpResponse.asString()))
            }

            throw config.exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR)
        }

        return config.json.fromJson(httpResponse.body(HttpResponse.asString()))
    }

    private fun getWebServiceClient(host: String): WebServiceClient = if (config.httpClient.isPresent) {
        config.webServiceClientFactory.create(config.httpClient.get()).withHost(host)
    } else {
        config.webServiceClientFactory.create(URI.create("https://$host"))
    }
}