package io.curity.identityserver.plugin.frejaeid.authentication

import se.curity.identityserver.sdk.haapi.*
import se.curity.identityserver.sdk.http.HttpMethod
import se.curity.identityserver.sdk.http.MediaType
import se.curity.identityserver.sdk.web.LinkRelation
import se.curity.identityserver.sdk.web.Representation
import java.net.URI

class GetRepresentationFunction : RepresentationFunction
{
    companion object
    {
        val titleMessage: Message = Message.ofKey("view.title")
        val submitMessage: Message = Message.ofKey("view.submit")
    }
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation
    {
        val authUrl = URI.create(model.getString("_authUrl"))
        val userInfoType = model.getString("userInfoType")

        return factory.newAuthenticationStep { step ->
            step.addMessage(Message.ofKey("view.$userInfoType.description"), HaapiContract.MessageClasses.INFO)
            step.addFormAction(HaapiContract.Actions.Kinds.LOGIN, authUrl, HttpMethod.POST,
                    MediaType.X_WWW_FORM_URLENCODED, titleMessage, submitMessage) { fields ->
                fields.addTextField("username", Message.ofKey("view.$userInfoType.label"))

            }
        }
    }
}

class ErrorRepresentationFunction : RepresentationFunction
{
    companion object
    {
        val restartMessage: Message = Message.ofKey("error.restart")
    }
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation
    {
        val restartLink = URI.create(model.getString("_authUrl"))
        val error = model.getString("error")
        return factory.newAuthenticationStep { step ->
            step.addLink(restartLink, LinkRelation.of("restart"), restartMessage)
            step.addMessage(Message.ofKey(error), HaapiContract.MessageClasses.ERROR)
        }
    }
}

class WaitRepresentationFunction : RepresentationFunction
{
    companion object
    {
        val cancelMessage: Message = Message.ofKey("wait.cancel")
        val thisDeviceMessage: Message = Message.ofKey("view.this-device")
        val scanQrCode: Message = Message.ofKey("view.scan-qrcode")
        val startAppLinkRelation: LinkRelation = LinkRelation.of("app-start")
    }
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
            val thisDeviceLink = model.getOptionalString("_thisDeviceLink")
            val qrCode = model.getOptionalString("_qrCode")
            factory.newPollingStep().pending { step ->
                if (qrCode.isPresent && thisDeviceLink.isPresent) {
                    step.addLink(URI.create(thisDeviceLink.get()), startAppLinkRelation, thisDeviceMessage)
                    step.addLink(URI.create(qrCode.get()), startAppLinkRelation, scanQrCode, MediaType.IMAGE_PNG)
                }
                step.setPollFormAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED, null, Actions.EMPTY_CONSUMER)
                step.setCancelFormAction(action, HttpMethod.POST, MediaType.X_WWW_FORM_URLENCODED,
                        cancelMessage,
                        cancelMessage) { fields ->
                    fields.addHiddenField("cancel", "true")
                }
            }
        }
    }
}
