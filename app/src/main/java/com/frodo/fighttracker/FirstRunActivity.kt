package com.frodo.fighttracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.graphics.BitmapFactory

class FirstRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "UPDATE 2.8 \uD83D\uDD25"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 40, 20, 40)
        }

        val textView = TextView(this).apply {

            setText(
                """
                • Item Assess v 1.2 - Faster and Lighter
                • Updated internal items database for Riftfall items
                • Added support for external database from url
                
                Click play button on bottom right to hide/show floating controller.
                Use it to start/stop live Assess.
                Go to item and you will see item qualities
                Click on overlay to copy Name+ X% to clipboard for renaming
                
                If you encounter any bugs - feel free to hit me up on discord - Frodo9382
                
                Important note:
                Assess works only in english for now.
                Some items may fail to asses. App try to assess items with adorns, 
                but not all can provide accurate results.
                """.trimIndent()
            )

            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        val image = ImageView(this).apply {
            val input = assets.open("example.png")
            val bmp = BitmapFactory.decodeStream(input)
            setImageBitmap(bmp)

            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = (resources.displayMetrics.widthPixels * 0.20).toInt()
                setMargins(margin, 25, margin, 25)
            }
        }

        val button = Button(this).apply {
            text = "GOT IT"
            setOnClickListener {
                finish()
            }
        }

        layout.addView(textView)
        layout.addView(button)
//        layout.addView(image)


        setContentView(layout)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    override fun onStop() {
        super.onStop()
        FirstRunStore.markShown(this)
    }
}