package naihe2010.csplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.C

import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider


import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var playerService: PlayerService? = null
    private var isBound = false


    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlayerService.PlayerBinder
            playerService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }


    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnRewind: MaterialButton
    private lateinit var btnForward: MaterialButton
    private lateinit var sliderProgress: Slider
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permission results
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // Permissions granted, proceed with app logic
                // You might want to reload HomeFragment or trigger some action here
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                // Permissions denied, show a message or disable functionality
                Toast.makeText(this, "存储权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG)
                    .show()
                Log.e("MainActivity", "Permissions denied: $permissions")
            }
        }

    private val playerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlayerService.ACTION_STATE_CHANGED) {
                val isPlaying = intent.getBooleanExtra(PlayerService.EXTRA_IS_PLAYING, false)
                val title = intent.getStringExtra(PlayerService.EXTRA_TITLE)
                val duration = intent.getLongExtra(PlayerService.EXTRA_DURATION, 0)
                val currentPosition = intent.getLongExtra(PlayerService.EXTRA_CURRENT_POSITION, 0)
                updateUI(isPlaying, title, duration, currentPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupListeners()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        checkAndRequestPermissions()

        Intent(this, PlayerService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(playerStateReceiver, IntentFilter(PlayerService.ACTION_STATE_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun setupViews() {
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRewind = findViewById(R.id.btnRewind)
        btnForward = findViewById(R.id.btnForward)
        sliderProgress = findViewById(R.id.sliderProgress)
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvDuration = findViewById(R.id.tvDuration)
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { sendControlBroadcast(PlayerService.ACTION_TOGGLE_PLAY_PAUSE) }
        btnRewind.setOnClickListener { sendControlBroadcast(PlayerService.ACTION_REWIND) }
        btnForward.setOnClickListener { sendControlBroadcast(PlayerService.ACTION_FORWARD) }

        findViewById<MaterialButton>(R.id.btnHome).setOnClickListener {
            navigateToFragment(HomeFragment())
        }
        findViewById<MaterialButton>(R.id.btnDirectory).setOnClickListener {
            navigateToFragment(DirectoryFragment())
        }
        findViewById<MaterialButton>(R.id.btnSetting).setOnClickListener {
            navigateToFragment(SettingFragment())
        }
        findViewById<MaterialButton>(R.id.btnExit).setOnClickListener {
            if (isBound) {
                playerService?.stopService()
                unbindService(connection)
                isBound = false
            }
            finishAffinity()
        }
    }

    private fun updateUI(
        isPlaying: Boolean,
        title: String?,
        duration: Long,
        currentPosition: Long
    ) {
        btnPlayPause.setIconResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        tvTitle.text = title ?: "CSPlayer"

        if (duration == C.TIME_UNSET || duration == 0L) {
            tvDuration.text = "00:00"
            sliderProgress.valueTo = 1f // Set to a small positive value to avoid crash
        } else {
            tvDuration.text = formatMillis(duration)
            sliderProgress.valueTo = duration.toFloat()
        }
        tvCurrentTime.text = formatMillis(currentPosition)
        sliderProgress.value = currentPosition.toFloat()
    }

    private fun formatMillis(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        )
    }

    private fun sendControlBroadcast(action: String) {
        val intent = Intent(action)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun navigateToPlaylist(directoryPath: String) {
        val playerConfig = PlayerConfig.getInstance(this)
        playerConfig.updateCurrentDirectory(directoryPath).save(this)
        navigateToFragment(PlaylistFragment.newInstance(directoryPath))
    }

    private fun navigateToFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        val permissionsPermanentlyDenied = mutableListOf<String>()

        val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        for (permission in permissionsToCheck) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(permission)) {
                    permissionsNeeded.add(permission)
                } else {
                    permissionsPermanentlyDenied.add(permission)
                }
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else if (permissionsPermanentlyDenied.isNotEmpty()) {
            Toast.makeText(this, "存储权限已被永久拒绝，请在应用设置中手动授予。", Toast.LENGTH_LONG)
                .show()
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }
}