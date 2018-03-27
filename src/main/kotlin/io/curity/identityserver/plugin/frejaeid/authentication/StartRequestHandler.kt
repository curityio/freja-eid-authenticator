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
import java.util.*
import kotlin.collections.HashMap

class StartRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig,
                          private val authenticatedState: AuthenticatedState)
    : AuthenticatorRequestHandler<RequestModel> {

    private val _logger: Logger = LoggerFactory.getLogger(StartRequestHandler::class.java)

    override fun preProcess(request: Request, response: Response): RequestModel {
        val dataMap: HashMap<String, String> = HashMap(2)
        dataMap["userInfoType"] = config.userInfoType.toString().toLowerCase()
        if (request.isGetRequest) {
            // GET request
            if (config.userPreferencesManager.username != null) {
                if (config.userInfoType.equals(UserInfoType.SSN)) {
                    dataMap["username"] = config.userPreferencesManager.username
                } else {
                    dataMap["email"] = config.userPreferencesManager.username
                }
            }
            response.setResponseModel(templateResponseModel(dataMap as Map<String, Any>?, "authenticate/get"),
                    Response.ResponseModelScope.NOT_FAILURE)
        }

        // on request validation failure, we should use the same template as for NOT_FAILURE
        response.setResponseModel(templateResponseModel(dataMap as Map<String, Any>?,
                "authenticate/get"), HttpStatus.BAD_REQUEST)
        return RequestModel(request)
    }

    override fun get(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            if (authenticatedState.isAuthenticated) {
                startAuthentication(requestModel, response)
            } else {
                Optional.empty()
            }

    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            startAuthentication(requestModel, response)

    private fun startAuthentication(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> {
        if (config.userInfoType.equals(UserInfoType.SSN)) {
            config.userPreferencesManager.saveUsername((requestModel.postRequestModel as UsernameModel).username)
        } else {
            config.userPreferencesManager.saveUsername((requestModel.postRequestModel as EmailModel).email)
        }

        val postData: Map<String, Any> = createPostData(config.userInfoType, requestModel)
        val responseData: Map<String, Any> = getAuthTransaction(postData)
        if (responseData["authRef"] != null) {
            config.sessionManager.put(Attribute.of("authRef", responseData["authRef"].toString()))
            throw config.exceptionFactory.redirectException(getWaitHandlerUrl(), RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
        }

        return Optional.empty()
    }

    private fun getAuthTransaction(postData: Map<String, Any>): Map<String, Any> {

        val httpResponse = getWebServiceClient(config.environment.getHost())
                .withPath("/authentication/1.0/initAuthentication")
                .request()
                .contentType("text/plain")
                .body(HttpRequest.fromByteArray(("initAuthRequest=" + Base64.getEncoder().encodeToString(config.json.toJson(postData).toByteArray())).toByteArray()))
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

    private fun createPostData(userInfoType: UserInfoType, requestModel: RequestModel): Map<String, Any> {
        val data = HashMap<String, Any>(3)

        data["userInfoType"] = userInfoType.toString()
        data["askForBasicUserInfo"] = true
        if (userInfoType.equals(UserInfoType.EMAIL)) {
            data["userInfo"] = (requestModel.postRequestModel as EmailModel).email
        } else {
            val username = (requestModel.postRequestModel as UsernameModel).username
            val userInfo = HashMap<String, Any>(2)
            userInfo["country"] = "SE"
            userInfo["ssn"] = username
            data["userInfo"] = userInfo
        }
        return data
    }

    private fun getWaitHandlerUrl(): String {
        try {
            val authUri = config.authenticatorInformationProvider.fullyQualifiedAuthenticationUri

            return URL(authUri.toURL(), authUri.path + "/wait").toString()
        } catch (e: MalformedURLException) {
            throw config.exceptionFactory.internalServerException(ErrorCode.INVALID_REDIRECT_URI,
                    "Could not create redirect URI")
        }
    }

    private fun getWebServiceClient(host: String): WebServiceClient = if (config.httpClient.isPresent) {
        config.webServiceClientFactory.create(config.httpClient.get()).withHost(host)
    } else {
        config.webServiceClientFactory.create(URI.create("https://$host"))
    }
}