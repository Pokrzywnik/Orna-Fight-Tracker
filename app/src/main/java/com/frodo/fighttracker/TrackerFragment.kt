package com.frodo.fighttracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentTrackerBinding
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.MotionEvent
import android.widget.Toast
import coil.load

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
var recording = 0

class TrackerFragment : Fragment() {

    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private val stopHoldHandler = Handler(Looper.getMainLooper())
    private var stopHoldRunnable: Runnable? = null
    private var isHolding = false

    /** proof id -> counter TextView currently shown under the stats */
    private val proofCountViews = mutableMapOf<String, TextView>()

    private fun fmt(value: Long): String {
        return value.toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
    }

    override fun onResume() {
        super.onResume()
        // Anguish version could have been changed in settings
        refreshProofViews()
        renderHistory()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTrackerBinding.inflate(inflater, container, false)

        val handler = Handler(Looper.getMainLooper())

        handler.post(object : Runnable {
            override fun run() {

                // view destroyed -> stop this loop
                val b = _binding ?: return

                b.goldText.text = "gold: ${fmt(FightState.gold)}"

                b.ornsText.text = "orns: ${fmt(FightState.orns)}"

                b.expText.text = "exp: ${fmt(FightState.exp)}"

                b.shardsText.text = "tower shards: ${fmt(FightState.shards)}"

                for ((id, view) in proofCountViews) {
                    view.text = fmt(FightState.proofs[id] ?: 0L)
                }

                handler.postDelayed(this, 500)

            }

        })

        binding.proofModeButton.setOnClickListener {
            showProofModeDialog()
        }

        refreshProofViews()

        binding.startButton.setOnClickListener {
            FightState.startTime = System.currentTimeMillis()
            FightState.ornsConfirmationThreshold = 3_000_000L
            (activity as MainActivity).startCapture()
            recording = 1
        }



        binding.stopButton.setOnTouchListener { v, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    isHolding = true

                    stopHoldRunnable = Runnable {

                        if (isHolding) {

                            val enabled = DebugController.toggle()

                            Toast.makeText(
                                requireContext(),
                                if (enabled) "debug enabled" else "debug disabled",
                                Toast.LENGTH_SHORT
                            ).show()

                            val intent = Intent(
                                requireContext(),
                                OcrDebugOverlayService::class.java
                            )

                            if (enabled)
                                requireContext().startService(intent)
                            else
                                requireContext().stopService(intent)

                            // prevent normal click afterwards
                            isHolding = false
                        }
                    }

                    stopHoldHandler.postDelayed(stopHoldRunnable!!, 2000)

                    true
                }

                MotionEvent.ACTION_UP -> {

                    stopHoldRunnable?.let {
                        stopHoldHandler.removeCallbacks(it)
                    }

                    if (isHolding) {

                        isHolding = false

                        if (recording == 1) {
                            recording = 0
                            (activity as MainActivity).stopTracking()
                            renderHistory()
                        }

                    } else {
                        // We already toggled debug, do nothing.
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {

                    isHolding = false

                    stopHoldRunnable?.let {
                        stopHoldHandler.removeCallbacks(it)
                    }

                    true
                }

                else -> true
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        proofCountViews.clear()
        _binding = null
    }

    /**
     * Dialog: what is tracked (shown in this tab) and what is
     * shown in the floating overlay.
     *
     * Section 1: Orns / EXP / Gold
     * Section 2: Proofs / King's guard / Skeleton key
     *
     * Each row: checkbox (track) + switch (show in overlay).
     */
    private fun showProofModeDialog() {

        val ctx = requireContext()

        val modes = ProofTracking.Mode.values()
        val currencies = CurrencyTracking.Currency.values()

        val tracked = ProofTracking.getModes(ctx).toMutableSet()
        val overlay = ProofTracking.getOverlayModes(ctx).toMutableSet()

        val curTracked = CurrencyTracking.getTracked(ctx).toMutableSet()
        val curOverlay = CurrencyTracking.getOverlay(ctx).toMutableSet()

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun addHeader(title: String) {
            root.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * density).toInt(), 0, 0)
                addView(TextView(ctx).apply {
                    text = title
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                addView(TextView(ctx).apply {
                    text = "Overlay"
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            })
        }

        fun addRow(
            label: String,
            isTracked: Boolean,
            isOverlay: Boolean,
            onTracked: (Boolean) -> Unit,
            onOverlay: (Boolean) -> Unit
        ) {

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val check = android.widget.CheckBox(ctx).apply {
                text = label
                isChecked = isTracked
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
                isChecked = isOverlay
                isEnabled = isTracked
            }

            check.setOnCheckedChangeListener { _, checked ->
                onTracked(checked)
                sw.isEnabled = checked
            }

            sw.setOnCheckedChangeListener { _, checked ->
                onOverlay(checked)
            }

            row.addView(check)
            row.addView(sw)
            root.addView(row)
        }

        // --- Orns / EXP / Gold ---
        addHeader("Currencies")

        for (c in currencies) {
            addRow(
                label = c.label,
                isTracked = c in curTracked,
                isOverlay = c in curOverlay,
                onTracked = { if (it) curTracked.add(c) else curTracked.remove(c) },
                onOverlay = { if (it) curOverlay.add(c) else curOverlay.remove(c) }
            )
        }

        // --- Proofs / keys ---
        addHeader("Proofs / misc")

        for (mode in modes) {
            addRow(
                label = mode.label,
                isTracked = mode in tracked,
                isOverlay = mode in overlay,
                onTracked = { if (it) tracked.add(mode) else tracked.remove(mode) },
                onOverlay = { if (it) overlay.add(mode) else overlay.remove(mode) }
            )
        }

        val scroll = android.widget.ScrollView(ctx).apply {
            addView(root)
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Track")
            .setView(scroll)
            .setPositiveButton("OK") { _, _ ->

                ProofTracking.setModes(ctx, tracked)
                ProofTracking.setOverlayModes(ctx, overlay)

                CurrencyTracking.setTracked(ctx, curTracked)
                CurrencyTracking.setOverlay(ctx, curOverlay)

                refreshProofViews()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Rebuilds the icon + counter rows for all enabled modes
     * and shows / hides the Orns / EXP / Gold lines.
     * Four icons per row so everything fits on narrow screens.
     */
    private fun refreshProofViews() {

        val b = _binding ?: return

        val ctx = context ?: return

        // Orns / EXP / Gold visibility
        val shown = CurrencyTracking.getTracked(ctx)

        b.goldText.visibility =
            if (CurrencyTracking.Currency.GOLD in shown) View.VISIBLE else View.GONE

        b.ornsText.visibility =
            if (CurrencyTracking.Currency.ORNS in shown) View.VISIBLE else View.GONE

        b.expText.visibility =
            if (CurrencyTracking.Currency.EXP in shown) View.VISIBLE else View.GONE

        b.proofModeButton.text =
            "TRACKING: ${ProofTracking.modesLabel(ctx)}"

        b.proofsContainer.removeAllViews()
        proofCountViews.clear()

        val density = resources.displayMetrics.density
        val iconSize = (28 * density).toInt()
        val gap = (10 * density).toInt()

        val proofs = ProofTracking.activeProofs(ctx)

        for (rowProofs in proofs.chunked(4)) {

            val row = LinearLayout(ctx).apply {

                orientation = LinearLayout.HORIZONTAL

                gravity = Gravity.CENTER_VERTICAL

                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }

            for (proof in rowProofs) {

                val icon = ImageView(ctx).apply {

                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)

                    scaleType = ImageView.ScaleType.FIT_CENTER

                    load(proof.iconUrl) {
                        crossfade(false)
                        error(android.R.drawable.ic_menu_help)
                    }
                }

                val count = TextView(ctx).apply {

                    text = fmt(FightState.proofs[proof.id] ?: 0L)

                    textSize = 18f

                    setPadding((4 * density).toInt(), 0, gap, 0)
                }

                row.addView(icon)
                row.addView(count)

                proofCountViews[proof.id] = count
            }

            b.proofsContainer.addView(row)
        }
    }

    private fun renderHistory() {

        val b = _binding ?: return

        Log.d("RUN_HISTORY", "renderHistory CALLED")

        val runs = RunStorage.loadRuns(requireContext())

        b.historyContainer.removeAllViews()

        fun statRow(iconRes: Int, label: String, value: String): android.view.View {

            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val icon = android.widget.ImageView(requireContext()).apply {
                setImageResource(iconRes)
                layoutParams = android.widget.LinearLayout.LayoutParams(48, 48)
            }

            val text = android.widget.TextView(requireContext()).apply {
                this.text = "$label: $value"
                textSize = 16f
                setPadding(24, 0, 0, 0)
            }

            row.addView(icon)
            row.addView(text)

            return row
        }

        // same row, but the icon comes from a URL
        fun statRowUrl(iconUrl: String, label: String, value: String): android.view.View {

            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val icon = android.widget.ImageView(requireContext()).apply {

                layoutParams = android.widget.LinearLayout.LayoutParams(48, 48)

                load(iconUrl) {
                    crossfade(false)
                    error(android.R.drawable.ic_menu_help)
                }
            }

            val text = android.widget.TextView(requireContext()).apply {
                this.text = "$label: $value"
                textSize = 16f
                setPadding(24, 0, 0, 0)
            }

            row.addView(icon)
            row.addView(text)

            return row
        }

        val deleteicon = android.R.drawable.star_big_on
        val goldIcon = R.drawable.gold
        val ornsIcon = R.drawable.orns
        val xpIcon = R.drawable.exp_potion
        val shardIcon = R.drawable.tower_fragment

        runs.forEachIndexed { index, run ->

            val tile = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f

                    setColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorSurface,
                            0
                        )
                    )

                    setStroke(
                        2,
                        com.google.android.material.color.MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOutline,
                            0
                        )
                    )
                }

                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 24)
                layoutParams = params
            }

