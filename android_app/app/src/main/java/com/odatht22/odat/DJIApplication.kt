package com.odatht22.odat

import android.app.Application
import android.content.Context
import com.secneo.sdk.Helper

class DJIApplication: Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Helper.install(this)
    }
}