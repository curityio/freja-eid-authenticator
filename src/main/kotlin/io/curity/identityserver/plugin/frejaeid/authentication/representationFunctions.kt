package io.curity.identityserver.plugin.frejaeid.authentication

import se.curity.identityserver.sdk.haapi.HaapiContract
import se.curity.identityserver.sdk.haapi.Message
import se.curity.identityserver.sdk.haapi.RepresentationFactory
import se.curity.identityserver.sdk.haapi.RepresentationFunction
import se.curity.identityserver.sdk.http.HttpMethod
import se.curity.identityserver.sdk.http.MediaType
import se.curity.identityserver.sdk.web.LinkRelation
import se.curity.identityserver.sdk.web.Representation
import java.net.URI

class GetRepresentationFunction : RepresentationFunction
{
    override fun apply(modelMap: MutableMap<String, Any>, factory: RepresentationFactory): Representation
    {
        val authUrl = modelMap["_authUrl"]?.let { URI.create(it.toString()) }
                ?: throw IllegalStateException("auth url missing")
        val userInfoType = modelMap["userInfoType"] as? String
                ?: throw java.lang.IllegalStateException("userInfoType missing")


        return factory.newAuthenticationStep { step ->
            step.addMessage(Message.ofKey("view.$userInfoType.description"), HaapiContract.MessageClasses.INFO)
            step.addFormAction(HaapiContract.Actions.Kinds.LOGIN, authUrl, HttpMethod.POST,
                    MediaType.X_WWW_FORM_URLENCODED, Message.ofKey("view.title"), Message.ofKey("view.submit")) { fields ->
                fields.addTextField("username", Message.ofKey("view.$userInfoType.label"))

            }
        }
    }
}

class ErrorRepresentationFunction : RepresentationFunction
{
    override fun apply(modelMap: MutableMap<String, Any>, factory: RepresentationFactory): Representation
    {
        val restartLink = modelMap["_authUrl"]?.let { URI.create(it.toString()) }
                ?: throw IllegalStateException("auth url missing")
        val error = modelMap["error"] as? String ?: throw IllegalStateException("error missing")
        return factory.newAuthenticationStep { step ->
            step.addLink(restartLink, LinkRelation.of("restart"), Message.ofKey("error.restart"))
            step.addMessage(Message.ofKey(error), HaapiContract.MessageClasses.ERROR)
        }
    }
}

class WaitRepresentationFunction : RepresentationFunction
{
    override fun apply(modelMap: MutableMap<String, Any>, factory: RepresentationFactory): Representation
    {
        val action = modelMap["_authUrl"]?.let { URI.create(it.toString() + "/wait") }
                ?: throw IllegalStateException("action missing")
        val forceMoveOn = modelMap["_haapiMoveOn"] as? Boolean ?: false
        return if (forceMoveOn)
        {
            factory.newPollingStep().completed(true) { step ->
                step.setRedirectAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED) { fields ->
                    fields.addHiddenField("moveOn", "true")
                }
            }
        }
        else
        {
            factory.newPollingStep().pending { step ->
                step.setPollAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED) {}
                step.setCancelAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED,
                        Message.ofKey("wait.cancel"),
                        Message.ofKey("wait.cancel")) { fields ->
                    fields.addHiddenField("cancel", "true")
                }
            }
        }
    }
}