            // format numbers
            val gold = fmt(run.gold)
            val orns = fmt(run.orns)
            val exp = fmt(run.exp)
            val shards = fmt(run.shards)

            // run header (date + duration)
            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 16)
            }

            val dateText = TextView(requireContext()).apply {
                text = run.date
                textSize = 14f

                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.WHITE
                    )
                )

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nameText = TextView(requireContext()).apply {

                text = if (run.name.isBlank()) "" else run.name

                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)

                textAlignment = View.TEXT_ALIGNMENT_CENTER

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val durationText = TextView(requireContext()).apply {
                text = run.duration
                textSize = 14f

                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnSurface,
                        android.graphics.Color.WHITE
                    )
                )

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }

            header.addView(dateText)
            header.addView(nameText)
            header.addView(durationText)

            // delete run button
            val deleteBtn = android.widget.TextView(requireContext()).apply {
                text = "Delete"
                setTextColor(android.graphics.Color.RED)
                visibility = View.GONE
                setPadding(0, 16, 0, 0)

                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            deleteBtn.setOnClickListener {
                RunStorage.deleteRun(requireContext(), run.runid)
                Log.d("RUN_HISTORY", "Deleted run at index $index")
                renderHistory()
            }

            val renameBtn = android.widget.TextView(requireContext()).apply {

                text = "Rename"

                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorPrimary,
                        android.graphics.Color.GREEN
                    )
                )

                visibility = View.GONE

                setPadding(0, 8, 0, 0)

                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 32
                }
            }

            renameBtn.setOnClickListener {

                val input = android.widget.EditText(requireContext())

                input.setText(run.name)

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Rename Run")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->

                        RunStorage.renameRun(
                            requireContext(),
                            run.runid,
                            input.text.toString().trim()
                        )

                        renderHistory()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }


            // long press to show delete
            tile.setOnLongClickListener {
                deleteBtn.visibility = View.VISIBLE
                renameBtn.visibility = View.VISIBLE
                true
            }

            // build card
            tile.addView(header)
            tile.addView(statRow(goldIcon, "Gold", gold))
            tile.addView(statRow(ornsIcon, "Orns", orns))
            tile.addView(statRow(xpIcon, "XP", exp))
            if (run.shards > 0) {
                tile.addView(
                    statRow(
                        shardIcon,
                        "Tower Shards",
                        shards
                    )
                )
            }

            // proofs / skeleton keys collected in this run
            for (proof in ProofTracking.allProofs) {

                val count = run.proofs[proof.id] ?: 0L

                if (count > 0L) {

                    tile.addView(
                        statRowUrl(
                            proof.iconUrl,
                            proof.label,
                            fmt(count)
                        )
                    )
                }
            }

            val buttonRow = android.widget.LinearLayout(requireContext()).apply {

                orientation = android.widget.LinearLayout.HORIZONTAL

                gravity = android.view.Gravity.START

            }

            buttonRow.addView(renameBtn)
            buttonRow.addView(deleteBtn)

            tile.addView(buttonRow)

            b.historyContainer.addView(tile)
        }
    }
}