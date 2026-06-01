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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
var recording = 0

class TrackerFragment : Fragment() {
    var start = 0
    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!


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

                handler.postDelayed(this, 500)
                if(start==0){
                    renderHistory()
                    start = 1
                }
            }

        })

        binding.startButton.setOnClickListener {
            FightState.startTime = System.currentTimeMillis()
            (activity as MainActivity).startCapture()
            recording = 1
        }

        binding.stopButton.setOnClickListener {

            if (recording == 1) {

                recording = 0

                (activity as MainActivity).stopTracking()

                renderHistory()
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

            // run header (date + duration)
            val header = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 16)
            }

            val dateText = android.widget.TextView(requireContext()).apply {
                text = run.date
                textSize = 14f
                setTextColor(android.graphics.Color.DKGRAY)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val durationText = android.widget.TextView(requireContext()).apply {
                text = run.duration
                textSize = 14f
                setTextColor(android.graphics.Color.DKGRAY)
            }

            header.addView(dateText)
            header.addView(durationText)

            // delete run button
            val deleteBtn = android.widget.TextView(requireContext()).apply {
                text = "Delete"
                setTextColor(android.graphics.Color.RED)
                visibility = View.GONE
                setPadding(0, 16, 0, 0)
            }

            deleteBtn.setOnClickListener {
                RunStorage.deleteRun(requireContext(), run.runid)
                Log.d("RUN_HISTORY", "Deleted run at index $index")
                renderHistory()
            }

            // long press to show delete
            tile.setOnLongClickListener {
                deleteBtn.visibility = View.VISIBLE
                true
            }

            // build card
            tile.addView(header)
            tile.addView(statRow(goldIcon, "Gold", gold))
            tile.addView(statRow(ornsIcon, "Orns", orns))
            tile.addView(statRow(xpIcon, "XP", exp))
            tile.addView(deleteBtn)

            binding.historyContainer.addView(tile)
        }
    }
}