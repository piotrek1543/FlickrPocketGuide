package com.piotrek1543.example.flickrpocketguide.ui.photos

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.piotrek1543.example.flickrpocketguide.Constants
import com.piotrek1543.example.flickrpocketguide.R
import com.piotrek1543.example.flickrpocketguide.ui.service.LocationService
import com.piotrek1543.example.flickrpocketguide.ui.utils.GpsUtils
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


class PhotosActivity : AppCompatActivity() {

    private lateinit var photosViewModel: PhotosViewModel
    private lateinit var adapter: PhotosAdapter
    private lateinit var gpsUtils: GpsUtils
    private var menuItem: MenuItem? = null
    private var isGPSEnabled = MutableLiveData<Boolean>()
    private val isRunningData = MutableLiveData<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = ""

        photosViewModel = ViewModelProviders.of(this).get(PhotosViewModel::class.java)

        gpsUtils = GpsUtils(this)
        isGPSEnabled.observe(this, Observer {
            if (it) startService() else stopService()
        })

        adapter = PhotosAdapter(context = this@PhotosActivity)
        recycler_locations.adapter = adapter

        isRunningData.observe(this, Observer { isServiceRunning ->
            val stringId = if (isServiceRunning) R.string.action_track_stop else R.string.action_track_start
            menuItem?.title = resources.getString(stringId)
        })

        photosViewModel.photosLiveData.observe(this, Observer {
            adapter.items = it
            adapter.notifyDataSetChanged()
        })
    }

    private fun registerTrackingServiceReceiver() {
        val serviceFilter = IntentFilter()
        serviceFilter.addAction(ACTION_TRACKING)
        registerReceiver(trackingServiceReceiver, serviceFilter)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_track_activity) {
            val isRunning = isRunningData.value ?: false
            val isGPSEnabled = isGPSEnabled.value ?: gpsUtils.isProviderEnabled()

            if (!isGPSEnabled) gpsUtils.turnGPSOn() else if (isRunning) stopService() else startService()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_track, menu)
        menuItem = menu.findItem(R.id.action_track_activity)

        return true
    }

    private fun startService() {
        if (ActivityCompat.checkSelfPermission(
                this@PhotosActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this@PhotosActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@PhotosActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                Constants.LOCATION_REQUEST
            )

        } else {
            registerTrackingServiceReceiver()

            val serviceIntent = Intent(this, LocationService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            isRunningData.postValue(true)
        }
    }

    private fun stopService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
        isRunningData.postValue(false)
        unregisterReceiver(trackingServiceReceiver)
    }

    private val trackingServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(LocationService.ARG_LOCATION) ?: return
            photosViewModel.fetchPhotos(lat = location.latitude, lon = location.longitude)
        }
    }

    companion object {
        const val ACTION_TRACKING = "ACTION_TRACKING"
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1000 -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    isGPSEnabled.value = true
                } else {
                    Toast.makeText(this, getString(R.string.warning_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.GPS_REQUEST) {
            isGPSEnabled.postValue(true) // flag maintain before get location
        }
    }
}
