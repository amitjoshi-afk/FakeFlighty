package app.contrail

import android.app.Application
import app.contrail.data.FlightRepository
import app.contrail.data.Settings

class ContrailApp : Application() {
    lateinit var repository: FlightRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FlightRepository(this, Settings(this))
    }
}
