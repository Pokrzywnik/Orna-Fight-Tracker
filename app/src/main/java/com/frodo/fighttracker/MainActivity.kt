package com.frodo.fighttracker

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.frodo.fighttracker.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.Configuration

object SettingsStore {

    private const val KEY_UI_SCALE = "ui_scale"

    /** Interface scale in percent: 60..140, step 20, default 100. */
    fun getUiScale(context: Context): Int {
        val v = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_UI_SCALE, 100)
        return v.coerceIn(60, 140)
    }

    fun setUiScale(context: Context, value: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_UI_SCALE, value.coerceIn(60, 140))
            .apply()
    }

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
    private const val KEY_EXPERIMENTAL_OCR = "experimental_ocr"

    /** PaddleOCR reward scanner. Overrides the legacy OCR setting. */
    fun getExperimentalOcr(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXPERIMENTAL_OCR, false)
    }

    fun setExperimentalOcr(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXPERIMENTAL_OCR, value)
            .apply()
    }

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

    /** "AUSSIE" or "YACO". Now chosen with the segmented control in the Codex tab. */
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

        /** Page order == bottom navigation order == "open_tab" index. */
        private val PAGE_IDS = intArrayOf(
            R.id.nav_tracker,
            R.id.nav_materials,
            R.id.nav_codex,
            R.id.nav_gleaming,
            R.id.nav_guides
        )
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

        // notifications ask for a tab by index (1 = Materials)
        val openTab = intent.getIntExtra("open_tab", -1)
        if (openTab in PAGE_IDS.indices) {
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

    override fun attachBaseContext(newBase: Context) {

        val scale = SettingsStore.getUiScale(newBase)

        if (scale == 100) {
            super.attachBaseContext(newBase)
            return
        }

        // dp, sp and WebView content all follow densityDpi
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = (config.densityDpi * scale / 100f).toInt()

        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // transient status bar (swipe from the top edge), as before
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupInsets()

        ItemDatabaseManager.initialize(this)

        ItemAssessmentPipeline
            .ItemAssessmentManager
            .initialize(this)

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

        setupPagerAndNavigation()

        val openTab = intent.getIntExtra("open_tab", -1)
        if (openTab in PAGE_IDS.indices) {
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
    }

    override fun onResume() {
        super.onResume()
        refreshBadges()
    }

    // ------------------------------------------------------------------
    // layout: insets, pager, bottom navigation
    // ------------------------------------------------------------------

    /**
     * - top: only the display cutout (the status bar is hidden / transient)
     * - bottom: the navigation-bar inset goes on the nav container, so the
     *   bar's background extends under the system navigation
     * - keyboard: the page shrinks and the bottom bar steps aside
     */
    private fun setupInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val keyboardUp = ime.bottom > 0

            binding.root.setPadding(
                cutout.left,
                cutout.top,
                cutout.right,
                if (keyboardUp) ime.bottom else 0
            )

            binding.navContainer.setPadding(
                0,
                0,
                0,
                if (keyboardUp) 0 else nav.bottom
            )

            binding.navContainer.visibility =
                if (keyboardUp) android.view.View.GONE else android.view.View.VISIBLE

            insets
        }
    }

    private fun setupPagerAndNavigation() {

        binding.viewPager.adapter =
            object : FragmentStateAdapter(this) {

                override fun getItemCount() = PAGE_IDS.size

                override fun createFragment(position: Int) =
                    when (position) {
                        0 -> TrackerFragment()
                        1 -> MaterialsFragment()
                        2 -> CodexFragment()
                        3 -> GleamingFragment()
                        else -> GuidesFragment()
                    }
            }

        // navigation is done with the bottom bar only (no swiping, so the
        // web pages can scroll sideways without changing tabs)
        binding.viewPager.isUserInputEnabled = false

        // keep every page alive: web pages keep their state when switching tabs
        binding.viewPager.offscreenPageLimit = PAGE_IDS.size - 1

        binding.bottomNav.setOnItemSelectedListener { item ->

            val index = PAGE_IDS.indexOf(item.itemId)

            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, false)
            }

            index >= 0
        }

        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(position: Int) {

                    binding.bottomNav.selectedItemId = PAGE_IDS[position]

                    // the run card is expanded each time the Tracker tab is shown
                    if (position == 0) {
                        supportFragmentManager.fragments
                            .filterIsInstance<TrackerFragment>()
                            .firstOrNull()
                            ?.onTabSelected()
                    }
                }
            }
        )
    }

    /**
     * Small accent dot on "Materials" when one of the SELECTED materials
     * is available today.
     */
    fun refreshBadges() {

        if (!::binding.isInitialized) return

        val available = try {
            MaterialAvailability.hasSelectedMaterialToday(this)
        } catch (e: Exception) {
            false
        }

        if (available) {

            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_materials)

            badge.clearNumber()
            badge.backgroundColor = ContextCompat.getColor(this, R.color.ft_accent)
            badge.isVisible = true

        } else {

            binding.bottomNav.removeBadge(R.id.nav_materials)
        }
    }

    // ------------------------------------------------------------------
    // floating overlay
    // ------------------------------------------------------------------

    fun stopFloatingService() {

        stopService(
            Intent(
                this,
                FloatingTrackerService::class.java
            )
        )
    }

    /** Used by the "Overlay" pill in the Tracker header. */
    fun toggleFloatingService() {

        if (FloatingTrackerService.isRunning) {

            stopFloatingService()

        } else {

            if (!Settings.canDrawOverlays(this)) {

                checkOverlayPermission()
                return
            }

            startFloatingService()
        }
    }

    // ------------------------------------------------------------------
    // settings
    // ------------------------------------------------------------------

    fun showSettingsDialog() {

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
        val experimentalOcrCheck =
            dialogView.findViewById<android.widget.CheckBox>(
                R.id.experimentalOcrCheck
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
                        getString(R.string.ft_share_logs_chooser)
                    )
                )
            }
        }
        highQualityCheck.isChecked =
            SettingsStore.getHighQualityOcr(this)

        legacyOcrCheck.isChecked =
            SettingsStore.getLegacyOcr(this)

        experimentalOcrCheck.isChecked =
            SettingsStore.getExperimentalOcr(this)

        // experimental mode overrides legacy OCR -> grey it out while it is on
        legacyOcrCheck.isEnabled = !experimentalOcrCheck.isChecked

        experimentalOcrCheck.setOnCheckedChangeListener { _, checked ->
            legacyOcrCheck.isEnabled = !checked
        }

        notifyCheck.isChecked =
            SettingsStore.getMaterialNotifications(this)

        val auraSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.auraSpinner)
        val auraPreview = dialogView.findViewById<android.widget.ImageView>(R.id.auraPreview)

        val scanInput = dialogView.findViewById<android.widget.EditText>(R.id.scanIntervalInput)

        val uiScaleSlider =
            dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.uiScaleSlider)
        val uiScaleLabel =
            dialogView.findViewById<android.widget.TextView>(R.id.uiScaleLabel)

        val currentUiScale = SettingsStore.getUiScale(this)

        fun updateUiScaleLabel(v: Int) {
            uiScaleLabel.text =
                getString(R.string.ft_ui_scale) + ": " + getString(R.string.ft_ui_scale_value, v)
        }

        uiScaleSlider.value = currentUiScale.toFloat()
        updateUiScaleLabel(currentUiScale)

        uiScaleSlider.addOnChangeListener { _, value, _ ->
            updateUiScaleLabel(value.toInt())
        }

        val assessDatabaseUrlInput =
            dialogView.findViewById<android.widget.EditText>(
                R.id.assessDatabaseUrlInput
            )

        assessDatabaseUrlInput.setText(
            SettingsStore.getAssessDatabaseUrl(this)
        )

        scanInput.setText(SettingsStore.getScanInterval(this).toString())

        // (the Codex source radio group moved to the Codex tab)

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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ft_settings_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ft_save) { _, _ ->

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
                SettingsStore.setExperimentalOcr(
                    this,
                    experimentalOcrCheck.isChecked
                )

                val selectedAura = auraSpinner.selectedItem as? String
                if (selectedAura != null) {
                    prefs.edit().putString("selected_aura", selectedAura).apply()
                }
                AuraNotifier.refresh?.invoke()

                AuraState.lastUpdateTime = System.currentTimeMillis()
                MaterialAlarmScheduler.schedule(this)

                // the Anguish version changes the proofs and the materials list
                supportFragmentManager.fragments
                    .filterIsInstance<TrackerFragment>()
                    .firstOrNull()
                    ?.refreshProofViews()

                refreshBadges()
                val newUiScale = uiScaleSlider.value.toInt()

                if (newUiScale != currentUiScale) {
                    SettingsStore.setUiScale(this, newUiScale)
                    recreate()
                }
            }

            .setNegativeButton(R.string.ft_cancel, null)
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
