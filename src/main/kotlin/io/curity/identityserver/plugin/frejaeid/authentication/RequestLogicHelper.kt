package io.curity.identityserver.plugin.frejaeid.authentication

import io.curity.identityserver.plugin.frejaeid.config.AttributesToReturn
import io.curity.identityserver.plugin.frejaeid.config.FrejaEidAuthenticatorPluginConfig
import io.curity.identityserver.plugin.frejaeid.config.RegistrationLevel
import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import net.glxn.qrgen.QRCode
import net.glxn.qrgen.image.ImageType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.http.HttpRequest
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.service.WebServiceClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

class RequestLogicHelper(private val config: FrejaEidAuthenticatorPluginConfig)
{
    private val _json = config.json
    private val _httpClient = config.httpClient
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

        if (_minRegistrationLevel == RegistrationLevel.BASIC)
        {
            dataMap["attributesToReturn"] = _attributesToReturn.filter { attr ->
                AttributesToReturn.EMAIL_ADDRESS == attr || AttributesToReturn.INTEGRATOR_SPECIFIC_USER_ID == attr
            }.map { mapOf("attribute" to it) }
        }
        else if (_attributesToReturn.isNotEmpty() && _minRegistrationLevel != RegistrationLevel.BASIC)
        {
            dataMap["attributesToReturn"] = _attributesToReturn.map { mapOf("attribute" to it) }
        }

        return dataMap
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

    fun generateQRCodeAsDataUri(value: String): String
    {
        val transactionUri = URLEncoder
                .encode("frejaeid://bindUserToTransaction?transactionReference=$value", "utf-8")
        val builder = StringBuilder(StartRequestHandler.DATA_IMAGE_PNG_BASE_64)
        val stream = QRCode.from(transactionUri)
                .to(ImageType.PNG)
                .withSize(250, 250)
                .stream()

        val base64EncodedData = Base64.getEncoder().encodeToString(stream.toByteArray())

        builder.append(base64EncodedData)

        return builder.toString()
    }
}