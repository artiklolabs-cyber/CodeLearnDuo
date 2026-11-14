package com.codelearn.duo

import android.app.Application
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class CodeLearnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
        } catch (t: Throwable) {
            Log.e("CodeLearnApp", "Python runtime failed to start", t)
        }
    }
}
