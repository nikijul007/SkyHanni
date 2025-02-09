package at.hannibal2.skyhanni.features.combat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.features.combat.BestiaryConfig
import at.hannibal2.skyhanni.config.features.combat.BestiaryConfig.DisplayTypeEntry
import at.hannibal2.skyhanni.config.features.combat.BestiaryConfig.NumberFormatEntry
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.CollectionUtils.addString
import at.hannibal2.skyhanni.utils.ConfigUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.NumberUtil.romanToDecimalIfNecessary
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.NumberUtil.toRoman
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.addRenderableButton
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.init.Items
import net.minecraft.item.ItemStack

@SkyHanniModule
object BestiaryData {

    private val config get() = SkyHanniMod.feature.combat.bestiary

    private val patternGroup = RepoPattern.group("combat.bestiary.data")

    /**
     * REGEX-TEST: §7Progress to Tier 14: §b26%
     * REGEX-TEST: §7Progress to Tier XV: §b57.1%
     */
    private val tierProgressPattern by patternGroup.pattern(
        "tierprogress",
        "§7Progress to Tier [\\dIVXC]+: §b[\\d.]+%",
    )

    /**
     * REGEX-TEST: §7Overall Progress: §b55.2%
     * REGEX-TEST: §7Overall Progress: §b100% §7(§c§lMAX!§7)
     */
    private val overallProgressPattern by patternGroup.pattern(
        "overallprogress",
        "§7Overall Progress: §b[\\d.]+%(?: §7\\(§c§lMAX!§7\\))?",
    )

    /**
     * REGEX-TEST: 9/10
     * REGEX-TEST: 6/6
     */
    private val progressPattern by patternGroup.pattern(
        "progress",
        "(?<current>[0-9kKmMbB,.]+)/(?<needed>[0-9kKmMbB,.]+\$)",
    )

    /**
     * REGEX-TEST: (1/2) Bestiary ➜ The Catacombs
     * REGEX-TEST: Bestiary ➜ Dwarven Mines
     */
    private val titlePattern by patternGroup.pattern(
        "title",
        "^(?:\\(\\d+\\/\\d+\\) )?(?<title>Bestiary|.+) ➜ .+\$",
    )

    private var display = emptyList<Renderable>()
    private val mobList = mutableListOf<BestiaryMob>()
    private val stackList = mutableMapOf<Int, ItemStack>()
    private val catList = mutableListOf<Category>()
    private var inInventory = false
    private var isCategory = false
    private var overallProgressEnabled = false
    private val indexes = listOf(
        10..16,
        19..25,
        28..34,
        37..43,
    ).flatten()

    @HandleEvent
    fun onBackgroundDraw(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (inInventory) {
            config.position.renderRenderables(
                display, extraSpace = -1, posLabel = "Bestiary Data",
            )
        }
    }

