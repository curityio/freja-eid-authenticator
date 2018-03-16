package io.curity.identityserver.plugin.config;

import se.curity.identityserver.sdk.config.Configuration
import se.curity.identityserver.sdk.service.SessionManager

interface VerisecAuthenticatorPluginConfig : Configuration
{
    val sessionManager : SessionManager
}
