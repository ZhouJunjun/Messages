package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.messages.extensions.config

class SplashActivity : BaseSplashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Commons' sideloading probe misfires on this fork and pops a "download the original
        // app" dialog. Write the cached "not sideloaded" state before the base class reads it,
        // so the dialog can never show up even if something resets it.
        config.appSideloadingStatus = 2
        super.onCreate(savedInstanceState)
    }

    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
