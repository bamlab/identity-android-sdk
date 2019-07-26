package com.reach5.identity.sdk.facebook

import android.app.Activity
import android.content.Intent
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.reach5.identity.sdk.core.Provider
import com.reach5.identity.sdk.core.ProviderCreator
import com.reach5.identity.sdk.core.api.ReachFiveApi
import com.reach5.identity.sdk.core.api.ReachFiveApiCallback
import com.reach5.identity.sdk.core.models.*
import com.reach5.identity.sdk.core.models.requests.LoginProviderRequest
import com.reach5.identity.sdk.core.utils.Failure
import com.reach5.identity.sdk.core.utils.Success

class FacebookProvider : ProviderCreator {
    companion object {
        const val NAME = "facebook"
    }

    override val name: String = NAME

    override fun create(
        providerConfig: ProviderConfig,
        sdkConfig: SdkConfig,
        reachFiveApi: ReachFiveApi,
        activity: Activity
    ): Provider {
        return ConfiguredFacebookProvider(providerConfig, sdkConfig, reachFiveApi, activity)
    }
}

class ConfiguredFacebookProvider(
    private val providerConfig: ProviderConfig,
    val sdkConfig: SdkConfig,
    val reachFiveApi: ReachFiveApi,
    activity: Activity
) : Provider {
    override val requestCode: Int = 64206
    override val name: String = FacebookProvider.NAME

    private lateinit var origin: String

    private val callbackManager = CallbackManager.Factory.create()

    init {
        FacebookSdk.setApplicationId(providerConfig.clientId)
        // FIXME resolve deprecation
        @Suppress("DEPRECATION")
        FacebookSdk.sdkInitialize(activity.applicationContext)
    }

    override fun login(origin: String, activity: Activity) {
        this.origin = origin
        LoginManager.getInstance().logInWithReadPermissions(activity, providerConfig.scope)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        success: Success<AuthToken>,
        failure: Failure<ReachFiveError>
    ) {
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult?) {
                val accessToken = result?.accessToken?.token
                if (accessToken != null) {
                    val loginProviderRequest = LoginProviderRequest(
                        provider = name,
                        providerToken = accessToken,
                        clientId = sdkConfig.clientId,
                        origin = origin,
                        scope = (providerConfig.scope ?: setOf()).joinToString { " " }.plus("openid")
                    )
                    reachFiveApi.loginWithProvider(loginProviderRequest, SdkInfos.getQueries()).enqueue(
                        ReachFiveApiCallback(success = { it.toAuthToken().fold(success, failure) }, failure = failure)
                    )
                } else {
                    failure(ReachFiveError.from("Facebook didn't return an access token!"))
                }
            }

            override fun onCancel() {
                failure(ReachFiveError.from("User cancel")) // TODO is it a real error or we do nothing ?
            }

            override fun onError(error: FacebookException?) {
                if (error != null) {
                    failure(ReachFiveError.from(error))
                } else {
                    failure(ReachFiveError.from("Technical error"))
                }
            }
        })
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        failure: Failure<ReachFiveError>
    ) {
        // Do nothing
    }
}
