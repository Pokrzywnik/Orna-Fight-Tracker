package com.frodo.fighttracker

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
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
import com.google.android.material.button.MaterialButton
import androidx.appcompat.content.res.AppCompatResources

class MaterialsFragment : Fragment() {

    private lateinit var root: LinearLayout

    private fun getFiles(): List<String> {
        val anguish = when (SettingsStore.getAnguishVersion(requireContext())) {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        SelectedMaterials.load(requireContext())
        SelectedGuilds.load(requireContext())

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

    private fun guildDisplayName(file: String): String {

        return when(file) {
            "anguish_1_0.csv" -> "Anguish 1.0"
            "anguish_2_0.csv" -> "Anguish 2.0"
            "remembrance.csv" -> "Remembrance"
            "coral.csv" -> "Coral"
            "sparring.csv" -> "Sparring"
            "trials.csv" -> "Trials"
            "towers.csv" -> "Towers"
            "towers_hoa.csv" -> "Towers (HoA)"
            else -> file
        }
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

        getFiles()
            .filter { SelectedGuilds.isEnabled(it) }
            .forEach {
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

        

        getFiles()
            .filter { SelectedGuilds.isEnabled(it) }
            .forEach { file ->

                val entries = loadCsv(file)

                val todayEntry =
                    entries.find { it.date.equals(today, true) }

                val tomorrowEntry =
                    entries.find { it.date.equals(tomorrow, true) }

                root.addView(
                    createGuildCard(
                        guildDisplayName(file),
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

    private fun createGuildPairHeader(left: String, right: String): View {

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun buildCell(text: String): View {
            return LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
            }.apply {
                addView(TextView(requireContext()).apply {
                    this.text = text
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
            }
        }

        row.addView(buildCell(left))
        row.addView(buildCell(right))

        return row
    }

    private fun createGuildLabel(text: String, iconName: String): View {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val icon = ImageView(requireContext()).apply {

            val dp = resources.displayMetrics.density
            val size = (26 * dp).toInt()

            layoutParams = LinearLayout.LayoutParams(size, size)

            val asset = try {
                requireContext().assets.open("$iconName.png")
            } catch (e: Exception) {
                null
            }

            if (asset != null) {
                setImageDrawable(android.graphics.drawable.Drawable.createFromStream(asset, null))
            }
        }

        val label = TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(6, 0, 0, 0)
        }

        container.addView(icon)
        container.addView(label)

        return container
    }

    private fun createHeaderAction(
        iconRes: Int,
        label: String,
        onClick: () -> Unit
    ): View {

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val icon = ImageView(requireContext()).apply {

            val color = com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorPrimary,
                "getColor"
            )
            setColorFilter(color)
            val dp = resources.displayMetrics.density
            val size = (32 * dp).toInt()

            layoutParams = LinearLayout.LayoutParams(size, size)

            setImageResource(iconRes)

            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val text = TextView(requireContext()).apply {
            this.text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }

        root.addView(icon)
        root.addView(text)

        root.setOnClickListener { onClick() }

        return root
    }

    private fun createTodayHeader(): View {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 24)
        }


        val title = TextView(requireContext()).apply {
            text = "Materials Today"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)

            setPadding(0, 16, 0, 0)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val buttonRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }


        buttonRow.addView(
            createHeaderAction(
                iconRes = R.drawable.ic_calendar_month,
                label = "Calendar"
            ) {
                showMaterialCalendar()
            }
        )


        buttonRow.addView(
            createHeaderAction(
                iconRes = R.drawable.ic_explore,
                label = "Guilds"
            ) {
                showGuildSelector()
            }
        )


        buttonRow.addView(
            createHeaderAction(
                iconRes = R.drawable.ic_checklist,
                label = "Materials"
            ) {

                val allEntries = mutableListOf<MaterialEntry>()

                getFiles()
                    .filter { SelectedGuilds.isEnabled(it) }
                    .forEach {
                        allEntries.addAll(loadCsv(it))
                    }

                showMaterialSelector(allEntries)
            }
        )


        buttonRow.addView(
            createHeaderAction(
                iconRes = R.drawable.ic_info,
                label = "Info"
            ) {
                showCreditsDialog()
            }
        )

        container.addView(buttonRow)
        container.addView(title)

        return container
    }

    private fun showMaterialCalendar() {

        val allEntries = mutableListOf<MaterialEntry>()

        getFiles()
            .filter { SelectedGuilds.isEnabled(it) }
            .forEach {
                allEntries.addAll(loadCsv(it))
            }

        val materials = allEntries
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

        val gridContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(gridContainer)

        fun render(filter: String = "") {

            gridContainer.removeAllViews()

            val filtered = materials
                .filter {
                    it.contains(filter, ignoreCase = true)
                }
                .sorted()

            var currentRow: LinearLayout? = null

            filtered.forEachIndexed { index, material ->

                if (index % 4 == 0) {

                    currentRow = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    gridContainer.addView(currentRow)
                }

                currentRow?.addView(
                    createCalendarMaterialIcon(
                        material,
                        allEntries
                    )
                )
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

        layout.addView(search)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Material Calendar")
            .setView(layout)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMaterialDates(
        material: String,
        entries: List<MaterialEntry>
    ) {

        data class MaterialOccurrence(
            val date: LocalDate,
            val guild: String
        )

        val currentYear = LocalDate.now().year
        val today = LocalDate.now()

        val formatter =
            java.time.format.DateTimeFormatter.ofPattern(
                "MMMM d yyyy",
                Locale.ENGLISH
            )

        val occurrences = mutableListOf<MaterialOccurrence>()

        getFiles()
            .filter { SelectedGuilds.isEnabled(it) }
            .forEach { file ->

            val guildName = guildDisplayName(file)

            loadCsv(file)
                .filter {
                    parseItems(it.items).contains(material)
                }
                .forEach { entry ->

                    try {

                        var date = LocalDate.parse(
                            "${entry.date} $currentYear",
                            formatter
                        )

                        if (date.isBefore(today)) {
                            date = date.plusYears(1)
                        }

                        occurrences.add(
                            MaterialOccurrence(
                                date,
                                guildName
                            )
                        )

                    } catch (_: Exception) {
                    }
                }
        }

        val nextFive =
            occurrences
                .sortedBy { it.date }
                .take(5)

        val text = buildString {

            append(material)
            append("\n\n")

            if (nextFive.isEmpty()) {

                append("No upcoming dates found.")

            } else {

                nextFive.forEach {

                    append("• ")

                    append(
                        it.date.month.getDisplayName(
                            TextStyle.FULL,
                            Locale.getDefault()
                        )
                    )

                    append(" ")
                    append(it.date.dayOfMonth)

                    append(" (")
                    append(it.guild)
                    append(")")

                    append("\n")
                }
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Next 5 Occurrences")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun createCalendarMaterialIcon(
        material: String,
        entries: List<MaterialEntry>
    ): View {

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 8)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnClickListener {
                showMaterialDates(material, entries)
            }
        }

        val image = ImageView(requireContext()).apply {

            val dp = resources.displayMetrics.density
            val size = (48 * dp).toInt()

            layoutParams = LinearLayout.LayoutParams(
                size,
                size
            )

            scaleType = ImageView.ScaleType.FIT_CENTER

            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            load(getMaterialIconUrl(material)) {

                crossfade(false)

                allowHardware(false)

                listener(
                    onSuccess = { _, _ ->
                        drawable?.setFilterBitmap(false)
                    }
                )

                error(android.R.drawable.ic_menu_help)
            }
        }

        val text = TextView(requireContext()).apply {
            this.text = material
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        root.addView(image)
        root.addView(text)

        return root
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

    private fun showGuildSelector() {

        val guilds = getFiles()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,32,32,32)
        }

        guilds.forEach { file ->

            val check = android.widget.CheckBox(requireContext()).apply {

                text = guildDisplayName(file)

                isChecked = SelectedGuilds.isEnabled(file)

                setOnCheckedChangeListener { _, checked ->

                    SelectedGuilds.setEnabled(
                        requireContext(),
                        file,
                        checked
                    )

                    rebuildMaterials()
                }
            }

            layout.addView(check)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Guilds")
            .setView(layout)
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

            val dp = resources.displayMetrics.density
            val size = (76 * dp).toInt()

            layoutParams = LinearLayout.LayoutParams(size, size)

            foregroundGravity = Gravity.CENTER
        }

        if (SelectedMaterials.selected.contains(material)) {

            val aura = AuraWebView(requireContext()).apply {

                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )

                loadAura(getSelectedAura())
            }

            frame.addView(aura)
        }

        val image = ImageView(requireContext()).apply {

            val dp = resources.displayMetrics.density
            val iconSize = (40 * dp).toInt()

            layoutParams = android.widget.FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.CENTER
            )

            scaleType = ImageView.ScaleType.FIT_CENTER

            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            load(getMaterialIconUrl(material)) {

                crossfade(false)

                allowHardware(false)

                listener(
                    onSuccess = { _, _ ->
                        drawable?.setFilterBitmap(false)
                    }
                )

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

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f

                setColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )

                setStroke(
                    2,
                    com.google.android.material.color.MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOutline,
                        0
                    )
                )
            }

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

        card.addView(createMiniSection(title, "TODAY", today))
        card.addView(createMiniSection(title, "TOMORROW", tomorrow))

        return card
    }

    private fun createMiniSection(
        guildTitle: String,
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

        Log.d("TITLES", guildTitle)
        if (guildTitle.contains("Anguish 2.0", ignoreCase = true)) {

            val groups = listOf(
                Triple("Agony", "proof_agony", items.subList(0, 2)),
                Triple("Despair", "proof_despair", items.subList(2, 4)),
                Triple("Melancholy", "proof_melancholy", items.subList(4, 6)),
                Triple("Torment", "proof_torment", items.subList(6, 8))
            )

            var leftHeader: View? = null
            var rightHeader: View? = null
            var leftRow: LinearLayout? = null
            var rightRow: LinearLayout? = null

            groups.chunked(2).forEach { pair ->

                val containerRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                pair.forEachIndexed { index, (name, icon, groupItems) ->

                    val column = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    column.addView(createGuildLabel(name, icon))
                    column.addView(createMaterialsGrid(groupItems))

                    containerRow.addView(column)
                }

                root.addView(containerRow)
            }

        } else {

            root.addView(createMaterialsGrid(items))
        }

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
            "Demonic Ore" to "demonstone",
            "Darkstone" to "dark_stone",
            "Firestone" to "fire_stone"

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