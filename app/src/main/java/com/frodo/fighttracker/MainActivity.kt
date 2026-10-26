//12.08.2026 14:17 - ostatnia zmiana przed ruszaniem guzika
package com.frodo.fighttracker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.frodo.fighttracker.databinding.ActivityMainBinding
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.Settings
import android.net.Uri
import coil.load
import coil.request.ImageRequest
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.TimePicker
import android.app.AlarmManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private var isFloatingEnabled = false
private lateinit var codexFragment: WebFragment

object SettingsStore {

    private const val KEY_ASSESS_DATABASE_URL =
        "assess_database_url"

    fun getAssessDatabaseUrl(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_ASSESS_DATABASE_URL,
                "local"
            )
            ?.trim()
            ?.ifBlank {
                "local"
            }
            ?: "local"
    }

    fun setAssessDatabaseUrl(
        context: Context,
        value: String
    ) {

        context
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_ASSESS_DATABASE_URL,
                value.trim()
                    .ifBlank {
                        "local"
                    }
            )
            .apply()
    }


    private const val KEY_HIGH_QUALITY_OCR = "high_quality_ocr"
    private const val KEY_LEGACY_OCR = "legacy_ocr"

    fun getLegacyOcr(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_LEGACY_OCR, false)
    }

    fun setLegacyOcr(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEGACY_OCR, value)
            .apply()
    }

    fun getHighQualityOcr(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIGH_QUALITY_OCR, false)
    }

    fun setHighQualityOcr(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIGH_QUALITY_OCR, value)
            .apply()
    }

    private const val KEY_ANGUISH_VERSION = "anguish_version"

    fun getAnguishVersion(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ANGUISH_VERSION, "1.0") ?: "1.0"
    }

    fun setAnguishVersion(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ANGUISH_VERSION, value)
            .apply()
    }

    private const val KEY_MATERIAL_HOUR = "material_hour"
    private const val KEY_MATERIAL_MIN = "material_min"

    fun getMaterialHour(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MATERIAL_HOUR, -1)
    }

    fun getMaterialMinute(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MATERIAL_MIN, -1)
    }

    fun setMaterialTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MATERIAL_HOUR, hour)
            .putInt(KEY_MATERIAL_MIN, minute)
            .apply()
    }
    private const val KEY_NOTIFY = "notify_materials"

    fun getMaterialNotifications(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFY, false)
    }

    fun setMaterialNotifications(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFY, value)
            .apply()
    }


    private const val KEY_AURA = "selected_aura"

    fun getSelectedAura(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_AURA, "song.webp") ?: "song.webp"
    }

    fun setSelectedAura(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AURA, value)
            .apply()
    }

    private const val PREF = "settings"

    private const val KEY_SCAN_INTERVAL = "scan_interval"
    private const val KEY_CODEX = "codex"

    fun getScanInterval(context: Context): Long {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_SCAN_INTERVAL, 500L) // default 0.5s
    }

    fun setScanInterval(context: Context, value: Long) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SCAN_INTERVAL, value)
            .apply()
    }

    fun getCodexType(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_CODEX, "AUSSIE") ?: "AUSSIE"
    }

    fun setCodexType(context: Context, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODEX, value)
            .apply()
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"

        const val ACTION_TOGGLE_ITEM_ASSESSMENT =
            "ACTION_TOGGLE_ITEM_ASSESSMENT"
    }


    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager

    private val captureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK &&
                result.data != null
            ) {

                val intent =
                    Intent(this, FightTrackerService::class.java)

                intent.putExtra("resultCode", result.resultCode)
                intent.putExtra("data", result.data)

                startForegroundService(intent)
                startService(Intent(this, OverlayService::class.java))

                // tracking is really running now
                TrackingController.isRunning = true
                recording = 1
            }
        }

    private val itemScanLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            Log.d(
                "ITEM_SCAN",
                "MediaProjection resultCode=${result.resultCode}"
            )

            Log.d(
                "ITEM_SCAN",
                "MediaProjection data=${result.data != null}"
            )

            if (
                result.resultCode != Activity.RESULT_OK ||
                result.data == null
            ) {

                Log.e(
                    "ITEM_SCAN",
                    "MediaProjection permission was not granted"
                )

                return@registerForActivityResult
            }

            val intent =
                Intent(
                    this,
                    ItemAssessmentCaptureService::class.java
                )

            intent.putExtra(
                ItemAssessmentCaptureService.EXTRA_RESULT_CODE,
                result.resultCode
            )

            intent.putExtra(
                ItemAssessmentCaptureService.EXTRA_DATA,
                result.data
            )

            Log.d(
                "ITEM_SCAN",
                "Starting ItemAssessmentCaptureService with MediaProjection data"
            )

            ContextCompat.startForegroundService(
                this,
                intent
            )
        }

    fun startCapture() {
        OrnaLauncher.launch(this)
        Handler(Looper.getMainLooper()).postDelayed({

            captureLauncher.launch(
                projectionManager.createScreenCaptureIntent()
            )

        }, 600)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        val openTab = intent.getIntExtra("open_tab", -1)
        if (openTab >= 0) {
            binding.viewPager.setCurrentItem(openTab, false)
        }

        handleIntentAction(intent)
    }

    /**
     * Handles actions sent by overlays / notifications.
     * Used both by onNewIntent (activity already alive)
     * and onCreate (activity had to be created first).
     */
    private fun handleIntentAction(intent: Intent) {

        when (intent.action) {

            ACTION_START_TRACKING -> {

                if (!TrackingController.isRunning) {

                    FightState.startTime = System.currentTimeMillis()
                    FightState.ornsConfirmationThreshold = 3_000_000L

                    startCapture()
                }
            }

            ACTION_STOP_TRACKING -> {
                stopTracking()
            }

            FloatingTrackerService.ACTION_START_ITEM_ASSESSMENT -> {

                startItemScan()
            }

        }
    }

    private fun startItemScan() {

        Log.d(
            "ITEM_SCAN",
            "Requesting MediaProjection for item scanner"
        )

        itemScanLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )
    }

    /**
     * Stops tracking, saves the run and closes the stats overlay.
     */
    fun stopTracking() {

        TrackingController.stopAndSave(
            this,
            closeOverlay = true
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        UpdateChecker.check(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val alarmManager =
                getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {

                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                )

                startActivity(intent)
            }
        }


        MaterialAlarmScheduler.schedule(this)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        ItemDatabaseManager.initialize(this)

        ItemAssessmentPipeline
            .ItemAssessmentManager
            .initialize(this)
        updateFloatingButton()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            } else {
                Log.d("Notifications", "checking materials")

            }

        } else {
            Log.d("Notifications", "checking materials")

        }

        if (FirstRunStore.shouldShow(this)) {
            startActivity(Intent(this, FirstRunActivity::class.java))
        }

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager


        val towerFragment =
            WebFragment.newInstance("https://codex.fqegg.top/#/tower")

        val monumentsFragment =
            WebFragment.newInstance("https://floorchart.top/")

        codexFragment =
            WebFragment.newInstance(getCodexUrl())


        val fragments = listOf(
            TrackerFragment(),
            MaterialsFragment(),
            codexFragment,
            monumentsFragment,
            towerFragment
        )

        val titles = listOf(
            "Tracker",
            "Materials",
            "Codex",
            "Monuments",
            "Towers"
        )
        binding.settingsFab.setOnClickListener {
            showSettingsDialog()
        }

        binding.fabFloatingToggle.setOnClickListener {

            if (!isFloatingEnabled) {

                if (!Settings.canDrawOverlays(this)) {

                    checkOverlayPermission()
                    return@setOnClickListener
                }

                startFloatingService()

                binding.fabFloatingToggle.setImageResource(
                    android.R.drawable.ic_media_pause
                )

                isFloatingEnabled = true

            } else {

                stopFloatingService()

                binding.fabFloatingToggle.setImageResource(
                    android.R.drawable.ic_media_play
                )

                isFloatingEnabled = false
            }
        }

        binding.viewPager.adapter =
            object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {

                override fun getItemCount() = fragments.size

                override fun createFragment(position: Int) =
                    fragments[position]
            }
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.offscreenPageLimit = 1

        com.google.android.material.tabs.TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager
        ) { tab, position ->

            tab.text = titles[position]

        }.attach()
        renderHistory()
        val openTab = intent.getIntExtra("open_tab", -1)
        if (openTab >= 0) {
            binding.viewPager.post {
                binding.viewPager.setCurrentItem(openTab, false)
            }
        }

        // Activity was created by an overlay action (e.g. Start button
        // pressed while the app was not alive) - handle it now.
        if (savedInstanceState == null) {
            binding.root.post {
                handleIntentAction(intent)
            }
        }

