package com.frodo.fighttracker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.DialogRenameBinding
import com.frodo.fighttracker.databinding.FragmentTrackerBinding
import com.google.android.material.chip.Chip
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

var recording = 0

class TrackerFragment : Fragment() {

    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!

    private val stopHoldHandler = Handler(Looper.getMainLooper())
    private var stopHoldRunnable: Runnable? = null
    private var isHolding = false

    /** 500ms refresh of timer / stats / overlay pill (runs only while resumed). */
    private val ui = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (_binding == null) return
            updateLive()
            ui.postDelayed(this, 500)
        }
    }

    /** Current run card: expanded by default every time the tab is shown. */
    private var expanded = true

    /** proof id -> counter TextView currently shown in the run card */
    private val proofCountViews = mutableMapOf<String, TextView>()

    // ---- run history state ----

    private lateinit var adapter: RunHistoryAdapter

    private var allRuns: List<RunRecord> = emptyList()

    /** null = "All" */
    private var typeFilter: RunType? = null

    private var query = ""

    private enum class Sort(val labelRes: Int) {
        NEWEST(R.string.ft_sort_newest),
        MOST_GOLD(R.string.ft_sort_gold),
        ORNS_PER_MIN(R.string.ft_sort_orns_min)
    }

    private var sort = Sort.NEWEST

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTrackerBinding.inflate(inflater, container, false)

        val b = binding

        setupStatCells()
        setupRunCard()
        setupHistory()

        b.settingsButton.setOnClickListener {
            (activity as? MainActivity)?.showSettingsDialog()
        }

        b.overlayToggle.setOnClickListener {
            (activity as? MainActivity)?.toggleFloatingService()
        }

        b.trackingRow.setOnClickListener {
            showProofModeDialog()
        }

        refreshProofViews()
        applyExpanded(animate = false)

        return b.root
    }

    override fun onResume() {
        super.onResume()

        // Anguish version could have been changed in settings
        refreshProofViews()
        reloadHistory()

        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ui.removeCallbacks(tick)
        stopHoldRunnable?.let { stopHoldHandler.removeCallbacks(it) }
        proofCountViews.clear()
        _binding = null
    }

    /** Called by MainActivity when the Tracker tab becomes the selected page. */
    fun onTabSelected() {
        expanded = true
        if (_binding != null) applyExpanded(animate = false)
    }

    // ------------------------------------------------------------------
    // setup
    // ------------------------------------------------------------------

    private fun setupStatCells() {

        val b = binding

        fun cell(
            c: com.frodo.fighttracker.databinding.ViewStatCellBinding,
            label: Int,
            color: Int
        ) {
            c.statLabel.setText(label)
            c.statValue.setTextColor(ContextCompat.getColor(requireContext(), color))
        }

        cell(b.statGold, R.string.ft_label_gold, R.color.ft_gold)
        cell(b.statOrns, R.string.ft_label_orns, R.color.ft_orns)
        cell(b.statExp, R.string.ft_label_exp, R.color.ft_exp)
        cell(b.statShards, R.string.ft_label_shards, R.color.ft_shards)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRunCard() {

        val b = binding

        // whole card toggles (the buttons / tracking row consume their own taps)
        b.runCard.setOnClickListener {
            expanded = !expanded
            applyExpanded(animate = true)
        }

        b.startButton.setOnClickListener {
            FightState.startTime = System.currentTimeMillis()
            FightState.ornsConfirmationThreshold = 3_000_000L
            (activity as MainActivity).startCapture()
            recording = 1
        }

        b.undoButton.setOnClickListener {
            if (!FightTrackerService.undoLast()) {
                Toast.makeText(
                    requireContext(),
                    R.string.ft_nothing_to_undo,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // tap = stop, hold for 2s = toggle the OCR debug overlay (unchanged behaviour)
        b.stopButton.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    isHolding = true

                    stopHoldRunnable = Runnable {

                        if (isHolding) {

                            val enabled = DebugController.toggle()

                            Toast.makeText(
                                requireContext(),
                                if (enabled) R.string.ft_debug_on else R.string.ft_debug_off,
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

                        if (recording == 1 || TrackingController.isRunning) {
                            recording = 0
                            (activity as MainActivity).stopTracking()
                            reloadHistory()
                            updateLive()
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
    }

    private fun setupHistory() {

        val b = binding

        adapter = RunHistoryAdapter(
            onRename = { showRenameDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        b.historyList.adapter = adapter
        b.historyList.itemAnimator = null

        b.searchInput.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                applyFilters()
            }
        })

        b.filterGroup.setOnCheckedStateChangeListener { _, ids ->

            typeFilter = when (ids.firstOrNull()) {
                R.id.chipAnguish -> RunType.ANGUISH
                R.id.chipTowers -> RunType.TOWERS
                R.id.chipCoral -> RunType.CORAL
                R.id.chipGuard -> RunType.GUARD
                R.id.chipRemembrance -> RunType.REMEMBRANCE
                else -> null
            }

            applyFilters()
        }

        b.sortButton.text = getString(R.string.ft_sort_format, getString(sort.labelRes))

        b.sortButton.setOnClickListener { anchor ->

            val menu = PopupMenu(requireContext(), anchor)

            Sort.values().forEachIndexed { index, s ->
                menu.menu.add(0, index, index, s.labelRes)
            }

            menu.setOnMenuItemClickListener { item ->
                sort = Sort.values()[item.itemId]
                binding.sortButton.text =
                    getString(R.string.ft_sort_format, getString(sort.labelRes))
                applyFilters()
                true
            }

            menu.show()
        }
    }

    // ------------------------------------------------------------------
    // current run card
    // ------------------------------------------------------------------

    private fun applyExpanded(animate: Boolean) {

        val b = _binding ?: return

        if (animate) {
            TransitionManager.beginDelayedTransition(
                b.runCard,
                AutoTransition().setDuration(180)
            )
        }

        b.runDetails.visibility = if (expanded) View.VISIBLE else View.GONE

        val target = if (expanded) 180f else 0f

        if (animate) {
            b.runChevron.animate().rotation(target).setDuration(180).start()
        } else {
            b.runChevron.animate().cancel()
            b.runChevron.rotation = target
        }
    }

    /** Runs every 500ms while the tab is visible. */
    private fun updateLive() {

        val b = _binding ?: return
        val ctx = context ?: return

        // overlay pill
        b.overlayDot.setBackgroundResource(
            if (FloatingTrackerService.isRunning) R.drawable.bg_dot else R.drawable.bg_dot_off
        )

        val running = TrackingController.isRunning

        // header state
        b.statusDot.setBackgroundResource(if (running) R.drawable.bg_dot else R.drawable.bg_dot_off)
        b.statusText.setText(if (running) R.string.ft_tracking else R.string.ft_idle)
        b.statusText.setTextColor(
            ContextCompat.getColor(ctx, if (running) R.color.ft_ok else R.color.ft_muted)
        )

        b.startButton.visibility = if (running) View.GONE else View.VISIBLE
        b.stopButton.visibility = if (running) View.VISIBLE else View.GONE
        b.undoButton.visibility = if (running) View.VISIBLE else View.GONE
        b.compactRates.visibility = if (running) View.VISIBLE else View.GONE

        val elapsedMs =
            if (running && FightState.startTime > 0L)
                System.currentTimeMillis() - FightState.startTime
            else 0L

        b.timerText.text = UiKit.timer(elapsedMs)

        // per-minute = value / minutes since the run started
        // (needs at least 10s of data, otherwise the number is just noise)
        val minutes = elapsedMs / 60_000.0

        fun perMinute(value: Long): Double? =
            if (running && minutes >= 10.0 / 60.0) value / minutes else null

        fun rateText(value: Long): String {
            val r = perMinute(value) ?: return ctx.getString(R.string.ft_dash)
            return ctx.getString(R.string.ft_per_min, UiKit.compact(r))
        }

        fun shortRate(value: Long): String {
            val r = perMinute(value) ?: return ctx.getString(R.string.ft_dash)
            return ctx.getString(R.string.ft_per_min_short, UiKit.compact(r))
        }

        val gold = FightState.gold
        val orns = FightState.orns
        val exp = FightState.exp
        val shards = FightState.shards

        b.statGold.statValue.text = UiKit.fmt(gold)
        b.statGold.statRate.text = rateText(gold)

        b.statOrns.statValue.text = UiKit.fmt(orns)
        b.statOrns.statRate.text = rateText(orns)

        b.statExp.statValue.text = UiKit.fmt(exp)
        b.statExp.statRate.text = rateText(exp)

        b.statShards.statValue.text = UiKit.fmt(shards)
        b.statShards.statRate.text = rateText(shards)

        b.compactGold.text = shortRate(gold)
        b.compactOrns.text = shortRate(orns)

        for ((id, view) in proofCountViews) {
            view.text = UiKit.fmt(FightState.proofs[id] ?: 0L)
        }
    }

    /** "TRACKING: Orns · EXP · Gold · Anguish 2.0 · King's guard" */
    private fun trackingSummary(): String {

        val ctx = requireContext()

        val parts = mutableListOf<String>()

        val currencies = CurrencyTracking.getTracked(ctx)

        for (c in CurrencyTracking.Currency.values()) {
            if (c in currencies) parts.add(c.label)
        }

        val modes = ProofTracking.getModes(ctx)

        for (mode in ProofTracking.Mode.values()) {

            if (mode !in modes) continue

            parts.add(
                when (mode) {
                    ProofTracking.Mode.ANGUISH ->
                        getString(
                            R.string.ft_anguish_version_label,
                            SettingsStore.getAnguishVersion(ctx)
                        )

                    ProofTracking.Mode.KINGS_GUARD -> getString(R.string.ft_kings_guard)

                    ProofTracking.Mode.SKELETON_KEY -> getString(R.string.ft_skeleton_key)
                }
            )
        }

        val text =
            if (parts.isEmpty()) getString(R.string.ft_tracking_nothing)
            else parts.joinToString(" · ")

        return getString(R.string.ft_tracking_summary, text)
    }

    /**
     * Rebuilds the icon + counter rows for all enabled modes, shows / hides
     * the Orns / EXP / Gold cells and updates the tracking summary.
     * Four proofs per line so everything fits on narrow screens.
     * (Also called by MainActivity after Settings were saved.)
     */
    fun refreshProofViews() {

        val b = _binding ?: return

        val ctx = context ?: return

        // Orns / EXP / Gold visibility (shards are always shown, as before)
        val shown = CurrencyTracking.getTracked(ctx)

        b.statGold.root.visibility =
            if (CurrencyTracking.Currency.GOLD in shown) View.VISIBLE else View.GONE

        b.statOrns.root.visibility =
            if (CurrencyTracking.Currency.ORNS in shown) View.VISIBLE else View.GONE

        b.statExp.root.visibility =
            if (CurrencyTracking.Currency.EXP in shown) View.VISIBLE else View.GONE

        // compact header rates follow the same setting
        b.compactGold.visibility =
            if (CurrencyTracking.Currency.GOLD in shown) View.VISIBLE else View.GONE

        b.compactOrns.visibility =
            if (CurrencyTracking.Currency.ORNS in shown) View.VISIBLE else View.GONE

        b.trackingSummaryText.text = trackingSummary()

        b.proofsContainer.removeAllViews()
        proofCountViews.clear()

        val proofs = ProofTracking.activeProofs(ctx)

        for (rowProofs in proofs.chunked(4)) {

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (proof in rowProofs) {

                val (cell, counter) = UiKit.proofCell(
                    ctx,
                    proof.iconUrl,
                    FightState.proofs[proof.id] ?: 0L
                )

                row.addView(cell)

                proofCountViews[proof.id] = counter
            }

            b.proofsContainer.addView(row)
        }

        b.proofsContainer.visibility =
            if (proofs.isEmpty()) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Track dialog (what is tracked + what is shown in the overlay)
    // ------------------------------------------------------------------

    /**
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

        val pad = UiKit.dp(ctx, 20)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, UiKit.dp(ctx, 4), pad, 0)
        }

        fun addHeader(title: String) {

            root.addView(LinearLayout(ctx).apply {

                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 2))

                addView(TextView(ctx).apply {
                    setTextAppearance(R.style.TextAppearance_FT_Overline)
                    setTextColor(ContextCompat.getColor(ctx, R.color.ft_accent))
                    text = title.uppercase()
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })

                addView(TextView(ctx).apply {
                    setTextAppearance(R.style.TextAppearance_FT_Overline)
                    text = getString(R.string.ft_overlay_column).uppercase()
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
                minimumHeight = UiKit.dp(ctx, 48)
            }

            val check = MaterialCheckBox(ctx).apply {
                text = label
                isChecked = isTracked
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val sw = MaterialSwitch(ctx).apply {
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
        addHeader(getString(R.string.ft_currencies))

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
        addHeader(getString(R.string.ft_proofs_misc))

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

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ft_track_title)
            .setView(scroll)
            .setPositiveButton(R.string.ft_ok) { _, _ ->

                ProofTracking.setModes(ctx, tracked)
                ProofTracking.setOverlayModes(ctx, overlay)

                CurrencyTracking.setTracked(ctx, curTracked)
                CurrencyTracking.setOverlay(ctx, curOverlay)

                refreshProofViews()
            }
            .setNegativeButton(R.string.ft_cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Run history
    // ------------------------------------------------------------------

    private fun reloadHistory() {

        val ctx = context ?: return

        allRuns = RunStorage.loadRuns(ctx)

        applyFilters()
    }

    private fun ornsPerMinute(run: RunRecord): Double {

        val minutes = UiKit.durationMinutes(run.duration)

        return if (minutes > 0.0) run.orns / minutes else 0.0
    }

    /** name, type and proof labels */
    private fun matches(run: RunRecord, q: String): Boolean {

        val type = RunType.fromKey(run.type)

        val typeLabel = getString(type?.labelRes ?: R.string.ft_type_other)

        if (run.name.contains(q, ignoreCase = true)) return true
        if (typeLabel.contains(q, ignoreCase = true)) return true

        return ProofTracking.allProofs.any { proof ->
            (run.proofs[proof.id] ?: 0L) > 0L &&
                    proof.label.contains(q, ignoreCase = true)
        }
    }

    private fun applyFilters() {

        val b = _binding ?: return
        val ctx = context ?: return

        val filter = typeFilter

        var list = allRuns.filter { run ->
            (filter == null || run.type == filter.key) &&
                    (query.isEmpty() || matches(run, query))
        }

        list = when (sort) {
            Sort.NEWEST -> list.sortedByDescending { it.runid.toLongOrNull() ?: 0L }
            Sort.MOST_GOLD -> list.sortedByDescending { it.gold }
            Sort.ORNS_PER_MIN -> list.sortedByDescending { ornsPerMinute(it) }
        }

        adapter.submitList(list)

        val totalGold = list.sumOf { it.gold }

        val goldText =
            if (totalGold >= 1_000_000L)
                String.format(java.util.Locale.US, "%.1f M", totalGold / 1_000_000.0)
            else
                UiKit.fmt(totalGold)

        b.summaryText.text = getString(
            R.string.ft_summary_format,
            resources.getQuantityString(R.plurals.ft_run_count, list.size, list.size),
            goldText
        )

        b.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showRenameDialog(run: RunRecord) {

        val ctx = requireContext()

        val d = DialogRenameBinding.inflate(LayoutInflater.from(ctx))

        d.renameInput.setText(run.name)
        d.renameInput.setSelection(d.renameInput.text?.length ?: 0)

        // type picker: the five types + "Other" (= no type)
        val options: List<Pair<RunType?, Int>> =
            RunType.values().map { it to it.labelRes } + (null to R.string.ft_type_other)

        var selectedType: RunType? = RunType.fromKey(run.type)

        for ((type, label) in options) {

            val chip = LayoutInflater.from(ctx)
                .inflate(R.layout.item_filter_chip, d.typeGroup, false) as Chip

            chip.id = View.generateViewId()
            chip.setText(label)
            chip.isChecked = (type == selectedType)

            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedType = type
            }

            d.typeGroup.addView(chip)
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ft_rename_title)
            .setView(d.root)
            .setPositiveButton(R.string.ft_save) { _, _ ->

                RunStorage.renameRun(
                    ctx,
                    run.runid,
                    d.renameInput.text.toString().trim()
                )

                RunStorage.setRunType(ctx, run.runid, selectedType?.key)

                reloadHistory()
            }
            .setNegativeButton(R.string.ft_cancel, null)
            .show()
    }

    private fun confirmDelete(run: RunRecord) {

        val ctx = requireContext()

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ft_delete_title)
            .setMessage(R.string.ft_delete_message)
            .setPositiveButton(R.string.ft_delete) { _, _ ->

                RunStorage.deleteRun(ctx, run.runid)

                reloadHistory()
            }
            .setNegativeButton(R.string.ft_cancel, null)
            .show()
    }
}
