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
import io.curity.identityserver.plugin.frejaeid.authentication.WaitRequestHandler.Companion.SESSION_USERNAME
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.authentication.AuthenticatedState
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpStatus
import se.curity.identityserver.sdk.http.RedirectStatusCode
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.ResponseModel.templateResponseModel
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

class StartRequestHandler(private val config: FrejaEidAuthenticatorPluginConfig,
                          private val authenticatedState: AuthenticatedState)
    : AuthenticatorRequestHandler<RequestModel>
{
    private val _userInfoType = config.userInfoType
    private val _exceptionFactory = config.exceptionFactory
    private val _userPreferencesManager = config.userPreferencesManager
    private val _requestLogicHelper = RequestLogicHelper(config)

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

            when
            {
                config.qrCodeEnabled() ->
                {
                    //if qrcode is enabled, throw to go to wait handler
                    // and skip the form entry for email, ssn or phone
                    throw _exceptionFactory.redirectException(getWaitHandlerUrl(),
                            RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
                }
                authenticatedState.isAuthenticated ->
                {

                    startAuthentication(requestModel)
                }
                else ->
                {
                    Optional.empty()
                }
            }

    override fun post(requestModel: RequestModel, response: Response): Optional<AuthenticationResult> =
            startAuthentication(requestModel)

    private fun startAuthentication(requestModel: RequestModel): Optional<AuthenticationResult>
    {
        val username = if (authenticatedState.isAuthenticated)
        {
            authenticatedState.username
        }
        else
        {
            when
            {
                requestModel.postRequestModel is SSNRequestModel -> requestModel.postRequestModel.username
                requestModel.postRequestModel is EmailModel -> requestModel.postRequestModel.username
                requestModel.postRequestModel is PhoneRequestModel -> requestModel.postRequestModel.username
                else ->
                    throw _exceptionFactory.internalServerException(ErrorCode.CONFIGURATION_ERROR)
            }
        }

        _userPreferencesManager.saveUsername(username)
        config.sessionManager.put(Attribute.of(SESSION_USERNAME, username));

        val postData = _requestLogicHelper.createPostData(_userInfoType, username).toMutableMap()
        val responseData = _requestLogicHelper.getAuthTransaction(postData)
        val authRef = responseData["authRef"]?.toString()

        if (authRef != null)
        {
            config.sessionManager.put(Attribute.of(SESSION_AUTH_REF, authRef))

            throw _exceptionFactory.redirectException(getWaitHandlerUrl(),
                    RedirectStatusCode.MOVED_TEMPORARILY, emptyMap(), false)
        }

        return Optional.empty()
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
}