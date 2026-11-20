package app.contrail

import android.app.Application
import app.contrail.data.FlightRepository
import app.contrail.data.Settings
import app.contrail.work.Notifier
import app.contrail.work.RefreshWorker

class ContrailApp : Application() {
    lateinit var repository: FlightRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FlightRepository(this, Settings(this))
        Notifier.createChannel(this)
        RefreshWorker.schedule(this)
    }
}
