package sh.margot.open_koodo

import android.app.Application
import com.google.android.material.color.DynamicColors
import sh.margot.open_koodo.network.KoodoApiClient

class OpenKoodoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KoodoApiClient.init(this)
        // Apply Material You wallpaper-based colors on Android 12+.
        // Falls back to the orange theme on older devices.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
