package com.kostaking.raspored

import android.app.Application
import com.kostaking.raspored.alarm.NotificationHelper

/** Robolectric application: in-memory database, no background observers. */
class TestApp : Application(), ContainerHolder {
    override val container: AppContainer by lazy { AppContainer(this, inMemoryDb = true) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
