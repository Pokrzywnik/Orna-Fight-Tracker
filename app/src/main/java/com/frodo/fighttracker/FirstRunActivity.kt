package com.frodo.fighttracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.graphics.BitmapFactory

class FirstRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "UPDATE 2.0 🎉"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 40, 20, 40)
        }

        val textView = TextView(this).apply {

            setText(
                """
                • tap to switch between total/per minute overlay
                • Added HOA towers guild    • better OCR   • new guide below 
                
                OCR SETUP GUIDE
                
                1. Open orna and get to any rewards screen
                2. Start tracker
                3. check notifications from fight tracker - click Crop button
                4. Drag green box over reward area as shown below
                5. Resize using corners
                6. Make sure there is space left/right and top border is high
                7. Press SAVE button (top right)
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
                val margin = (resources.displayMetrics.widthPixels * 0.15).toInt()
                setMargins(margin, 20, margin, 20)
            }
        }

        val button = Button(this).apply {
            text = "GOT IT"
            setOnClickListener {
                FirstRunStore.markShown(this@FirstRunActivity)
                finish()
            }
        }

        layout.addView(textView)
        layout.addView(image)
        layout.addView(button)

        setContentView(layout)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
}