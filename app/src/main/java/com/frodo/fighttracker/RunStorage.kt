package com.frodo.fighttracker

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
        obj.put("name", run.name)
        obj.put("date", run.date)
        obj.put("duration", run.duration)
        obj.put("gold", run.gold)
        obj.put("orns", run.orns)
        obj.put("exp", run.exp)
        obj.put("shards", run.shards)

        // optional - old runs simply do not have it
        if (!run.type.isNullOrBlank()) {
            obj.put("type", run.type)
        }

        val proofsObj = JSONObject()

        for ((id, count) in run.proofs) {
            if (count > 0) {
                proofsObj.put(id, count)
            }
        }

        obj.put("proofs", proofsObj)

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

            val proofs = mutableMapOf<String, Long>()

            val proofsObj = obj.optJSONObject("proofs")

            if (proofsObj != null) {

                val keys = proofsObj.keys()

                while (keys.hasNext()) {

                    val id = keys.next()

                    proofs[id] = proofsObj.optLong(id, 0L)
                }
            }

            // optString would turn a missing / null value into "" or "null"
            val type =
                if (obj.has("type") && !obj.isNull("type"))
                    obj.optString("type", "").ifBlank { null }
                else
                    null

            list.add(
                RunRecord(
                    runid = obj.getString("runid"),
                    date = obj.getString("date"),
                    duration = obj.getString("duration"),
                    gold = obj.getLong("gold"),
                    orns = obj.getLong("orns"),
                    exp = obj.getLong("exp"),
                    shards = obj.optLong("shards", 0L),
                    name = obj.optString("name", ""),
                    proofs = proofs,
                    type = type
                )
            )
        }

        return list.reversed()
    }

    fun renameRun(
        context: Context,
        runid: String,
        newName: String
    ) {

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val array = JSONArray(
            prefs.getString(KEY, "[]")
        )

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            if (obj.getString("runid") == runid) {

                obj.put("name", newName)
                break
            }
        }

        prefs.edit()
            .putString(KEY, array.toString())
            .apply()
    }

    /** Sets (or clears, when [type] is null) the type of a saved run. */
    fun setRunType(
        context: Context,
        runid: String,
        type: String?
    ) {

        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val array = JSONArray(
            prefs.getString(KEY, "[]")
        )

        for (i in 0 until array.length()) {

            val obj = array.getJSONObject(i)

            if (obj.getString("runid") == runid) {

                if (type.isNullOrBlank()) {
                    obj.remove("type")
                } else {
                    obj.put("type", type)
                }

                break
            }
        }

        prefs.edit()
            .putString(KEY, array.toString())
            .apply()
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
