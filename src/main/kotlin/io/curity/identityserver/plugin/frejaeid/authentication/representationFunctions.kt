package io.curity.identityserver.plugin.frejaeid.authentication

import se.curity.identityserver.sdk.haapi.*
import se.curity.identityserver.sdk.http.HttpMethod
import se.curity.identityserver.sdk.http.MediaType
import se.curity.identityserver.sdk.web.LinkRelation
import se.curity.identityserver.sdk.web.Representation
import java.net.URI

class GetRepresentationFunction : RepresentationFunction
{
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation
    {
        val authUrl = URI.create(model.getString("_authUrl"))
        val userInfoType = model.getString("userInfoType")

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
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation
    {
        val restartLink = URI.create(model.getString("_authUrl"))
        val error = model.getString("error")
        return factory.newAuthenticationStep { step ->
            step.addLink(restartLink, LinkRelation.of("restart"), Message.ofKey("error.restart"))
            step.addMessage(Message.ofKey(error), HaapiContract.MessageClasses.ERROR)
        }
    }
}

class WaitRepresentationFunction : RepresentationFunction
{
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation
    {
        val action = URI.create(model.getString("_authUrl") + "/wait")
        val forceMoveOn = model.getAs("_haapiMoveOn", Boolean::class.javaObjectType)
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
                step.setPollFormAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED, null, Actions.EMPTY_CONSUMER)
                step.setCancelFormAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED,
                        Message.ofKey("wait.cancel"),
                        Message.ofKey("wait.cancel")) { fields ->
                    fields.addHiddenField("cancel", "true")
                }
            }
        }
    }
}
