package ie.spnvoip.phone

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class SpnvoipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SipManager.init(this)

        // Low-power engine mode whenever no screen of ours is visible.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = SipManager.onAppForeground()
            override fun onStop(owner: LifecycleOwner) = SipManager.onAppBackground()
        })

        if (SipManager.account.value != null) SipService.start(this)
    }
}
