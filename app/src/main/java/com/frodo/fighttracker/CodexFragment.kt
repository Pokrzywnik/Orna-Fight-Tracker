package com.frodo.fighttracker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentCodexBinding

/**
 * Codex tab: header + "Aussie's Codex | Codex Yaco" segmented control
 * (replaces the old radio group in Settings) + a WebFragment.
 */
class CodexFragment : Fragment(R.layout.fragment_codex) {

    companion object {

        const val TYPE_AUSSIE = "AUSSIE"
        const val TYPE_YACO = "YACO"

        private const val WEB_TAG = "codex_web"

        fun urlFor(type: String): String =
            if (type == TYPE_AUSSIE) {
                "https://www.aussiescodex.com/orna-items"
            } else {
                "https://codex.fqegg.top/#/"
            }
    }

    private var _binding: FragmentCodexBinding? = null

    private fun webFragment(): WebFragment? =
        childFragmentManager.findFragmentByTag(WEB_TAG) as? WebFragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentCodexBinding.bind(view)
        _binding = b

        val ctx = requireContext()

        val type = SettingsStore.getCodexType(ctx)

        // created once, restored by the child fragment manager afterwards
        if (webFragment() == null) {
            childFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                    R.id.codexWebContainer,
                    WebFragment.newInstance(urlFor(type)),
                    WEB_TAG
                )
                .commit()
        }

        b.codexSegment.check(
            if (type == TYPE_AUSSIE) R.id.btnAussie else R.id.btnYaco
        )

        b.codexSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->

            if (!isChecked) return@addOnButtonCheckedListener

            val newType =
                if (checkedId == R.id.btnAussie) TYPE_AUSSIE else TYPE_YACO

            if (newType == SettingsStore.getCodexType(ctx)) {
                return@addOnButtonCheckedListener
            }

            SettingsStore.setCodexType(ctx, newType)

            webFragment()?.reloadUrl(urlFor(newType))
        }

        b.settingsButton.setOnClickListener {
            (activity as? MainActivity)?.showSettingsDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
