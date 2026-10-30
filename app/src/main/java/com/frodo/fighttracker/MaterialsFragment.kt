package com.frodo.fighttracker

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Materials tab. Data and behaviour are unchanged - only the look
 * (header, cards, spacing, dialogs) follows the new design tokens.
 */
class MaterialsFragment : Fragment() {

    private lateinit var root: LinearLayout

    // ------------------------------------------------------------------
    // small view helpers
    // ------------------------------------------------------------------

    private fun dp(value: Int): Int = UiKit.dp(requireContext(), value)

    private fun color(res: Int): Int = ContextCompat.getColor(requireContext(), res)

    private fun text(
        value: String,
        appearance: Int,
        sizeSp: Float? = null
    ): TextView = TextView(requireContext()).apply {
        setTextAppearance(appearance)
        text = value
        if (sizeSp != null) textSize = sizeSp
    }

    private fun card(): LinearLayout = LinearLayout(requireContext()).apply {

        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(dp(10), dp(12), dp(10), dp(12))

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
    }

    private fun searchField(hintText: String): EditText =
        EditText(requireContext()).apply {
            setTextAppearance(R.style.TextAppearance_FT_Body)
            hint = hintText
            setHintTextColor(color(R.color.ft_muted))
            setBackgroundResource(R.drawable.bg_search)
            setPadding(dp(12), 0, dp(12), 0)
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                bottomMargin = dp(8)
            }
        }

    private fun dialogScrollHeight(): Int =
        (resources.displayMetrics.heightPixels * 0.5f).toInt()

    private fun afterTextChanged(edit: EditText, onChange: (String) -> Unit) {

        edit.addTextChangedListener(
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
                    onChange(s.toString())
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )
    }

    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        SelectedMaterials.load(requireContext())
        SelectedGuilds.load(requireContext())

        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(color(R.color.ft_bg))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }

        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
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

        return when (file) {
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

    private fun enabledFiles(): List<String> =
        MaterialAvailability.files(requireContext())
            .filter { SelectedGuilds.isEnabled(it) }

    private fun loadCsv(file: String): List<MaterialEntry> =
        MaterialAvailability.loadCsv(requireContext(), file)

    private fun parseItems(raw: String): List<String> =
        MaterialAvailability.parseItems(raw)

    private fun rebuildMaterials() {

        if (!::root.isInitialized) return

        root.removeAllViews()

        val today = MaterialAvailability.dateString(0)
        val tomorrow = MaterialAvailability.dateString(1)

        // every enabled CSV is read once per rebuild
        val entriesByFile = enabledFiles().associateWith { loadCsv(it) }

        val allEntries = entriesByFile.values.flatten()

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

        root.addView(createHeader())

        root.addView(sectionTitle(getString(R.string.ft_materials_today)))
        root.addView(createGridCard(todayItems))

        root.addView(sectionTitle(getString(R.string.ft_materials_tomorrow)))
        root.addView(createGridCard(tomorrowItems))

        entriesByFile.forEach { (file, entries) ->

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

    // ------------------------------------------------------------------
    // header + sections
    // ------------------------------------------------------------------

    private fun sectionTitle(title: String): View {

        return text(title, R.style.TextAppearance_FT_Section).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
                bottomMargin = dp(8)
            }
        }
    }

    private fun createHeaderAction(
        iconRes: Int,
        description: String,
        onClick: () -> Unit
    ): View {

        return ImageButton(requireContext()).apply {

            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(8)
            }

            setBackgroundResource(R.drawable.bg_icon_button)
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.ft_text))
            contentDescription = description

            setOnClickListener { onClick() }
        }
    }

    /** Overline + "Materials" title on the left, four 40dp icon buttons on the right. */
    private fun createHeader(): View {

        val ctx = requireContext()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        titles.addView(
            text(getString(R.string.ft_overline_forecast), R.style.TextAppearance_FT_Overline)
        )

        val title = text(getString(R.string.ft_materials_title), R.style.TextAppearance_FT_Title)

        // four buttons + title must fit on a 360dp phone
        title.maxLines = 1
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            title, 20, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP
        )

        titles.addView(title)

        row.addView(titles)

        row.addView(
            createHeaderAction(
                R.drawable.ic_calendar_month,
                getString(R.string.ft_calendar)
            ) {
                showMaterialCalendar()
            }
        )

        row.addView(
            createHeaderAction(
                R.drawable.ic_explore,
                getString(R.string.ft_guilds)
            ) {
                showGuildSelector()
            }
        )

        row.addView(
            createHeaderAction(
                R.drawable.ic_checklist,
                getString(R.string.ft_materials_button)
            ) {

                val allEntries = mutableListOf<MaterialEntry>()

                enabledFiles().forEach {
                    allEntries.addAll(loadCsv(it))
                }

                showMaterialSelector(allEntries)
            }
        )

        row.addView(
            createHeaderAction(
                R.drawable.ic_info,
                getString(R.string.ft_info)
            ) {
                showCreditsDialog()
            }
        )

        return row
    }

    // ------------------------------------------------------------------
    // dialogs
    // ------------------------------------------------------------------

    private fun showMaterialCalendar() {

        val allEntries = mutableListOf<MaterialEntry>()

        enabledFiles().forEach {
            allEntries.addAll(loadCsv(it))
        }

        val materials = allEntries
            .flatMap { parseItems(it.items) }
            .distinct()
            .sorted()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        val search = searchField(getString(R.string.ft_search_material))

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

        afterTextChanged(search) { render(it) }

        layout.addView(search)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dialogScrollHeight()
            )
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ft_material_calendar)
            .setView(layout)
            .setNegativeButton(R.string.ft_close, null)
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

        enabledFiles()
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

        val message = buildString {

            append(material)
            append("\n\n")

            if (nextFive.isEmpty()) {

                append(getString(R.string.ft_no_upcoming))

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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ft_next_five)
            .setMessage(message)
            .setPositiveButton(R.string.ft_ok, null)
            .show()
    }

    private fun showCreditsDialog() {

        val creditsText =
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
            setTextAppearance(R.style.TextAppearance_FT_Body)
            setPadding(dp(24), dp(16), dp(24), dp(8))
            autoLinkMask = android.text.util.Linkify.WEB_URLS
            linksClickable = true
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            textSize = 14f
            setText(creditsText)
        }

        val scroll = ScrollView(requireContext()).apply {
            addView(textView)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ft_credits)
            .setView(scroll)
            .setPositiveButton(R.string.ft_close, null)
            .show()
    }

    private fun showGuildSelector() {

        val guilds = MaterialAvailability.files(requireContext())

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        guilds.forEach { file ->

            val check = MaterialCheckBox(requireContext()).apply {

                text = guildDisplayName(file)

                minimumHeight = dp(48)

                isChecked = SelectedGuilds.isEnabled(file)

                setOnCheckedChangeListener { _, checked ->

                    SelectedGuilds.setEnabled(
                        requireContext(),
                        file,
                        checked
                    )

                    rebuildMaterials()

                    (activity as? MainActivity)?.refreshBadges()
                }
            }

            layout.addView(check)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ft_select_guilds)
            .setView(layout)
            .setPositiveButton(R.string.ft_close, null)
            .show()
    }

    private fun showMaterialSelector(entries: List<MaterialEntry>) {

        val allMaterials = entries
            .flatMap { parseItems(it.items) }
            .distinct()
            .sorted()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val search = searchField(getString(R.string.ft_search_material))

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

                    val check = MaterialCheckBox(requireContext()).apply {

                        text = material

                        minimumHeight = dp(48)

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

                            (activity as? MainActivity)?.refreshBadges()
                        }
                    }

                    container.addView(check)
                }
        }

        render()

        afterTextChanged(search) { render(it) }

        scroll.addView(container)

        layout.addView(search)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dialogScrollHeight()
            )
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ft_select_materials)
            .setView(layout)
            .setPositiveButton(R.string.ft_close, null)
            .show()
    }

    // ------------------------------------------------------------------
    // material icons
    // ------------------------------------------------------------------

    private fun loadMaterialIcon(image: ImageView, material: String) {

        image.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        image.load(getMaterialIconUrl(material)) {

            crossfade(false)

            allowHardware(false)

            listener(
                onSuccess = { _, _ ->
                    image.drawable?.setFilterBitmap(false)
                }
            )

            error(android.R.drawable.ic_menu_help)
        }
    }

    private fun createCalendarMaterialIcon(
        material: String,
        entries: List<MaterialEntry>
    ): View {

        val cell = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(8), dp(2), dp(8))

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

            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))

            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        loadMaterialIcon(image, material)

        val label = text(material, R.style.TextAppearance_FT_Muted, 10f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        cell.addView(image)
        cell.addView(label)

        return cell
    }

    private fun createMaterialsGrid(items: List<String>): View {

        val grid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        var currentRow: LinearLayout? = null

        items.forEachIndexed { index, item ->

            if (index % 4 == 0) {
                currentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                grid.addView(currentRow)
            }

            currentRow?.addView(createMaterialIcon(item))
        }

        return grid
    }

    /** Grid inside a card; "Nothing listed" when there is nothing that day. */
    private fun createGridCard(items: List<String>): View {

        val container = card()

        if (items.isEmpty()) {

            container.addView(
                text(getString(R.string.ft_nothing_listed), R.style.TextAppearance_FT_Muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(8))
                }
            )

        } else {

            container.addView(createMaterialsGrid(items))
        }

        return container
    }

    private fun getSelectedAura(): String {

        val prefs = requireContext()
            .getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

        return prefs.getString("selected_aura", "song.webp") ?: "song.webp"
    }

    private fun createMaterialIcon(material: String): View {

        val cell = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(6), dp(2), dp(6))

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val frame = FrameLayout(requireContext()).apply {

            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))

            foregroundGravity = Gravity.CENTER
        }

        if (SelectedMaterials.selected.contains(material)) {

            val aura = AuraWebView(requireContext()).apply {

                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                loadAura(getSelectedAura())
            }

            frame.addView(aura)
        }

        val image = ImageView(requireContext()).apply {

            layoutParams = FrameLayout.LayoutParams(
                dp(40),
                dp(40),
                Gravity.CENTER
            )

            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        loadMaterialIcon(image, material)

        frame.addView(image)

        val label = text(material, R.style.TextAppearance_FT_Muted, 10f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }

        cell.addView(frame)
        cell.addView(label)

        return cell
    }

    // ------------------------------------------------------------------
    // guild cards
    // ------------------------------------------------------------------

    private fun createGuildCard(
        title: String,
        today: MaterialEntry?,
        tomorrow: MaterialEntry?
    ): View {

        val container = card()

        container.setPadding(dp(12), dp(14), dp(12), dp(8))

        container.addView(
            text(title, R.style.TextAppearance_FT_Section).apply {
                setPadding(dp(2), 0, 0, dp(4))
            }
        )

        container.addView(createMiniSection(title, getString(R.string.ft_today), today))

        container.addView(
            View(requireContext()).apply {
                setBackgroundColor(color(R.color.ft_line))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
            }
        )

        container.addView(createMiniSection(title, getString(R.string.ft_tomorrow), tomorrow))

        return container
    }

    private fun createGuildLabel(label: String, iconName: String): View {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }

        val icon = ImageView(requireContext()).apply {

            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))

            val asset = try {
                requireContext().assets.open("$iconName.png")
            } catch (e: Exception) {
                null
            }

            if (asset != null) {
                setImageDrawable(android.graphics.drawable.Drawable.createFromStream(asset, null))
            }
        }

        val labelView = text(label, R.style.TextAppearance_FT_Label, 14f).apply {
            setPadding(dp(6), 0, 0, 0)
        }

        container.addView(icon)
        container.addView(labelView)

        return container
    }

    private fun createMiniSection(
        guildTitle: String,
        label: String,
        entry: MaterialEntry?
    ): View {

        val section = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        section.addView(
            text(label, R.style.TextAppearance_FT_Overline).apply {
                setPadding(dp(2), 0, 0, dp(4))
            }
        )

        val items = parseItems(entry?.items ?: "")

        // Anguish 2.0 lists 8 items: 2 per proof type. If the day has fewer
        // (missing entry) fall back to the plain grid instead of crashing.
        if (guildTitle.contains("Anguish 2.0", ignoreCase = true) && items.size >= 8) {

            val groups = listOf(
                Triple("Agony", "proof_agony", items.subList(0, 2)),
                Triple("Despair", "proof_despair", items.subList(2, 4)),
                Triple("Melancholy", "proof_melancholy", items.subList(4, 6)),
                Triple("Torment", "proof_torment", items.subList(6, 8))
            )

            groups.chunked(2).forEach { pair ->

                val containerRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                pair.forEach { (name, icon, groupItems) ->

                    val column = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    column.addView(createGuildLabel(name, icon))
                    column.addView(createMaterialsGrid(groupItems))

                    containerRow.addView(column)
                }

                section.addView(containerRow)
            }

        } else if (items.isEmpty()) {

            section.addView(
                text(getString(R.string.ft_nothing_listed), R.style.TextAppearance_FT_Muted).apply {
                    setPadding(dp(2), 0, 0, dp(4))
                }
            )

        } else {

            section.addView(createMaterialsGrid(items))
        }

        return section
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
}
