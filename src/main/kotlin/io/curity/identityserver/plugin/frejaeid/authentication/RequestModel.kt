/*
 *  Copyright 2019 Curity AB
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

import io.curity.identityserver.plugin.frejaeid.config.UserInfoType
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import se.curity.identityserver.sdk.web.Request
import javax.validation.Valid
import javax.validation.constraints.Pattern

class RequestModel(request: Request, userInfoType: UserInfoType?)
{
    @Valid
    val postRequestModel: Post? = if (request.isPostRequest)
    {
        when (userInfoType)
        {
            UserInfoType.SSN -> SSNRequestModel(request)
            UserInfoType.EMAIL -> EmailModel(request)
            UserInfoType.PHONE -> PhoneRequestModel(request)
            else -> WaitModel(request)
        }
    }
    else
    {
        null
    }
}

sealed class Post

class SSNRequestModel(request: Request) : Post()
{
    @Pattern(regexp = "[0-9]{12}", message = "validation.error.ssn.invalid")
    @NotBlank(message = "validation.error.ssn.required")
    val username: String = request.getFormParameterValueOrError("username")
}

class EmailModel(request: Request) : Post()
{
    @Email(message = "validation.error.email.invalid")
    @NotBlank(message = "validation.error.email.required")
    val username: String = request.getFormParameterValueOrError("username")
}

class PhoneRequestModel(request: Request) : Post()
{
    @Pattern(regexp = "^\\+[0-9]{11}", message = "validation.error.phone.invalid")
    @NotBlank(message = "validation.error.phone.required")
    val username: String = request.getFormParameterValueOrError("username")
}

class WaitModel(request: Request) : Post()
{
    val moveOn: Boolean = "true" == request.getFormParameterValueOrError("moveOn")
    val cancel: Boolean = "true" == request.getFormParameterValueOrError("cancel")
}