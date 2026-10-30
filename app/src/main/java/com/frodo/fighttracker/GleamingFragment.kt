package com.frodo.fighttracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.frodo.fighttracker.databinding.FragmentGleamingBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Gleaming codex: every normal world mob, split by tier (T1..T10) with
 * found/total counters. Not-yet-seen mobs are black silhouettes; tap one to
 * mark it as seen gleaming (real sprite + aura). Tap a discovered one to undo.
 */
class GleamingFragment : Fragment(R.layout.fragment_gleaming) {

    private companion object {
        const val TIER_ALL = 0
    }

    private var _binding: FragmentGleamingBinding? = null

    private lateinit var adapter: GleamingAdapter

    private var mobs: List<GleamingMob> = emptyList()
    private var tiers: List<Int> = emptyList()
    private var selectedTier = TIER_ALL

    /** tier (or TIER_ALL) -> its chip, so counters can be refreshed in place */
    private val chips = mutableMapOf<Int, Chip>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentGleamingBinding.bind(view)
        _binding = b

        val ctx = requireContext()

        GleamingStore.load(ctx)
        mobs = GleamingStore.mobs(ctx)
        tiers = mobs.map { it.tier }.distinct().sorted()

        adapter = GleamingAdapter(::onMobClick)

        val spanCount = if (resources.configuration.screenWidthDp >= 600) 5 else 3

        b.gleamingList.layoutManager = GridLayoutManager(ctx, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (adapter.isHeader(position)) spanCount else 1
            }
        }
        b.gleamingList.adapter = adapter
        b.gleamingList.itemAnimator = null

        buildChips(b)
        refresh()
    }

    private fun buildChips(b: FragmentGleamingBinding) {

        chips.clear()
        b.tierChips.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        for (tier in listOf(TIER_ALL) + tiers) {

            val chip = inflater.inflate(R.layout.item_filter_chip, b.tierChips, false) as Chip

            chip.id = View.generateViewId()
            chip.tag = tier
            chip.isChecked = tier == selectedTier

            chips[tier] = chip
            b.tierChips.addView(chip)
        }

        b.tierChips.setOnCheckedStateChangeListener { group, checkedIds ->

            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val tier = group.findViewById<Chip>(id)?.tag as? Int
                ?: return@setOnCheckedStateChangeListener

            if (tier == selectedTier) return@setOnCheckedStateChangeListener

            selectedTier = tier
            refresh()
            b.gleamingList.scrollToPosition(0)
        }
    }

    /** Rebuilds rows + all counters (overall, per-tier chips, headers). */
    private fun refresh() {

        val b = _binding ?: return
        val ctx = requireContext()

        val byTier = mobs.groupBy { it.tier }
        val discovered = GleamingStore.discovered

        fun found(list: List<GleamingMob>) = list.count { it.id in discovered }

        // overall counter next to the title
        b.gleamingOverall.text =
            getString(R.string.ft_gleaming_progress, found(mobs), mobs.size)

        // chips: "All 2/319", "T1 0/25", ...
        chips[TIER_ALL]?.text =
            getString(R.string.ft_gleaming_chip_all, found(mobs), mobs.size)

        for (tier in tiers) {
            val list = byTier[tier].orEmpty()
            chips[tier]?.text =
                getString(R.string.ft_gleaming_chip_tier, tier, found(list), list.size)
        }

        // grid rows
        val shownTiers = if (selectedTier == TIER_ALL) tiers else listOf(selectedTier)

        val rows = buildList<GleamingRow> {
            for (tier in shownTiers) {
                val list = byTier[tier].orEmpty()
                add(GleamingRow.Header(tier, found(list), list.size))
                list.forEach { add(GleamingRow.Mob(it, it.id in discovered)) }
            }
        }

        adapter.submitList(rows)
    }

    private fun onMobClick(mob: GleamingMob, discovered: Boolean) {

        val ctx = requireContext()

        if (!discovered) {
            GleamingStore.setDiscovered(ctx, mob.id, true)
            refresh()
            return
        }

        // already discovered: ask first so a stray tap does not wipe progress
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ft_gleaming_unmark_title)
            .setMessage(getString(R.string.ft_gleaming_unmark_message, mob.name))
            .setPositiveButton(R.string.ft_gleaming_unmark) { _, _ ->
                GleamingStore.setDiscovered(ctx, mob.id, false)
                refresh()
            }
            .setNegativeButton(R.string.ft_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chips.clear()
        _binding = null
    }
}
