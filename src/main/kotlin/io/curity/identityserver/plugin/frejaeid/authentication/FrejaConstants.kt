/*
 *  Copyright 2021 Curity AB
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

class FrejaConstants
{
    companion object
    {
        const val QR_CODE_GENERATE_URL_TEST = "https://resources.test.frejaeid.com"
        const val QR_CODE_GENERATE_URL_PROD = "https://resources.prod.frejaeid.com"
        const val QR_CODE_GENERATE_PATH = "/qrcode/generate?qrcodedata="
        const val SIMPLE_UTF8_TEXT = "SIMPLE_UTF8_TEXT"
        const val SIGN_REF_KEY = "signRef"
        const val AUTH_REF_KEY = "authRef"

        const val SESSION_USERNAME = "freja-username"
        const val SESSION_STATUS = "freja-status"
        const val SESSION_NAME = "freja-name"
        const val SESSION_AUTH_REF = "freja-authref"
        const val SESSION_SIGN_REF = "freja-signref"
        const val SESSION_SURNAME = "freja-surname"
        const val SESSION_EMAIL = "freja-email"
        const val SESSION_DATE_OF_BIRTH = "freja-date-of-birth"
        const val SESSION_SSN = "freja-ssn"
        const val SESSION_COUNTRY = "freja-country"
        const val SESSION_RP_USER_ID = "freja-rp-user-id"
        const val SESSION_CUSTOM_IDENTIFIER = "freja-custom-identifier"
        const val SESSION_TIMESTAMP = "freja-timestamp"
        const val SESSION_INTEGRATOR_SPECIFIC_USER_ID = "freja-integrator-specific-user-id"
        const val QR_CODE = "_qrCode"
        const val CSP_OVERRIDE_IMG_SRC = "_cspImgsrc"
        const val THIS_DEVICE_LINK = "_thisDeviceLink"
    }
}