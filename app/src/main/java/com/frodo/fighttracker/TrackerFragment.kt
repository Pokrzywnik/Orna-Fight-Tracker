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
import android.widget.LinearLayout
import android.widget.TextView
import android.view.MotionEvent
import android.widget.Toast

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

    override fun onResume() {
        super.onResume()
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
                binding.goldText.text =
                    "gold: ${
                        FightState.gold.toString()
                            .reversed()
                            .chunked(3)
                            .joinToString(" ")
                            .reversed()
                    }"

                binding.ornsText.text =
                    "orns: ${
                        FightState.orns.toString()
                            .reversed()
                            .chunked(3)
                            .joinToString(" ")
                            .reversed()
                    }"

                binding.expText.text =
                    "exp: ${
                        FightState.exp.toString()
                            .reversed()
                            .chunked(3)
                            .joinToString(" ")
                            .reversed()
                    }"

                binding.shardsText.text =
                    "tower shards: ${
                        FightState.shards.toString()
                            .reversed()
                            .chunked(3)
                            .joinToString(" ")
                            .reversed()
                    }"

                handler.postDelayed(this, 500)

            }

        })

        binding.startButton.setOnClickListener {
            FightState.startTime = System.currentTimeMillis()
            FightState.ornsConfirmationThreshold = 30L
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
    private fun renderHistory() {

        Log.d("RUN_HISTORY", "renderHistory CALLED")

        val runs = RunStorage.loadRuns(requireContext())

        binding.historyContainer.removeAllViews()

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
            val gold = run.gold.toString()
                .reversed()
                .chunked(3)
                .joinToString(" ")
                .reversed()

            val orns = run.orns.toString()
                .reversed()
                .chunked(3)
                .joinToString(" ")
                .reversed()

            val exp = run.exp.toString()
                .reversed()
                .chunked(3)
                .joinToString(" ")
                .reversed()
            val shards = run.shards.toString()
                .reversed()
                .chunked(3)
                .joinToString(" ")
                .reversed()

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
            val buttonRow = android.widget.LinearLayout(requireContext()).apply {

                orientation = android.widget.LinearLayout.HORIZONTAL

                gravity = android.view.Gravity.START

            }

            buttonRow.addView(renameBtn)
            buttonRow.addView(deleteBtn)

            tile.addView(buttonRow)

            binding.historyContainer.addView(tile)
        }
    }
}