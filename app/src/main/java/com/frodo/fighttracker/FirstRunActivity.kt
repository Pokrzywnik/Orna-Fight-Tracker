package com.frodo.fighttracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.graphics.BitmapFactory

class FirstRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val textView = TextView(this).apply {

            setText(
                """

                This is one time popup!
                OCR SETUP GUIDE
                
                1. Open orna and get to any rewards screen
                2. Start tracker
                3. check notifications from fight tracker - click Crop button
                4. Drag green box over reward area as shown below
                5. Resize using corners
                6. Press SAVE button (top right)
                7. Done — app remembers it

                """.trimIndent()
            )

            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }

        val image = ImageView(this).apply {
            val input = assets.open("example.jpg")
            val bmp = BitmapFactory.decodeStream(input)
            setImageBitmap(bmp)
            adjustViewBounds = true
        }

        val button = Button(this).apply {
            text = "GOT IT"
            setOnClickListener {
                FirstRunStore.setSeen(this@FirstRunActivity)
                finish()
            }
        }

        layout.addView(textView)
        layout.addView(image)
        layout.addView(button)

        setContentView(layout)
    }
}