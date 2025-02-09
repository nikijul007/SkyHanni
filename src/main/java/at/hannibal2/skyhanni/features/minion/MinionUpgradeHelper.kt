package at.hannibal2.skyhanni.features.minion

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.GetFromSackApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.SackApi.getAmountInSacksOrNull
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.MinionCloseEvent
import at.hannibal2.skyhanni.events.MinionOpenEvent
import at.hannibal2.skyhanni.events.minecraft.WorldChangeEvent
import at.hannibal2.skyhanni.events.render.gui.ReplaceItemEvent
import at.hannibal2.skyhanni.features.inventory.bazaar.BazaarApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPriceOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.ItemUtils.setLore
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RegexUtils.findMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object MinionUpgradeHelper {
    private val config get() = SkyHanniMod.feature.misc.minions

    private var displayItem: ItemStack? = null
    private var itemsNeeded: Int = 0
    private var internalName: NeuInternalName? = null
    private var itemsInSacks: Int = 0

    /**
     * REGEX-TEST: §7§cYou need §6512 §cmore Nether Quartz.
     */
    private val requiredItemsPattern by RepoPattern.pattern(
        "minion.items.upgrade",
        "§7§cYou need §6(?<amount>\\d+) §cmore (?<itemName>.+)\\.",
    )

    private var lastMinionOpen = SimpleTimeMark.farPast()

    @HandleEvent
    fun onMinionOpen(event: MinionOpenEvent) {
        if (!config.minionConfigHelper) return
        lastMinionOpen = SimpleTimeMark.now()
        val lore = event.inventoryItems[50]?.getLore()?.joinToString(" ") ?: return
        requiredItemsPattern.findMatcher(lore) {
            internalName = NeuInternalName.fromItemName(group("itemName").removeColor())
            itemsNeeded = group("amount")?.toInt() ?: 0
        } ?: resetItems()

        val internalName = internalName ?: return
        if (itemsNeeded > 0) {
            itemsInSacks = internalName.getAmountInSacksOrNull() ?: 0
            displayItem = createDisplayItem(internalName)
        }
    }

    @HandleEvent
    fun onMinionClose(event: MinionCloseEvent) {
        resetItems()
    }

    // TODO make this event not necessary here.
    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        resetItems()
    }

    // TODO make this event not necessary here.
    @HandleEvent
    fun onWorldChange(event: WorldChangeEvent) {
        resetItems()
    }

    // TODO make this event not necessary here.
    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (lastMinionOpen.passedSince() > 2.seconds) {
            resetItems()
        }
    }

    private fun resetItems() {
        internalName = null
        itemsNeeded = 0
        itemsInSacks = 0
        displayItem = null
    }

    private fun createDisplayItem(internalName: NeuInternalName): ItemStack {
        val lore = createLore(internalName)
        return ItemStack(Blocks.diamond_block).setLore(lore).setStackDisplayName("§bGet Required Items")
    }

    private fun createLore(internalName: NeuInternalName): List<String> {
        val itemPrice = internalName.getPriceOrNull() ?: 0.0
        val lore = buildList {
            val itemsRemaining = itemsNeeded - itemsInSacks
            val totalCost = itemsNeeded * itemPrice
            val remainingCost = itemsRemaining * itemPrice
            val itemName = internalName.itemName

            add("§8(From SkyHanni)")
            add("")

            if (itemsInSacks > 0) {
                add("§7In sacks: §a${itemsInSacks.addSeparators()}§7x §b$itemName")
            }

            if (itemsRemaining > 0) {
                add("§7From Bazaar: §a$itemsRemaining§7x §b$itemName")
                add("§7Cost: §6${remainingCost.shortFormat()} coins")
            } else {
                add("§7All needed items are already in sacks!")
            }

            add("")
            add("§7Total price: §6${totalCost.shortFormat()} coins")

            add("")
            add(if (itemsRemaining > 0) "§eClick to open Bazaar!" else "§eClick to retrieve items from the sacks!")
        }
        return lore
    }

    @HandleEvent
    fun replaceItem(event: ReplaceItemEvent) {
        if (!config.minionConfigHelper) return
        if (event.inventory !is InventoryPlayer && event.slot == 51) {
            displayItem?.let { event.replace(it) }
        }
    }

    @HandleEvent(priority = HandleEvent.HIGH)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!config.minionConfigHelper || displayItem == null || event.slotId != 51) return
        event.cancel()
        val internalName = internalName ?: return
        val remainingItems = itemsNeeded - itemsInSacks
        if (remainingItems > 0) {
            BazaarApi.searchForBazaarItem(internalName, remainingItems)
        } else {
            GetFromSackApi.getFromSack(internalName, itemsNeeded)
        }
    }
}
