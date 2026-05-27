package com.example.fighttracker

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import coil.load
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MaterialsFragment : Fragment() {

    private lateinit var root: LinearLayout

    private val files = listOf(
        "remembrance.csv",
        "coral.csv",
        "anguish_1_0.csv",
        "sparring.csv",
        "trials.csv",
        "towers.csv"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        SelectedMaterials.load(requireContext())

        val scroll = ScrollView(requireContext())

        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        scroll.addView(root)

        rebuildMaterials()
        AuraNotifier.refresh = {
            activity?.runOnUiThread {
                rebuildMaterials()
            }
        }

        return scroll
    }

    override fun onDestroyView() {
        super.onDestroyView()

        AuraNotifier.refresh = null
    }

    private fun rebuildMaterials() {

        if (!::root.isInitialized) return

        root.removeAllViews()

        val today = getDateString(0)
        val tomorrow = getDateString(1)

        val allEntries = mutableListOf<MaterialEntry>()

        files.forEach {
            allEntries.addAll(loadCsv(it))
        }

        val todayItems = allEntries
            .filter { it.date.equals(today, true) }
            .flatMap { parseItems(it.items) }
            .distinct()
            .sorted()

        val tomorrowItems = allEntries
            .filter { it.date.equals(tomorrow, true) }
            .flatMap { parseItems(it.items) }
            .distinct()
            .sorted()

        root.addView(createTodayHeader())
        root.addView(createMaterialsGrid(todayItems))

        root.addView(sectionTitle("Materials Tomorrow"))
        root.addView(createMaterialsGrid(tomorrowItems))

        val filterButton = android.widget.Button(requireContext()).apply {
            text = "Select Materials"

            setOnClickListener {
                showMaterialSelector(allEntries)
            }
        }

        root.addView(filterButton)

        files.forEach { file ->

            val entries = loadCsv(file)

            val todayEntry =
                entries.find { it.date.equals(today, true) }

            val tomorrowEntry =
                entries.find { it.date.equals(tomorrow, true) }

            root.addView(
                createGuildCard(
                    file.removeSuffix(".csv")
                        .replace("_", " ")
                        .uppercase(),
                    todayEntry,
                    tomorrowEntry
                )
            )
        }
    }

    private fun sectionTitle(text: String): View {

        return TextView(requireContext()).apply {
            this.text = text
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 24, 0, 24)
        }
    }

    private fun createTodayHeader(): View {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(requireContext()).apply {
            text = "Materials Today"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val creditsBtn = android.widget.Button(requireContext()).apply {
            text = "Credits"

            setOnClickListener {
                showCreditsDialog()
            }
        }

        container.addView(title)
        container.addView(creditsBtn)

        return container
    }

    private fun showCreditsDialog() {

        val text =
            """
          ────────────────── ⋆⋅★⋅⋆ ──────────────────
Forecast data provided by:

-Data Entry-

Cosmo
Ethiraric
Knight411
Konq
Sirith23
Zach669
ZaharX97


--Screenshots / Sources--

Many Individuals - Orna Legends
13cat1                - Jotunheim
AussiePlz           - Jotunheim
Cher_                   - Jotunheim
costagamer          - Cade Labs
Firzen                  - OrnHub
h a r m l e s s      - OrnHub
Henry the Mage   - Jotunheim
lilysparkle           - Omnicracy
LuuCuong            - OrnHub
Maxiz                   - Omnicracy
minh tri huynh    - Omnicracy
Myrfie                  - His Spreadsheet
NyaDove             - OrnHub
Obscurity           - Omnicracy
Ripl                    - Jotunheim
Rivir                   - Omnicracy
RubberChicken  - Cade Labs
Sirith23             - His Phone
Smelty               - Omnicracy
Takru                - Jotunheim
Warren               - Omnicracy
zexterior            - Omnicracy


--Original spreadsheet--

https://docs.google.com/spreadsheets/d/1gWTEeQnFlNePLTOLCbrzyMWljJjR01L84z2tpeaOAi8/edit?gid=1635134007#gid=1635134007

          ────────────────── ⋆⋅★⋅⋆ ──────────────────
""".trimIndent()



        val textView = TextView(requireContext()).apply {
            setPadding(32, 32, 32, 32)
            autoLinkMask = android.text.util.Linkify.WEB_URLS
            linksClickable = true
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            textSize = 14f
            setText(text)
        }


        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Credits")
            .setView(textView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showMaterialSelector(entries: List<MaterialEntry>) {

        val allMaterials = entries
            .flatMap { parseItems(it.items) }
            .distinct()
            .sorted()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val search = android.widget.EditText(requireContext()).apply {
            hint = "Search material..."
        }

        val scroll = ScrollView(requireContext())

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun render(filter: String = "") {

            container.removeAllViews()

            allMaterials
                .filter {
                    it.contains(filter, ignoreCase = true)
                }
                .forEach { material ->

                    val check = android.widget.CheckBox(requireContext()).apply {

                        text = material

                        isChecked =
                            SelectedMaterials.selected.contains(material)

                        setOnCheckedChangeListener { _, checked ->

                            if (checked) {
                                SelectedMaterials.selected.add(material)
                            } else {
                                SelectedMaterials.selected.remove(material)
                            }

                            SelectedMaterials.save(requireContext())
                            rebuildMaterials()
                            AuraState.lastUpdateTime = System.currentTimeMillis()
                        }
                    }

                    container.addView(check)
                }
        }

        render()

        search.addTextChangedListener(
            object : android.text.TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    render(s.toString())
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) {}
            }
        )

        scroll.addView(container)

        layout.addView(search)
        layout.addView(scroll)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Materials")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun createMaterialsGrid(items: List<String>): View {

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        var currentRow: LinearLayout? = null

        items.forEachIndexed { index, item ->

            if (index % 4 == 0) {
                currentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                root.addView(currentRow)
            }

            currentRow?.addView(createMaterialIcon(item))
        }

        return root
    }

    private fun getSelectedAura(): String {

        val prefs = requireContext()
            .getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

        return prefs.getString("selected_aura", "song.webp") ?: "song.webp"
    }

    private fun createMaterialIcon(material: String): View {

        val root = LinearLayout(requireContext()).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER

            setPadding(4, 8, 4, 8)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val frame = android.widget.FrameLayout(requireContext()).apply {

            val size = (76 * resources.displayMetrics.density).toInt()

            layoutParams = LinearLayout.LayoutParams(size, size)

            foregroundGravity = Gravity.CENTER
        }

        if (SelectedMaterials.selected.contains(material)) {

            val aura = ImageView(requireContext()).apply {

                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )

                scaleType = ImageView.ScaleType.FIT_CENTER

                val auraName = getSelectedAura()

                load("file:///android_asset/aura/$auraName") {
                    crossfade(false)
                }
            }

            frame.addView(aura)
        }

        val image = ImageView(requireContext()).apply {

            val iconSize = (40 * resources.displayMetrics.density).toInt()

            layoutParams = android.widget.FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.CENTER
            )

            scaleType = ImageView.ScaleType.FIT_CENTER

            load(getMaterialIconUrl(material)) {
                error(android.R.drawable.ic_menu_help)
            }
        }

        frame.addView(image)

        val text = TextView(requireContext()).apply {

            this.text = material

            textSize = 10f

            gravity = Gravity.CENTER

            setPadding(0, 4, 0, 0)
        }

        root.addView(frame)
        root.addView(text)

        return root
    }

    private fun createGuildCard(
        title: String,
        today: MaterialEntry?,
        tomorrow: MaterialEntry?
    ): View {

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)

            background = resources.getDrawable(
                android.R.drawable.dialog_holo_light_frame,
                null
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.setMargins(0, 0, 0, 32)

            layoutParams = params
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        card.addView(titleText)

        card.addView(createMiniSection("TODAY", today))
        card.addView(createMiniSection("TOMORROW", tomorrow))

        return card
    }

    private fun createMiniSection(
        label: String,
        entry: MaterialEntry?
    ): View {

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        val title = TextView(requireContext()).apply {
            text = label
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }

        root.addView(title)

        val items = parseItems(entry?.items ?: "")

        root.addView(createMaterialsGrid(items))

        return root
    }

    private fun parseItems(raw: String): List<String> {

        return raw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun getMaterialIconUrl(material: String): String {

        val specialCases = mapOf(

            "Earthstone" to "earth_stone",
            "Waterstone" to "water_stone",
            "Lightningstone" to "lightning_stone",
            "Eyestone" to "eye",

            "Wolf's Blood" to "wolf_blood",
            "Demonic Ore" to "demonstone"

        )

        val normalized = specialCases[material]
            ?: material
                .lowercase()
                .replace("'", "")
                .replace(" ", "_")

        return "https://playorna.com/static/img/materials/$normalized.png"
    }

    private fun loadCsv(fileName: String): List<MaterialEntry> {

        val list = mutableListOf<MaterialEntry>()

        val input = requireContext().assets.open(fileName)

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

        return list
    }

    private fun getDateString(offset: Long): String {

        val date = LocalDate.now().plusDays(offset)

        val month = date.month.getDisplayName(
            TextStyle.FULL,
            Locale.ENGLISH
        )

        return "$month ${date.dayOfMonth}"
    }
}