//        val pipeline =
//            ItemAssessmentPipeline(
//                this@MainActivity
//            )

//        pipeline.processAllExamples()
    }


    fun stopFloatingService() {

        stopService(
            Intent(
                this,
                FloatingTrackerService::class.java
            )
        )
    }

    fun getCodexUrl(): String {
        return if (SettingsStore.getCodexType(this) == "AUSSIE") {
            "https://www.aussiescodex.com/orna-items"
        } else {
            "https://codex.fqegg.top/#/"
        }
    }

    private fun updateFloatingButton() {

        if (FloatingTrackerService.isRunning) {

            binding.fabFloatingToggle.setImageResource(
                android.R.drawable.ic_media_pause
            )

        } else {

            binding.fabFloatingToggle.setImageResource(
                android.R.drawable.ic_media_play
            )
        }
    }
    private fun renderHistory() {



        val runs = RunStorage.loadRuns(this)

        for (run in runs) {

            val tv = TextView(this)

            val gold =
                run.gold.toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(" ")
                    .reversed()

            val orns =
                run.orns.toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(" ")
                    .reversed()

            val exp =
                run.exp.toString()
                    .reversed()
                    .chunked(3)
                    .joinToString(" ")
                    .reversed()

            tv.text =
                "${run.date}\n" +
                        "Time: ${run.duration}\n" +
                        "Gold: $gold\n" +
                        "Orns: $orns\n" +
                        "EXP: $exp"

            tv.textSize = 16f

            tv.setPadding(0, 0, 0, 40)


        }
    }
    private fun showSettingsDialog() {



        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val timePicker =
            dialogView.findViewById<TimePicker>(R.id.materialTimePicker)
        timePicker.setIs24HourView(true)
        val savedHour = SettingsStore.getMaterialHour(this)
        val savedMinute = SettingsStore.getMaterialMinute(this)

        val calendar = java.util.Calendar.getInstance()

        val hour = if (savedHour == -1) calendar.get(java.util.Calendar.HOUR_OF_DAY) else savedHour
        val minute = if (savedMinute == -1) calendar.get(java.util.Calendar.MINUTE) else savedMinute

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.hour = hour
            timePicker.minute = minute
        } else {
            timePicker.currentHour = hour
            timePicker.currentMinute = minute
        }

        val notifyCheck =
            dialogView.findViewById<android.widget.CheckBox>(
                R.id.materialNotifyCheck
            )

        val highQualityCheck =
            dialogView.findViewById<android.widget.CheckBox>(
                R.id.highQualityOcrCheck
            )
        val legacyOcrCheck =
            dialogView.findViewById<android.widget.CheckBox>(
                R.id.legacyOcrCheck
            )
        val shareLogsButton =
            dialogView.findViewById<android.widget.Button>(
                R.id.shareLogsButton
            )

        shareLogsButton.setOnClickListener {

            val file = TrackerLogger.getFile(this)

            if (file != null && file.exists()) {

                val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        file
                    )

                val share =
                    Intent(Intent.ACTION_SEND)

                share.type = "text/plain"
                share.putExtra(Intent.EXTRA_STREAM, uri)
                share.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                startActivity(
                    Intent.createChooser(
                        share,
                        "Share Fight Tracker Logs"
                    )
                )
            }
        }
        highQualityCheck.isChecked =
            SettingsStore.getHighQualityOcr(this)

        legacyOcrCheck.isChecked =
            SettingsStore.getLegacyOcr(this)

        notifyCheck.isChecked =
            SettingsStore.getMaterialNotifications(this)

        val auraSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.auraSpinner)
        val auraPreview = dialogView.findViewById<android.widget.ImageView>(R.id.auraPreview)

        val auraFiles = assets.list("aura")
            ?.filter { it.endsWith(".webp") }
            ?: emptyList()

        val scanInput = dialogView.findViewById<android.widget.EditText>(R.id.scanIntervalInput)
        val codexGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.codexGroup)

        val assessDatabaseUrlInput =
            dialogView.findViewById<android.widget.EditText>(
                R.id.assessDatabaseUrlInput
            )

        assessDatabaseUrlInput.setText(
            SettingsStore.getAssessDatabaseUrl(this)
        )

        scanInput.setText(SettingsStore.getScanInterval(this).toString())

        val current = SettingsStore.getCodexType(this)
        if (current == "AUSSIE") {
            codexGroup.check(R.id.radioAussie)
        } else {
            codexGroup.check(R.id.radioYaco)
        }
        val anguishGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.anguishGroup)


        when (SettingsStore.getAnguishVersion(this)) {
            "2.0" -> anguishGroup.check(R.id.radioAnguish2)
            else -> anguishGroup.check(R.id.radioAnguish1)
        }

        // list aura

        val auraList = assets.list("aura")?.filter { it.endsWith(".webp") } ?: listOf()

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            auraList
        )

        auraSpinner.adapter = adapter


        // load saved aura

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedAura = prefs.getString("selected_aura", "") ?: ""

        if (savedAura.isNotEmpty()) {
            val index = auraList.indexOf(savedAura)
            if (index >= 0) auraSpinner.setSelection(index)
        }

        // update preview


        fun updatePreview(name: String) {
            auraPreview.load("file:///android_asset/aura/$name") {
                crossfade(false)
                allowHardware(false)
                memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            }
        }

        if (savedAura.isNotEmpty()) {
            updatePreview(savedAura)
        }

        auraSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val selected = auraList[position]
                    updatePreview(selected)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        // save settings

        android.app.AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->

                val anguishVersion = when (anguishGroup.checkedRadioButtonId) {
                    R.id.radioAnguish2 -> "2.0"
                    else -> "1.0"
                }

                SettingsStore.setAnguishVersion(this, anguishVersion)

                val assessDatabaseUrl =
                    assessDatabaseUrlInput.text
                        .toString()
                        .trim()
                        .ifBlank {
                            "local"
                        }

                SettingsStore.setAssessDatabaseUrl(
                    this,
                    assessDatabaseUrl
                )

                ItemDatabaseManager.setDatabaseUrl(
                    this,
                    assessDatabaseUrl
                )

                val hour = if (Build.VERSION.SDK_INT >= 23)
                    timePicker.hour
                else
                    timePicker.currentHour

                val minute = if (Build.VERSION.SDK_INT >= 23)
                    timePicker.minute
                else
                    timePicker.currentMinute

                SettingsStore.setMaterialTime(this, hour, minute)

                val interval = scanInput.text.toString().toLongOrNull() ?: 500L
                SettingsStore.setScanInterval(this, interval)

                SettingsStore.setMaterialNotifications(
                    this,
                    notifyCheck.isChecked
                )
                SettingsStore.setHighQualityOcr(
                    this,
                    highQualityCheck.isChecked
                )
                SettingsStore.setLegacyOcr(
                    this,
                    legacyOcrCheck.isChecked
                )
                val oldCodex = SettingsStore.getCodexType(this)

                val selectedCodex = when (codexGroup.checkedRadioButtonId) {
                    R.id.radioAussie -> "AUSSIE"
                    else -> "YACO"
                }

                SettingsStore.setCodexType(this, selectedCodex)

                if (oldCodex != selectedCodex) {
                    codexFragment.reloadUrl(getCodexUrl())
                }

                val selectedAura = auraSpinner.selectedItem as String
                prefs.edit().putString("selected_aura", selectedAura).apply()
                AuraNotifier.refresh?.invoke()

                AuraState.lastUpdateTime = System.currentTimeMillis()
                MaterialAlarmScheduler.schedule(this)
            }

            .setNegativeButton("Cancel", null)
            .show()
    }

    fun checkOverlayPermission() {

        if (!Settings.canDrawOverlays(this)) {

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)
        }
    }

    fun startFloatingService() {

        startService(
            Intent(
                this,
                FloatingTrackerService::class.java
            )
        )
    }
}