package de.koenidv.spheres

import android.app.Application
import com.posthog.kmp.PostHog
import com.posthog.kmp.PostHogConfig
import com.posthog.kmp.PostHogContext
import de.koenidv.spheres.platform.PostHogConstants

class SpheresApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = PostHogConfig(
            projectToken = PostHogConstants.API_KEY,
            host = PostHogConstants.HOST,
            captureApplicationLifecycleEvents = true,
            captureScreenViews = false,
        )
        PostHog.setup(config, PostHogContext(this))
    }
}
