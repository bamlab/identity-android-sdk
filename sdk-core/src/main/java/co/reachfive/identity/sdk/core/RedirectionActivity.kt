package co.reachfive.identity.sdk.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNewIntent(newIntent: Intent) {
        val extras = newIntent.extras;
        Log.d(TAG, "onNewIntent - intent: $newIntent")
        Log.d(TAG, "onNewIntent - intent extras: $extras")

        // check if the originating Activity is from trusted package
        val className = this.callingActivity?.className;
        val packageName = this.callingActivity?.packageName;

        // com.cdapp.MainActivity
        Log.d(TAG, "onNewIntent - class: $className")

        // com.lumiplan.montagne.LesArcs.staging
        Log.d(TAG, "onNewIntent - package: $packageName")

        if (className == "com.cdapp.MainActivity") {
            newIntent.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            newIntent.removeFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            setResult(RESULT_OK, newIntent)
            finish()
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