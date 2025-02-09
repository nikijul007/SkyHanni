package at.hannibal2.skyhanni.features.mining.glacitemineshaft

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ItemAddManager
import at.hannibal2.skyhanni.data.MiningApi
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.mining.CorpseLootedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.CollectionUtils.addSearchString
import at.hannibal2.skyhanni.utils.CollectionUtils.enumMapOf
import at.hannibal2.skyhanni.utils.CollectionUtils.sumAllValues
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils.itemName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.tracker.BucketedItemTrackerData
import at.hannibal2.skyhanni.utils.tracker.SkyHanniBucketedItemTracker
import com.google.gson.annotations.Expose

@SkyHanniModule
object CorpseTracker {
    private val config get() = SkyHanniMod.feature.mining.glaciteMineshaft.corpseTracker

    private val tracker = SkyHanniBucketedItemTracker<CorpseType, BucketData>(
        "Corpse Tracker",
        { BucketData() },
        { it.mining.mineshaft.corpseProfitTracker },
        { drawDisplay(it) },
    )

    class BucketData : BucketedItemTrackerData<CorpseType>() {
        override fun resetItems() {
            corpsesLooted = enumMapOf()
        }

        override fun getDescription(bucket: CorpseType?, timesGained: Long): List<String> {
            val divisor = 1.coerceAtLeast(
                selectedBucket?.let {
                    corpsesLooted[it]?.toInt()
                } ?: corpsesLooted.sumAllValues().toInt(),
            )
            val percentage = timesGained.toDouble() / divisor
            val dropRate = LorenzUtils.formatPercentage(percentage.coerceAtMost(1.0))
            return listOf(
                "§7Dropped §e${timesGained.addSeparators()} §7times.",
                "§7Your drop rate: §c$dropRate.",
            )
        }

        override fun getCoinName(bucket: CorpseType?, item: TrackedItem) = "<no coins>"
        override fun getCoinDescription(bucket: CorpseType?, item: TrackedItem): List<String> = listOf("<no coins>")

        override fun CorpseType.isBucketSelectable() = true

        @Expose
        var corpsesLooted: MutableMap<CorpseType, Long> = enumMapOf()

        fun getCorpseCount(): Long = selectedBucket?.let { corpsesLooted[it] } ?: corpsesLooted.values.sum()
    }

    private fun addLootedCorpse(type: CorpseType) = tracker.modify { it.corpsesLooted.addOrPut(type, 1) }

    @HandleEvent
    fun onItemAdd(event: ItemAddEvent) {
        if (!isEnabled() || event.source != ItemAddManager.Source.COMMAND) return
        with(tracker) { event.addItemFromEvent() }
    }

    @HandleEvent
    fun onCorpseLooted(event: CorpseLootedEvent) {
        addLootedCorpse(event.corpseType)
        for ((itemName, amount) in event.loot) {
            if (itemName.removeColor().trim() == "Glacite Powder") continue
            NeuInternalName.fromItemNameOrNull(itemName)?.let { item ->
                tracker.modify {
                    it.addItem(event.corpseType, item, amount)
                }
            }
        }
    }

    private fun drawDisplay(bucketData: BucketData): List<Searchable> = buildList {
        addSearchString("§b§lMineshaft Corpse Profit Tracker")
        tracker.addBucketSelector(this, bucketData, "Corpse Type")

        if (bucketData.getCorpseCount() == 0L) return@buildList

        var profit = tracker.drawItems(bucketData, { true }, this)
        val applicableKeys: List<CorpseType> = bucketData.selectedBucket?.let {
            listOf(it)
        } ?: enumValues<CorpseType>().toList()
            .filter { bucketData.corpsesLooted[it] != null }
        var totalKeyCost = 0.0
        var totalKeyCount = 0
        val keyCostStrings = buildList {
            applicableKeys.forEach { keyData ->
                keyData.key?.let { key ->
                    val keyName = key.itemName
                    val price = key.getPrice()
                    val count = bucketData.corpsesLooted[keyData] ?: 0
                    val totalPrice = price * count
                    if (totalPrice > 0) {
                        profit -= totalPrice
                        totalKeyCost += totalPrice
                        totalKeyCount += count.toInt()
                        add("§7${count}x $keyName§7: §c-${totalPrice.shortFormat()}")
                    }
                }
            }
        }

        if (totalKeyCount > 0) {
            val specificKeyFormat = if (applicableKeys.count() == 1) applicableKeys.first().key!!.itemName else "§eCorpse Keys"
            val keyFormat = "§7${totalKeyCount}x $specificKeyFormat§7: §c-${totalKeyCost.shortFormat()}"
            add(
                if (applicableKeys.count() == 1) Renderable.string(keyFormat).toSearchable()
                else Renderable.hoverTips(
                    keyFormat,
                    keyCostStrings,
                ).toSearchable(),
            )
        }

        add(tracker.addTotalProfit(profit, bucketData.getCorpseCount(), "loot"))

        tracker.addPriceFromButton(this)
    }

    init {
        tracker.initRenderer({ config.position }) { isEnabled() }
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (event.newIsland == IslandType.MINESHAFT || event.newIsland == IslandType.DWARVEN_MINES) {
            tracker.firstUpdate()
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shresetcorpsetracker") {
            description = "Resets the Glacite Mineshaft Corpse Tracker"
            category = CommandCategory.USERS_RESET
            callback { tracker.resetCommand() }
        }
    }

    fun isEnabled() =
        LorenzUtils.inSkyBlock && config.enabled && (
            IslandType.MINESHAFT.isInIsland() ||
                (!config.onlyInMineshaft && MiningApi.inGlacialTunnels())
            )
}
