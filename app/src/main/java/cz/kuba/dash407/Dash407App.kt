package cz.kuba.dash407

import android.app.Application
import cz.kuba.dash407.data.Backup
import cz.kuba.dash407.data.CrashLog
import cz.kuba.dash407.data.NetQueue
import cz.kuba.dash407.data.Prefs

/**
 * Start procesu: nejdřív záznam pádů (aby zachytil i chyby při spuštění),
 * pak nastavení a hlídání internetu s frontou.
 */
class Dash407App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Prefs.init(this)
        NetQueue.init(this)
        Backup.init()
    }
}
