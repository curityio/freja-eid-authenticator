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

import org.hibernate.validator.constraints.Email
import org.hibernate.validator.constraints.NotBlank
import se.curity.identityserver.sdk.web.Request
import javax.validation.Valid

class RequestModel(request: Request)
{
    @Valid
    val postRequestModel: Post? = if (request.isPostRequest)
    {
        when
        {
            request.parameterNames.contains("username") -> UsernameModel(request)
            request.parameterNames.contains("email") -> EmailModel(request)
            else -> WaitModel(request)
        }
    }
    else
    {
        null
    }
}

sealed class Post

class UsernameModel(request: Request) : Post()
{
    @NotBlank(message = "validation.error.username.required")
    val username: String = request.getFormParameterValueOrError("username")
}

class EmailModel(request: Request) : Post()
{
    @Email
    @NotBlank(message = "validation.error.email.required")
    val email: String = request.getFormParameterValueOrError("email")
}

class WaitModel(request: Request) : Post()
{
    val moveOn: Boolean = "true" == request.getFormParameterValueOrError("moveOn")
}