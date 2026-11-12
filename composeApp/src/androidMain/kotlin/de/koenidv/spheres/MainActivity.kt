package de.koenidv.spheres

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import de.koenidv.spheres.platform.AndroidPlatformContext
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(this)
        handleAuthIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidPlatformContext.currentActivity = this
        handleAuthIntent(intent)
        if (AndroidPlatformContext.pendingAuthContinuation != null) {
            lifecycleScope.launch {
                delay(800)
                val cont = AndroidPlatformContext.pendingAuthContinuation
                if (cont != null && cont.isActive) {
                    AndroidPlatformContext.pendingAuthContinuation = null
                    cont.resumeWithException(Exception("Sign-in cancelled"))
                }
            }
        }
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme?.startsWith("com.googleusercontent.apps.") == true) {
            AndroidPlatformContext.onAuthCallback(data.toString())
            intent.data = null
        }
    }
}
