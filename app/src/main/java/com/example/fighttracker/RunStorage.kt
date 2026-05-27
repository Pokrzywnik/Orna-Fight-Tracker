package com.example.fighttracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RunStorage {

    private const val PREF = "runs"
    private const val KEY = "history"

    fun saveRun(
        context: Context,
        run: RunRecord
    ) {

        android.util.Log.d("RUN_HISTORY", "saveRun called")

        val prefs =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val old =
            prefs.getString(KEY, "[]")

        android.util.Log.d("RUN_HISTORY", "OLD JSON = $old")

        val array = JSONArray(old)

        val obj = JSONObject()

        obj.put("runid", run.runid)
        obj.put("date", run.date)
        obj.put("duration", run.duration)
        obj.put("gold", run.gold)
        obj.put("orns", run.orns)
        obj.put("exp", run.exp)

        android.util.Log.d(
            "RUN_HISTORY",
            "ADDING RUN -> ${obj}"
        )

        array.put(obj)

        prefs.edit()
            .putString(KEY, array.toString())
            .apply()

        android.util.Log.d(
            "RUN_HISTORY",
            "FINAL JSON = ${array}"
        )
    }

    fun loadRuns(
        context: Context
    ): List<RunRecord> {

        val prefs =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val raw =
            prefs.getString(KEY, "[]")

        val array = JSONArray(raw)

        val list = mutableListOf<RunRecord>()

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            list.add(
                RunRecord(
                    obj.getString("runid"),
                    obj.getString("date"),
                    obj.getString("duration"),
                    obj.getLong("gold"),
                    obj.getLong("orns"),
                    obj.getLong("exp")
                )
            )
        }

        return list.reversed()
    }

    fun deleteRun(context: Context, runid: String) {

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY, "[]"))

        val newArray = JSONArray()

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            if (obj.getString("runid") != runid) {
                newArray.put(obj)
            }
        }

        prefs.edit()
            .putString(KEY, newArray.toString())
            .apply()
    }
}