/*
 *  Copyright 2017 Curity AB
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

package io.curity.identityserver.plugin.authentication

import se.curity.identityserver.sdk.web.Request

class CallbackRequestModel(request: Request) {
    val error: String

    val errorDescription: String

    val requestUrl: String
    val code: String
    val state: String

    init {
        val invalidParameter = { s: String ->
            RuntimeException(String.format(
                    "Expected only one query string parameter named %s, but found multiple.", s))
        }

        code = request.getQueryParameterValueOrError("code", invalidParameter)
        state = request.getQueryParameterValueOrError("state", invalidParameter)
        error = request.getQueryParameterValueOrError("error", invalidParameter)
        errorDescription = request.getQueryParameterValueOrError("error_description", invalidParameter)
        requestUrl = request.url
    }
}