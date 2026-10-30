package com.frodo.fighttracker

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * CSV / date helpers shared by MaterialsFragment and the bottom-bar badge
 * (a dot on "Materials" when a SELECTED material is available today).
 * Logic is the same as it always was in MaterialsFragment.
 */
object MaterialAvailability {

    fun files(context: Context): List<String> {

        val anguish = when (SettingsStore.getAnguishVersion(context)) {
            "2.0" -> "anguish_2_0.csv"
            else -> "anguish_1_0.csv"
        }

        return listOf(
            "remembrance.csv",
            "coral.csv",
            anguish,
            "sparring.csv",
            "trials.csv",
            "towers.csv",
            "towers_hoa.csv"
        )
    }

    fun loadCsv(context: Context, fileName: String): List<MaterialEntry> {

        val list = mutableListOf<MaterialEntry>()

        context.assets.open(fileName).use { input ->

            val reader = BufferedReader(InputStreamReader(input))

            reader.readLine()

            reader.forEachLine { line ->

                val parts = line.split(",", limit = 3)

                if (parts.size >= 3) {

                    list.add(
                        MaterialEntry(
                            parts[0],
                            parts[1],
                            parts[2].replace("\"", "")
                        )
                    )
                }
            }
        }

        return list
    }

    fun parseItems(raw: String): List<String> {

        return raw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun dateString(offset: Long): String {

        val date = LocalDate.now().plusDays(offset)

        val month = date.month.getDisplayName(
            TextStyle.FULL,
            Locale.ENGLISH
        )

        return "$month ${date.dayOfMonth}"
    }

    /** True when any selected material is in an enabled guild's list for today. */
    fun hasSelectedMaterialToday(context: Context): Boolean {

        SelectedMaterials.load(context)
        SelectedGuilds.load(context)

        val selected = SelectedMaterials.selected

        if (selected.isEmpty()) return false

        val today = dateString(0)

        return files(context)
            .filter { SelectedGuilds.isEnabled(it) }
            .any { file ->

                try {
                    loadCsv(context, file).any { entry ->
                        entry.date.equals(today, true) &&
                                parseItems(entry.items).any { it in selected }
                    }
                } catch (e: Exception) {
                    false
                }
            }
    }
}
