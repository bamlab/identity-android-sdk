package co.reachfive.identity.sdk.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsIntent
import co.reachfive.identity.sdk.core.ReachFive.Companion.TAG

class RedirectionActivity : Activity() {
    companion object {
        const val FQN = "co.reachfive.identity.sdk.core.RedirectionActivity"
        const val CODE_VERIFIER_KEY = "CODE_VERIFIER"
        const val URL_KEY = "URL"
        const val SCHEME = "SCHEME"

        const val RC_WEBLOGIN = 52557

        fun isLoginRequestCode(code: Int): Boolean =
            setOf(RC_WEBLOGIN).contains(code)
    }

    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val urlString = intent.getStringExtra(URL_KEY)
        val codeVerifier = intent.getStringExtra(CODE_VERIFIER_KEY)

        val customTabsIntent = CustomTabsIntent.Builder().build().intent
        customTabsIntent.data = Uri.parse(urlString)
        customTabsIntent.putExtra(CODE_VERIFIER_KEY, codeVerifier)

        startActivity(customTabsIntent)
    }

    override fun onNewIntent(newIntent: Intent) {
        // check if the originating Activity is from trusted package
        val className = this.callingActivity?.className;
        val packageName = this.callingActivity?.packageName;
        Log.d(TAG, "onNewIntent - class: $className")
        Log.d(TAG, "onNewIntent - package: $packageName")
        if (className == FQN) {
            // extract the nested Intent
            val forward = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.intent.getParcelableExtra(SCHEME, Intent::class.java) // redirect the nested Intent
            } else {
                @Suppress("DEPRECATION")
                this.intent.getParcelableExtra(SCHEME)
            }
            Log.d(TAG, "onNewIntent - redirect the nested Intent: $forward")

            this.startActivity(forward);
        }
    }

    override fun onResume() {
        super.onResume()

        if (!started) {
            started = true
        } else {
            finish()
        }
    }
}