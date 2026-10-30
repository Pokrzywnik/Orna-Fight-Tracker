package com.frodo.fighttracker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentGuidesBinding

/**
 * Guides tab (replaces the Monuments + Towers tabs):
 *   "Monuments" | "Tower heights" segmented control, a read-only URL bar
 *   and one WebFragment per segment.
 *
 * Each WebFragment is created once (the first time its segment is shown)
 * and afterwards only shown / hidden, so the pages keep their state.
 */
class GuidesFragment : Fragment(R.layout.fragment_guides) {

    companion object {

        private const val KEY_SELECTED = "guides_selected"

        private val TAGS = arrayOf("guides_monuments", "guides_towers")

        private val URLS = arrayOf(
            "https://floorchart.top/",
            "https://codex.fqegg.top/#/tower"
        )
    }

    private var _binding: FragmentGuidesBinding? = null

    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = savedInstanceState?.getInt(KEY_SELECTED, 0) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED, selected)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentGuidesBinding.bind(view)
        _binding = b

        b.guidesSegment.check(
            if (selected == 0) R.id.btnMonuments else R.id.btnTowers
        )

        showSegment(selected)

        b.guidesSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->

            if (!isChecked) return@addOnButtonCheckedListener

            showSegment(if (checkedId == R.id.btnMonuments) 0 else 1)
        }
    }

    private fun showSegment(index: Int) {

        selected = index

        val fm = childFragmentManager

        val tx = fm.beginTransaction().setReorderingAllowed(true)

        for (i in TAGS.indices) {

            val existing = fm.findFragmentByTag(TAGS[i])

            if (i == index) {

                if (existing == null) {
                    tx.add(
                        R.id.guidesWebContainer,
                        WebFragment.newInstance(URLS[i]),
                        TAGS[i]
                    )
                } else {
                    tx.show(existing)
                }

            } else if (existing != null) {

                tx.hide(existing)
            }
        }

        tx.commit()

        _binding?.urlHost?.text =
            android.net.Uri.parse(URLS[index]).host ?: URLS[index]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
