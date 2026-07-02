package com.frodo.fighttracker

import android.content.Context
import android.graphics.RectF

object CropStore {

    private const val PREF = "crop_pref"

    fun save(context: Context, rect: RectF) {
        context.getSharedPreferences(PREF, 0).edit()
            .putFloat("l", rect.left)
            .putFloat("t", rect.top)
            .putFloat("r", rect.right)
            .putFloat("b", rect.bottom)
            .apply()
    }

    fun load(context: Context): RectF? {
        val p = context.getSharedPreferences(PREF, 0)

        if (!p.contains("l")) return null

        return RectF(
            p.getFloat("l", 0.2f),
            p.getFloat("t", 0.2f),
            p.getFloat("r", 0.8f),
            p.getFloat("b", 0.8f)
        )
    }
}