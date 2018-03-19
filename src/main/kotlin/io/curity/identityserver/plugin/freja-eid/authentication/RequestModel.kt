package io.curity.identityserver.plugin.authentication

import org.hibernate.validator.constraints.Email
import org.hibernate.validator.constraints.NotBlank
import se.curity.identityserver.sdk.web.Request
import javax.validation.Valid

class RequestModel(request: Request) {
    @Valid
    val postRequestModel: Post? = if (request.isPostRequest) {
        if (request.parameterNames.contains("username")) {
            UsernameModel(request)
        } else {
            EmailModel(request)
        }
    } else {
        null
    }
}

open class Post

class UsernameModel(request: Request) : Post() {
    @NotBlank(message = "validation.error.username.required")
    val username: String = request.getFormParameterValueOrError("username")
}

class EmailModel(request: Request) : Post() {
    @Email
    @NotBlank(message = "validation.error.email.required")
    val email: String = request.getFormParameterValueOrError("email")
}