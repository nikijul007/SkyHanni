package at.hannibal2.skyhanni.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.Perk
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryOpenEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.features.skillprogress.SkillType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.player.inventory.ContainerLocalMenu
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object UserLuckBreakdown {
    private var inMiscStats = false
    private var replaceSlot: Int? = null
    private var itemCreateCoolDown = SimpleTimeMark.farPast()
    private var skillCalcCoolDown = SimpleTimeMark.farPast()

    private val storage get() = ProfileStorageData.playerSpecific
    private val config get() = SkyHanniMod.feature.misc

    private lateinit var mainLuckItem: ItemStack
    private val mainLuckID = Items.ender_pearl
    private const val MAIN_LUCK_NAME = "§a✴ SkyHanni User Luck"

    private lateinit var fillerItem: ItemStack
    private val fillerID = Item.getItemFromBlock(Blocks.stained_glass_pane)
    private const val FILLER_NAME = " "

    private lateinit var limboItem: ItemStack
    private val limboID = Items.ender_pearl
    private const val LIMBO_NAME = "§a✴ Limbo Personal Best"

    private lateinit var skillsItem: ItemStack
    private val skillsID = Items.diamond_sword
    private const val SKILLS_NAME = "§a✴ Category: Skills"

    private lateinit var jerryItem: ItemStack
    private val jerryID = Items.paper
    private const val JERRY_NAME = "§a✴ Statspocalypse"

    private var showAllStats = true

    /**
     * REGEX-TEST: §7Show all stats: §aYes
     * REGEX-TEST: §7Show all stats: §cNope
     */
    private val showAllStatsPattern by RepoPattern.pattern(
        "misc.statsbreakdown.showallstats",
        "§7Show all stats: §.(?<toggle>.*)",
    )

    private const val LUCK_TOOLTIP = "§5§o §a✴ SkyHanni User Luck §f"
    private var inCustomBreakdown = false

    private val validItemSlots = (10..53).filter { it !in listOf(17, 18, 26, 27, 35, 36) && it !in 44..53 }
    private val invalidItemSlots = (0..53).filter { it !in validItemSlots }

    private val skillOverflowLuck = mutableMapOf<SkillType, Int>()

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!config.userluckEnabled) return
        if (event.inventory !is ContainerLocalMenu) return
        if (!inMiscStats) return

        if (event.slot == replaceSlot && !inCustomBreakdown) {
            val limboUserLuck = storage?.limbo?.userLuck ?: 0.0f
            if (limboUserLuck == 0.0f && !showAllStats) return
            if (itemCreateCoolDown.passedSince() > 3.seconds) {
                itemCreateCoolDown = SimpleTimeMark.now()
                createItems()
            }
            event.replace(mainLuckItem)
            return
        }
        if (inCustomBreakdown) {
            if (itemCreateCoolDown.passedSince() > 3.seconds) {
                itemCreateCoolDown = SimpleTimeMark.now()
                createItems()
            }
            checkItemSlot(event)
        }
    }

    private fun checkItemSlot(event: ReplaceItemEvent) {
        when (event.slot) {
            48, 49 -> return

            10 -> event.replace(skillsItem)
            11 -> event.replace(limboItem)
            12 -> event.replace(jerryItem)

            in validItemSlots -> event.remove()

            in invalidItemSlots -> {
                if (event.originalItem.item == limboID || event.originalItem.item == jerryID) return
                event.replace(fillerItem)
                return
            }
        }
    }

    @HandleEvent
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.inventoryName != "Your Stats Breakdown") {
            inMiscStats = false
            return
        }
        val inventoryName = event.inventoryItems[4]?.name.orEmpty()
        if (inventoryName != "§dMisc Stats") return
        inMiscStats = true
        replaceSlot = findValidSlot(event.inventoryItems)
        val showAllStatsLore = event.inventoryItems[50]?.getLore() ?: listOf("")
        for (line in showAllStatsLore) {
            showAllStatsPattern.matchMatcher(line) {
                showAllStats = when (group("toggle")) {
                    "Yes" -> true
                    else -> false
                }
            }
        }
        return
    }

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inMiscStats = false
        inCustomBreakdown = false
    }

    private fun findValidSlot(input: Map<Int, ItemStack>): Int? {
        for (slot in input.keys) {
            if (slot !in validItemSlots && slot < 44) continue
            val itemStack = input[slot]
            if (itemStack?.name == " ") {
                return slot
            }
        }
        return null
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun onTooltip(event: ToolTipEvent) {
        if (!config.userluckEnabled) return
        if (skillCalcCoolDown.passedSince() > 3.seconds) {
            skillCalcCoolDown = SimpleTimeMark.now()
            calcSkillLuck()
        }
        val limboLuck = storage?.limbo?.userLuck?.roundTo(1) ?: 0.0f
        when (event.slot.inventory.name) {
            "Your Equipment and Stats" -> equipmentMenuTooltip(event, limboLuck)
            "Your Stats Breakdown" -> statsBreakdownLoreTooltip(event, limboLuck)
            "SkyBlock Menu" -> skyblockMenuTooltip(event, limboLuck)
        }
    }

    private fun equipmentMenuTooltip(event: ToolTipEvent, limboLuck: Float) {
        if (event.slot.slotIndex != 25) return
        if (limboLuck == 0.0f && !showAllStats) return

        val skillLuck = skillOverflowLuck.values.sum()
        var totalLuck = skillLuck + limboLuck
        val lastIndex = event.toolTip.indexOfLast { it == "§5§o" }
        if (lastIndex == -1) return

        if (Perk.STATSPOCALYPSE.isActive) {
            totalLuck *= 1.1f
        }
        val luckString = tryTruncateFloat(totalLuck)
        event.toolTip.add(lastIndex, "$LUCK_TOOLTIP$luckString")
    }

    private fun statsBreakdownLoreTooltip(event: ToolTipEvent, limboLuck: Float) {
        if (!inMiscStats) return
        if (inCustomBreakdown && event.slot.slotIndex == 48) {
            event.toolTip[1] = "§7To Your Stats Breakdown"
        }
        if (event.slot.slotIndex != 4) return
        if (limboLuck == 0.0f && !showAllStats) return

        val skillLuck = skillOverflowLuck.values.sum()
        var totalLuck = skillLuck + limboLuck
        if (Perk.STATSPOCALYPSE.isActive) {
            totalLuck *= 1.1f
        }
        val luckString = tryTruncateFloat(totalLuck)
        event.toolTip.add("§5§o §a✴ SkyHanni User Luck §f$luckString")
    }

    private fun skyblockMenuTooltip(event: ToolTipEvent, limboLuck: Float) {
        if (event.slot.slotIndex != 13) return
        val lastIndex = event.toolTip.indexOfLast { it == "§5§o" }
        if (lastIndex == -1) return

        val skillLuck = skillOverflowLuck.values.sum()
        var totalLuck = skillLuck + limboLuck
        if (totalLuck == 0f) return
        if (Perk.STATSPOCALYPSE.isActive) {
            totalLuck *= 1.1f
        }

        val luckString = tryTruncateFloat(totalLuck)
        event.toolTip.add(lastIndex, "$LUCK_TOOLTIP$luckString")
    }

    private fun tryTruncateFloat(input: Float): String {
        val string = input.addSeparators()
        return if (string.endsWith(".0")) return string.dropLast(2)
        else string
    }

    @HandleEvent
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!config.userluckEnabled) return
        if (!inMiscStats) return
        val limboUserLuck = storage?.limbo?.userLuck ?: 0.0f
        if (limboUserLuck == 0.0f && !showAllStats) return

        if (inCustomBreakdown && event.slotId != 49) event.cancel()
        when (event.slotId) {
            replaceSlot -> {
                if (inCustomBreakdown) return
                event.cancel()
                inCustomBreakdown = true
            }

            48 -> {
                if (!inCustomBreakdown) return
                inCustomBreakdown = false
            }

            else -> return
        }
    }

    private fun createItems() {
        fillerItem = ItemUtils.createItemStack(
            fillerID,
            FILLER_NAME,
            listOf(),
            1,
            15,
        )

        val limboLuck = storage?.limbo?.userLuck ?: 0.0f
        val skillLuck = skillOverflowLuck.values.sum()
        var totalLuck = skillLuck + limboLuck
        var jerryLuck = 0f
        if (Perk.STATSPOCALYPSE.isActive) {
            jerryLuck = totalLuck * .1f
        }
        totalLuck += jerryLuck

        mainLuckItem = ItemUtils.createItemStack(
            mainLuckID,
            "$MAIN_LUCK_NAME §f${tryTruncateFloat(totalLuck)}",
            createItemLore("mainMenu", totalLuck),
        )
        limboItem = ItemUtils.createItemStack(
            limboID,
            LIMBO_NAME,
            createItemLore("limbo", limboLuck),
        )
        skillsItem = ItemUtils.createItemStack(
            skillsID,
            SKILLS_NAME,
            createItemLore("skills"),
        )
        if (jerryLuck > 0) {
            jerryItem = ItemUtils.createItemStack(
                jerryID,
                JERRY_NAME,
                createItemLore("jerry", jerryLuck),
            )
        }
    }

    private fun createItemLore(type: String, luckInput: Float = 0.0f): Array<String> {
        calcSkillLuck()
        return when (type) {
            "mainMenu" -> {
                val luckString = tryTruncateFloat(luckInput.roundTo(2))
                if (luckInput == 0.0f) {
                    arrayOf(
                        "§7SkyHanni User Luck is the best stat.",
                        "",
                        "§7Flat: §a+$luckString✴",
                        "",
                        "§8You have none of this stat!",
                        "§eClick to view!",
                    )
                } else {
                    arrayOf(
                        "§7SkyHanni User Luck increases your",
                        "§7overall fortune around Hypixel SkyBlock.",
                        "",
                        "§7(Disclaimer: May not affect real drop chances)",
                        "",
                        "§eClick to view!",
                    )
                }
            }

            "limbo" -> {
                val luckString = tryTruncateFloat(luckInput.roundTo(2))
                arrayOf(
                    "§8Action",
                    "",
                    "§7Value: §a+$luckString✴",
                    "",
                    "§8Gain more by going to Limbo,",
                    "§8and obtaining a higher Personal Best§8.",
                )
            }

            "skills" -> {
                val luckString = skillOverflowLuck.values.sum()
                val firstHalf = arrayOf(
                    "§8Grouped",
                    "",
                    "§7Value: §a+$luckString✴",
                    "",
                )
                val secondHalf = arrayOf(
                    "§8Stats from your overflow skills.",
                    "§8Obtain more each 5 overflow levels!",
                )
                val sourcesList = mutableListOf<String>()
                for ((skillType, luck) in skillOverflowLuck) {
                    if (luck == 0) continue
                    sourcesList.add(" §a+$luck✴ §f${skillType.displayName} Skill")
                }
                val finalList = mutableListOf<String>()
                finalList.addAll(firstHalf)
                if (sourcesList.isNotEmpty()) {
                    finalList.addAll(sourcesList)
                    finalList.add("")
                }
                finalList.addAll(secondHalf)
                finalList.toTypedArray()
            }

            "jerry" -> {
                val luckString = tryTruncateFloat(luckInput.roundTo(2))
                arrayOf(
                    "§8Elected Mayor",
                    "",
                    "§7Value: §a+$luckString✴",
                    "",
                    "§8Stats from the currently elected",
                    "§8mayor. Proof that voting does matter.",
                )
            }

            else -> arrayOf("")
        }
    }

    private fun calcSkillLuck() {
        val storage = ProfileStorageData.profileSpecific?.skillData ?: return
        skillOverflowLuck.clear()
        for ((skillType, skillInfo) in storage) {
            val level = skillInfo.level
            val overflow = skillInfo.overflowLevel
            val luck = ((overflow - level) / 5) * 50
            skillOverflowLuck.addOrPut(skillType, luck)
        }
    }
}
