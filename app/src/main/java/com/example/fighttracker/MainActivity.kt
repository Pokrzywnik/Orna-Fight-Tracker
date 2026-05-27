package com.example.fighttracker

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
import com.example.fighttracker.databinding.ActivityMainBinding
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

private var isFloatingEnabled = false

object SettingsStore {
    private const val KEY_MATERIAL_HOUR = "material_hour"
    private const val KEY_MATERIAL_MIN = "material_min"

    fun getMaterialHour(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MATERIAL_HOUR, 7)
    }

    fun getMaterialMinute(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MATERIAL_MIN, 0)
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
            .getLong(KEY_SCAN_INTERVAL, 2500L) // default 2.5s
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
            }
        }

    fun startCapture() {

        captureLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        when (intent.action) {

            ACTION_START_TRACKING -> {
                FightState.startTime = System.currentTimeMillis()
                startCapture()
            }

            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
        }
    }

    fun stopTracking() {

        stopService(Intent(this, FightTrackerService::class.java))

        val endTime = System.currentTimeMillis()

        val durationMillis = endTime - FightState.startTime

        val seconds = durationMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        val duration =
            String.format(
                "%02d:%02d:%02d",
                hours,
                minutes % 60,
                seconds % 60
            )

        val date =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
            ).format(Date())

        val runid = System.currentTimeMillis().toString()

        RunStorage.saveRun(
            this,
            RunRecord(
                runid,
                date,
                duration,
                FightState.gold,
                FightState.orns,
                FightState.exp
            )
        )

        FightState.gold = 0L
        FightState.orns = 0L
        FightState.exp = 0L
    }

//    override fun onResume() {
//        super.onResume()
//
//        val fragments = listOf(
//            TrackerFragment(),
//            WebFragment("https://codex.fqegg.top/#/tower"),
//            WebFragment(getCodexUrl()),
//            MaterialsFragment()
//        )
//        binding.viewPager.adapter =
//            object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
//
//                override fun getItemCount() = fragments.size
//
//                override fun createFragment(position: Int) =
//                    fragments[position]
//            }
//    }
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        MaterialAlarmScheduler.schedule(this)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
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


        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val fragments = listOf(
            TrackerFragment(),
            WebFragment.newInstance("https://codex.fqegg.top/#/tower"),
            WebFragment.newInstance(getCodexUrl()),
            MaterialsFragment()
        )

        val titles = listOf(
            "Tracker",
            "Towers",
            "Codex",
            "Materials"
        )
        binding.settingsFab.setOnClickListener {
            showSettingsDialog()
        }

        binding.fabFloatingToggle.setOnClickListener {
            checkOverlayPermission()

            if (!isFloatingEnabled) {
                startFloatingService()
                binding.fabFloatingToggle.setImageResource(android.R.drawable.ic_media_pause)
                isFloatingEnabled = true
            } else {
                stopFloatingService()
                binding.fabFloatingToggle.setImageResource(android.R.drawable.ic_media_play)
                isFloatingEnabled = false
            }
        }

        binding.viewPager.adapter =
            object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {

                override fun getItemCount() = fragments.size

                override fun createFragment(position: Int) =
                    fragments[position]
            }

        com.google.android.material.tabs.TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager
        ) { tab, position ->

            tab.text = titles[position]

        }.attach()
        renderHistory()


    }

    fun stopFloatingService() {
        stopService(Intent(this, FloatingTrackerService::class.java))
    }

    fun getCodexUrl(): String {
        return if (SettingsStore.getCodexType(this) == "AUSSIE") {
            "https://www.aussiescodex.com/orna-items"
        } else {
            "https://codex.fqegg.top/#/"
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

        val notifyCheck =
            dialogView.findViewById<android.widget.CheckBox>(
                R.id.materialNotifyCheck
            )

        notifyCheck.isChecked =
            SettingsStore.getMaterialNotifications(this)

        val auraSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.auraSpinner)
        val auraPreview = dialogView.findViewById<android.widget.ImageView>(R.id.auraPreview)

        val auraFiles = assets.list("aura")
            ?.filter { it.endsWith(".webp") }
            ?: emptyList()

        val scanInput = dialogView.findViewById<android.widget.EditText>(R.id.scanIntervalInput)
        val codexGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.codexGroup)

        scanInput.setText(SettingsStore.getScanInterval(this).toString())

        val current = SettingsStore.getCodexType(this)
        if (current == "AUSSIE") {
            codexGroup.check(R.id.radioAussie)
        } else {
            codexGroup.check(R.id.radioYaco)
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

                val timePicker =
                    dialogView.findViewById<TimePicker>(R.id.materialTimePicker)

                val hour = if (Build.VERSION.SDK_INT >= 23)
                    timePicker.hour
                else
                    timePicker.currentHour

                val minute = if (Build.VERSION.SDK_INT >= 23)
                    timePicker.minute
                else
                    timePicker.currentMinute

                SettingsStore.setMaterialTime(this, hour, minute)

                val interval = scanInput.text.toString().toLongOrNull() ?: 2500L
                SettingsStore.setScanInterval(this, interval)

                SettingsStore.setMaterialNotifications(
                    this,
                    notifyCheck.isChecked
                )

                val selectedCodex = when (codexGroup.checkedRadioButtonId) {
                    R.id.radioAussie -> "AUSSIE"
                    else -> "YACO"
                }

                SettingsStore.setCodexType(this, selectedCodex)

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
        startService(Intent(this, FloatingTrackerService::class.java))
    }
}