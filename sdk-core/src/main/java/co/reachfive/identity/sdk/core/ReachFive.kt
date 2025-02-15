package co.reachfive.identity.sdk.core

import android.app.Activity
import android.content.Intent
import android.util.Log
import co.reachfive.identity.sdk.core.api.ReachFiveApi
import co.reachfive.identity.sdk.core.api.ReachFiveApiCallback
import co.reachfive.identity.sdk.core.models.AuthToken
import co.reachfive.identity.sdk.core.models.ReachFiveError
import co.reachfive.identity.sdk.core.models.SdkConfig
import co.reachfive.identity.sdk.core.models.responses.ClientConfigResponse
import co.reachfive.identity.sdk.core.utils.Failure
import co.reachfive.identity.sdk.core.utils.Success

class ReachFive private constructor(
    private val reachFiveApi: ReachFiveApi,
    private val passwordAuth: PasswordAuthClient,
    private val passwordlessAuth: PasswordlessAuthClient,
    private val profileManagement: ProfileManagementClient,
    private val socialLoginAuth: SocialLoginAuthClient,
    private val webauthnAuth: WebauthnAuthClient,
    private val sessionUtils: SessionUtilsClient,
    override val sdkConfig: SdkConfig,
    override var defaultScope: Set<String> = emptySet(),
) :
    PasswordAuth by passwordAuth,
    PasswordlessAuth by passwordlessAuth,
    ProfileManagement by profileManagement,
    SocialLoginAuth by socialLoginAuth,
    WebauthnAuth by webauthnAuth,
    SessionUtils by sessionUtils {

    companion object {
        const val TAG = "Reach5"

        operator fun invoke(
            sdkConfig: SdkConfig,
            providersCreators: List<ProviderCreator>,
        ): ReachFive {
            val reachFiveApi: ReachFiveApi = ReachFiveApi.create(sdkConfig)
            val webLauncher = RedirectionActivityLauncher(sdkConfig, reachFiveApi)
            val sessionUtils =
                SessionUtilsClient(reachFiveApi, sdkConfig, webLauncher)

            val passwordAuthClient = PasswordAuthClient(sdkConfig, reachFiveApi, sessionUtils)
            val passwordlessAuthClient = PasswordlessAuthClient(reachFiveApi, sdkConfig)
            val profileManagementClient = ProfileManagementClient(reachFiveApi)
            val socialLoginAuthClient =
                SocialLoginAuthClient(reachFiveApi, providersCreators, sessionUtils)
            val webauthnAuthClient =
                WebauthnAuthClient(reachFiveApi, sdkConfig, sessionUtils)

            return ReachFive(
                reachFiveApi,
                passwordAuthClient,
                passwordlessAuthClient,
                profileManagementClient,
                socialLoginAuthClient,
                webauthnAuthClient,
                sessionUtils,
                sdkConfig,
            )
        }
    }

    fun initialize(
        success: Success<Unit> = {},
        failure: Failure<ReachFiveError> = {}
    ): ReachFive {
        reachFiveApi
            .clientConfig(mapOf("client_id" to sdkConfig.clientId))
            .enqueue(
                ReachFiveApiCallback.withContent<ClientConfigResponse>(
                    success = { clientConfig ->
                        defaultScope = clientConfig.scope.split(" ").toSet()
                        success(Unit)
                    },
                    failure = failure
                )
            )

        return this
    }

    fun onStop() = socialLoginAuth.onStop()

    fun logout(
        success: Success<Unit>,
        @Suppress("UNUSED_PARAMETER") failure: Failure<ReachFiveError>
    ) {
        socialLoginAuth.logoutFromAll()
        success(Unit)
    }

    fun onAddNewWebAuthnDeviceResult(
        requestCode: Int,
        intent: Intent?,
        success: Success<Unit>,
        failure: Failure<ReachFiveError>,
    ) {
        if (requestCode == WebauthnAuth.RC_REGISTER_DEVICE) {
            if (intent != null)
                webauthnAuth.onAddNewWebAuthnDeviceResult(intent, success, failure)
            else
                failure(ReachFiveError.NullIntent)
        } else logNotReachFiveRequestCode(requestCode)
    }

    fun onLoginActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
        success: Success<AuthToken>,
        failure: Failure<ReachFiveError>,
        activity: Activity
    ) {
        when (requestCode) {
            WebauthnAuth.RC_LOGIN ->
                if (intent != null)
                    webauthnAuth.onLoginWithWebAuthnResult(
                        resultCode,
                        intent,
                        defaultScope,
                        success,
                        failure,
                    )
                else failure(ReachFiveError.NullIntent)

            WebauthnAuth.RC_SIGNUP -> {
                if (intent != null)
                    webauthnAuth.onSignupWithWebAuthnResult(
                        resultCode,
                        intent,
                        defaultScope,
                        success,
                        failure,
                        activity
                    )
                else failure(ReachFiveError.NullIntent)
            }

            else ->
                if (RedirectionActivity.isLoginRequestCode(requestCode)) {
                    if (intent != null)
                        sessionUtils.handleAuthorizationCompletion(intent, success, failure)
                    else
                        failure(ReachFiveError.NullIntent)
                } else if (socialLoginAuth.isSocialLoginRequestCode(requestCode)) {
                    socialLoginAuth.onActivityResult(
                        requestCode,
                        resultCode,
                        intent,
                        success,
                        failure
                    )
                } else logNotReachFiveRequestCode(requestCode)

        }
    }

    fun resolveResultHandler(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?
    ): ActivityResultHandler? {
        if (isReachFiveLoginRequestCode(requestCode))
            return LoginResultHandler(this, requestCode, resultCode, intent)
        else if (WebauthnAuth.isWebauthnActionRequestCode(requestCode)) {
            if (WebauthnAuth.RC_REGISTER_DEVICE == requestCode)
                return WebAuthnDeviceAddResult(this, requestCode, intent)
            else return null
        } else return null
    }

    fun isReachFiveLoginRequestCode(code: Int): Boolean =
        socialLoginAuth.isSocialLoginRequestCode(code) ||
                WebauthnAuth.isWebauthnLoginRequestCode(code) ||
                RedirectionActivity.isLoginRequestCode(code)

    fun isReachFiveActionRequestCode(code: Int): Boolean =
        WebauthnAuth.isWebauthnActionRequestCode(code)

    private fun logNotReachFiveRequestCode(code: Int) {
        Log.d(TAG, "Request code ${code} does not match any ReachFive actions.")
    }
}