    @HandleEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!isEnabled() || !inInventory) return
        for (slot in InventoryUtils.getItemsInOpenChest()) {
            val lore = slot.stack.getLore()
            if (lore.any { it == "§7Overall Progress: §b100% §7(§c§lMAX!§7)" || it == "§7Families Completed: §a100%" }) {
                slot highlight LorenzColor.GREEN
            }
            if (!overallProgressEnabled && lore.any { it == "§7Overall Progress: §cHIDDEN" }) {
                slot highlight LorenzColor.RED
            }
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (!isEnabled()) return
        val inventoryName = event.inventoryName
        val items = event.inventoryItems
        val stack = items[4] ?: return
        val bestiaryGui = isBestiaryGui(stack, inventoryName)
        if (!(inventoryName == "Bestiary ➜ Fishing" || inventoryName == "Bestiary") && !bestiaryGui) return
        isCategory = inventoryName == "Bestiary ➜ Fishing" || inventoryName == "Bestiary"
        stackList.putAll(items)
        inInventory = true
        overallProgressEnabled = isOverallProgressEnabled(items)
        update()
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        mobList.clear()
        stackList.clear()
        inInventory = false
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(2, "misc.bestiaryData", "combat.bestiary")

        event.transform(15, "combat.bestiary.numberFormat") { element ->
            ConfigUtils.migrateIntToEnum(element, NumberFormatEntry::class.java)
        }
        event.transform(15, "combat.bestiary.displayType") { element ->
            ConfigUtils.migrateIntToEnum(element, DisplayTypeEntry::class.java)
        }
    }

    private fun update() {
        display = drawDisplay()
    }

    private fun init() {
        mobList.clear()
        catList.clear()
        if (isCategory) {
            inCategory()
        } else {
            notInCategory()
        }
    }

    private fun inCategory() {
        for ((index, stack) in stackList) {
            if (stack.displayName == " ") continue
            if (!indexes.contains(index)) continue
            val name = stack.displayName
            var familiesFound: Long = 0
            var totalFamilies: Long = 0
            var familiesCompleted: Long = 0
            for ((lineIndex, loreLine) in stack.getLore().withIndex()) {
                val line = loreLine.removeColor()
                if (!line.startsWith("                    ")) continue
                val previousLine = stack.getLore()[lineIndex - 1]
                val progress = line.substring(line.lastIndexOf(' ') + 1)
                if (previousLine.contains("Families Found")) {
                    progressPattern.matchMatcher(progress) {
                        familiesFound = group("current").formatLong()
                        totalFamilies = group("needed").formatLong()
                    }
                } else if (previousLine.contains("Families Completed")) {
                    progressPattern.matchMatcher(progress) {
                        familiesCompleted = group("current").formatLong()
                    }
                }
            }
            catList.add(Category(name, familiesFound, totalFamilies, familiesCompleted))
        }
    }

    private fun notInCategory() {
        for ((index, stack) in stackList) {
            if (stack.displayName == " ") continue
            if (!indexes.contains(index)) continue
            val name = " [IVX0-9]+$".toPattern().matcher(stack.displayName).replaceFirst("")
            val level = " ([IVX0-9]+$)".toRegex().find(stack.displayName)?.groupValues?.get(1) ?: "0"
            var totalKillToMax: Long = 0
            var currentTotalKill: Long = 0
            var totalKillToTier: Long = 0
            var currentKillToTier: Long = 0
            var actualRealTotalKill: Long = 0
            for ((lineIndex, line) in stack.getLore().withIndex()) {
                val loreLine = line.removeColor()
                if (loreLine.startsWith("Kills: ")) {
                    actualRealTotalKill = "([0-9,.]+)".toRegex().find(loreLine)?.groupValues?.get(1)?.formatLong()
                        ?: 0
                }
                if (!loreLine.startsWith("                    ")) continue
                val previousLine = stack.getLore()[lineIndex - 1]
                val progress = loreLine.substring(loreLine.lastIndexOf(' ') + 1)
                if (previousLine.contains("Progress to Tier")) {
                    progressPattern.matchMatcher(progress) {
                        totalKillToTier = group("needed").formatLong()
                        currentKillToTier = group("current").formatLong()
                    }
                } else if (previousLine.contains("Overall Progress")) {
                    progressPattern.matchMatcher(progress) {
                        totalKillToMax = group("needed").formatLong()
                        currentTotalKill = group("current").formatLong()
                    }
                }
            }
            mobList.add(
                BestiaryMob(
                    name,
                    level,
                    totalKillToMax,
                    currentTotalKill,
                    totalKillToTier,
                    currentKillToTier,
                    actualRealTotalKill,
                ),
            )
        }
    }

    private fun drawDisplay() = buildList {
        if (!overallProgressEnabled) {
            addString("§7Bestiary Data")
            addString(" §cPlease enable Overall Progress")
            addString(" §cUsing the Eye of Ender highlighted in red.")
            return@buildList
        }

        init()

        addCategories()

        if (mobList.isEmpty()) return@buildList

        addList()

        addButtons()
    }

    private fun sortMobList(): MutableList<BestiaryMob> {
        val sortedMobList = when (config.displayType) {
            DisplayTypeEntry.GLOBAL_MAX -> mobList.sortedBy { it.percentToMax() }
            DisplayTypeEntry.GLOBAL_NEXT -> mobList.sortedBy { it.percentToTier() }
            DisplayTypeEntry.LOWEST_TOTAL -> mobList.sortedBy { it.actualRealTotalKill }
            DisplayTypeEntry.HIGHEST_TOTAL -> mobList.sortedByDescending { it.actualRealTotalKill }
            DisplayTypeEntry.LOWEST_MAX -> mobList.sortedBy { it.killNeededToMax() }
            DisplayTypeEntry.HIGHEST_MAX -> mobList.sortedByDescending { it.killNeededToMax() }
            DisplayTypeEntry.LOWEST_NEXT -> mobList.sortedBy { it.killNeededToNextLevel() }
            DisplayTypeEntry.HIGHEST_NEXT -> mobList.sortedByDescending { it.killNeededToNextLevel() }
            else -> mobList.sortedBy { it.actualRealTotalKill }
        }.toMutableList()
        return sortedMobList
    }

    private fun MutableList<Renderable>.addList() {
        val sortedMobList = sortMobList()

        addString("§7Bestiary Data")
        for (mob in sortedMobList) {
            val isUnlocked = mob.actualRealTotalKill != 0.toLong()
            val isMaxed = mob.percentToMax() >= 1
            if (!isUnlocked) {
                addString(" §7- ${mob.name}: §cNot unlocked!")
                continue
            }
            if (isMaxed && config.hideMaxed) continue
            val text = getMobLine(mob, isMaxed)
            val tips = getMobHover(mob)
            add(Renderable.hoverTips(text, tips) { true })
        }
    }

    private fun getMobHover(mob: BestiaryMob) = listOf(
        "§6Name: §b${mob.name}",
        "§6Level: §b${mob.level} ${if (!config.replaceRoman) "§7(${mob.level.romanToDecimalIfNecessary()})" else ""}",
        "§6Total Kills: §b${mob.actualRealTotalKill.formatNumber()}",
        "§6Kills needed to max: §b${mob.killNeededToMax().formatNumber()}",
        "§6Kills needed to next lvl: §b${mob.killNeededToNextLevel().formatNumber()}",
        "§6Current kill to next level: §b${mob.currentKillToNextLevel.formatNumber()}",
        "§6Kill needed for next level: §b${mob.killNeededForNextLevel.formatNumber()}",
        "§6Current kill to max: §b${mob.killToMax.formatNumber()}",
        "§6Percent to max: §b${mob.percentToMaxFormatted()}",
        "§6Percent to tier: §b${mob.percentToTierFormatted()}",
        "",
        "§7More info thing",
    )

    private fun getMobLine(mob: BestiaryMob, isMaxed: Boolean): String {
        val type = config.displayType
        var text = ""
        text += " §7- "
        text += "${mob.name} ${mob.level.romanOrInt()}: "
        text += if (isMaxed) {
            "§c§lMAXED! §7(§b${mob.actualRealTotalKill.formatNumber()}§7 kills)"
        } else {
            when (type) {
                DisplayTypeEntry.GLOBAL_MAX, DisplayTypeEntry.GLOBAL_NEXT -> {
                    val currentKill = when (type) {
                        DisplayTypeEntry.GLOBAL_MAX -> mob.totalKills
                        DisplayTypeEntry.GLOBAL_NEXT -> mob.currentKillToNextLevel
                        else -> 0
                    }
                    val killNeeded = when (type) {
                        DisplayTypeEntry.GLOBAL_MAX -> mob.killToMax
                        DisplayTypeEntry.GLOBAL_NEXT -> mob.killNeededForNextLevel
                        else -> 0
                    }
                    val percentage = ((currentKill.toDouble() / killNeeded) * 100).roundTo(2)
                    val suffix = if (type == DisplayTypeEntry.GLOBAL_NEXT) "§ato level ${mob.getNextLevel()}" else ""
                    "§7(§b${currentKill.formatNumber()}§7/§b${killNeeded.formatNumber()}§7) §a$percentage§6% $suffix"
                }

                DisplayTypeEntry.LOWEST_TOTAL, DisplayTypeEntry.HIGHEST_TOTAL -> {
                    "§6${mob.actualRealTotalKill.formatNumber()} §7total kills"
                }

                DisplayTypeEntry.LOWEST_MAX, DisplayTypeEntry.HIGHEST_MAX -> {
                    "§6${mob.killNeededToMax().formatNumber()} §7kills needed"
                }

                DisplayTypeEntry.LOWEST_NEXT, DisplayTypeEntry.HIGHEST_NEXT -> {
                    "§6${mob.killNeededToNextLevel().formatNumber()} §7kills needed"
                }

                else -> "§cYou are not supposed to see this, please report it to @HiZe on discord!"
            }
        }
        return text
    }

    private fun MutableList<Renderable>.addButtons() {
        addRenderableButton<NumberFormatEntry>(
            label = "Number Format",
            current = config.numberFormat,
            onChange = {
                config.numberFormat = it
                update()
            },
        )

        addRenderableButton<DisplayTypeEntry>(
            label = "Display Type",
            current = config.displayType,
            onChange = {
                config.displayType = it
                update()
            },
        )

        // TODO add function that expects a boolean
        addRenderableButton<NumberType>(
            label = "Number Type",
            current = NumberType.entries[if (config.replaceRoman) 0 else 1],
            getName = { it.type },
            onChange = {
                config.replaceRoman = !config.replaceRoman
                update()
            },
        )

        // TODO add function that expects a boolean
        addRenderableButton<HideMaxed>(
            label = "Hide Maxed",
            current = HideMaxed.entries[if (config.hideMaxed) 1 else 0],
            getName = { it.type },
            onChange = {
                config.hideMaxed = !config.hideMaxed
                update()
            },
        )
    }

    private fun MutableList<Renderable>.addCategories() {
        if (catList.isEmpty()) return
        addString("§7Category")
        for (cat in catList) {
            val info = when {
                cat.familiesCompleted == cat.totalFamilies -> "§c§lCompleted!"
                cat.familiesFound == cat.totalFamilies -> "§b${cat.familiesCompleted}§7/§b${cat.totalFamilies} §7completed"
                cat.familiesFound < cat.totalFamilies ->
                    "§b${cat.familiesFound}§7/§b${cat.totalFamilies} §7found, " +
                        "§b${cat.familiesCompleted}§7/§b${cat.totalFamilies} §7completed"

                else -> continue
            }

            addString(" §7- ${cat.name}§7: $info")
        }
    }

    private fun isOverallProgressEnabled(inventoryItems: Map<Int, ItemStack>): Boolean {
        if (inventoryItems[52]?.item == Items.ender_eye) {
            return inventoryItems[52]?.getLore()?.any { it == "§7Overall Progress: §aSHOWN" } == true
        }

        indexes.forEach { index ->
            val item = inventoryItems[index] ?: return true
            val hasTierProgress = item.getLore().any { tierProgressPattern.matches(it) }
            val hasOverallProgress = item.getLore().any { overallProgressPattern.matches(it) }
            if (hasTierProgress && !hasOverallProgress) return false
        }

        return true
    }

    private fun isBestiaryGui(stack: ItemStack?, name: String): Boolean {
        if (stack == null) return false
        val bestiaryGuiTitleMatcher = titlePattern.matcher(name)
        if (bestiaryGuiTitleMatcher.matches()) {
            if ("Bestiary" != bestiaryGuiTitleMatcher.group("title")) {
                var loreContainsFamiliesFound = false
                for (line in stack.getLore()) {
                    if (line.removeColor().startsWith("Families Found")) {
                        loreContainsFamiliesFound = true
                        break
                    }
                }
                if (!loreContainsFamiliesFound) {
                    return false
                }
            }
            return true
        } else if (name == "Search Results") {
            val loreList = stack.getLore()
            if (loreList.size >= 2 &&
                loreList[0].startsWith("§7Query: §a") &&
                loreList[1].startsWith("§7Results: §a")
            ) {
                return true
            }
        }
        return false
    }

    enum class NumberType(val type: String) {
        INT("Normal (1, 2, 3)"),
        ROMAN("Roman (I, II, III)")
    }

    enum class HideMaxed(val type: String) {
        NO("Show"),
        YES("Hide")
    }

    private fun Long.formatNumber(): String = when (config.numberFormat) {
        BestiaryConfig.NumberFormatEntry.SHORT -> this.shortFormat()
        BestiaryConfig.NumberFormatEntry.LONG -> this.addSeparators()
        else -> "0"
    }

    data class Category(
        val name: String,
        val familiesFound: Long,
        val totalFamilies: Long,
        val familiesCompleted: Long,
    )

    data class BestiaryMob(
        var name: String,
        var level: String,
        var killToMax: Long,
        var totalKills: Long,
        var killNeededForNextLevel: Long,
        var currentKillToNextLevel: Long,
        var actualRealTotalKill: Long,
    ) {

        fun killNeededToMax(): Long {
            return 0L.coerceAtLeast(killToMax - actualRealTotalKill)
        }

        fun killNeededToNextLevel(): Long {
            return 0L.coerceAtLeast(killNeededForNextLevel - currentKillToNextLevel)
        }

        fun percentToMax() = actualRealTotalKill.toDouble() / killToMax

        fun percentToMaxFormatted() = LorenzUtils.formatPercentage(percentToMax())

        fun percentToTier() =
            if (killNeededForNextLevel == 0L) 1.0 else currentKillToNextLevel.toDouble() / killNeededForNextLevel

        fun percentToTierFormatted() = LorenzUtils.formatPercentage(percentToTier())

        fun getNextLevel() = level.getNextLevel()
    }

    private fun String.romanOrInt() = romanToDecimalIfNecessary().let {
        if (config.replaceRoman || it == 0) it.toString() else it.toRoman()
    }

    private fun String.getNextLevel() = if (this == "0") {
        "I".romanOrInt()
    } else {
        val intValue = romanToDecimalIfNecessary()
        (intValue + 1).toRoman().romanOrInt()
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enabled
}
