package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigFileType
import at.hannibal2.skyhanni.data.jsonobjects.repo.neu.NeuSacksJson
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.NeuRepositoryReloadEvent
import at.hannibal2.skyhanni.events.SackChangeEvent
import at.hannibal2.skyhanni.events.SackDataUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.fishing.FishingApi
import at.hannibal2.skyhanni.features.fishing.trophy.TrophyRarity
import at.hannibal2.skyhanni.features.inventory.SackDisplay
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.CollectionUtils.editCopy
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.NumberUtil.romanToDecimal
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchAll
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.StringUtils.removeNonAscii
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.gson.annotations.Expose
import net.minecraft.item.ItemStack

private typealias GemstoneQuality = SkyBlockItemModifierUtils.GemstoneQuality
private typealias GemstoneType = SkyBlockItemModifierUtils.GemstoneType

@SkyHanniModule
object SackApi {

    private val sackDisplayConfig get() = SkyHanniMod.feature.inventory.sackDisplay
    private val chatConfig get() = SkyHanniMod.feature.chat
    private val patternGroup = RepoPattern.group("data.sacks")
    private var lastOpenedInventory = ""

    val inventory = InventoryDetector { name -> sackPattern.matches(name) }

    // <editor-fold desc="Patterns">
    /**
     * REGEX-TEST: Fishing Sack
     * REGEX-TEST: Enchanted Agronomy Sack
     */
    private val sackPattern by patternGroup.pattern(
        "sack",
        "^(?:.* Sack|Enchanted .* Sack)\$",
    )

    /**
     * REGEX-TEST: §7Stored: §e28,183§7/60.5k
     * REGEX-TEST: §7Stored: §80§7/60.5k
     */
    @Suppress("MaxLineLength")
    private val numPattern by patternGroup.pattern(
        "number",
        "(?:(?:§[0-9a-f](?<level>I{1,3})§7:)?|(?:§7Stored:)?) (?<color>§[0-9a-f])(?<stored>[0-9.,kKmMbB]+)§7/(?<total>\\d+(?:[0-9.,]+)?[kKmMbB]?)",
    )

    /**
     * REGEX-TEST:  §fRough: §e78,999 §8(78,999)
     * REGEX-TEST:  §aFlawed: §e604 §8(48,320)
     * REGEX-TEST:  §9Fine: §e35 §8(224,000)
     * REGEX-TEST:  §7Amount: §a5,968
     */
    @Suppress("MaxLineLength")
    private val gemstoneCountPattern by patternGroup.pattern(
        "gemstone.count",
        " §[0-9a-f](?<quality>[A-z]*): §[0-9a-f](?<stored>\\d+(?:\\.\\d+)?(?:(?:,\\d+)?)+[kKmM]?)(?: §[0-9a-f]\\(\\d+(?:\\.\\d+)?(?:(?:,\\d+)?)+[kKmM]?\\))?",
    )

    /**
     * REGEX-TEST: §f☘ Rough Jade Gemstone
     * REGEX-TEST: §f⸕ Rough Amber Gemstone
     * REGEX-TEST: §f✧ Rough Topaz Gemstone
     * REGEX-TEST: §f✎ Rough Sapphire Gemstone
     * REGEX-TEST: §f❈ Rough Amethyst Gemstone
     * REGEX-TEST: §f❁ Rough Jasper Gemstone
     * REGEX-TEST: §f❤ Rough Ruby Gemstone
     * REGEX-TEST: §f❂ Rough Opal Gemstone
     * REGEX-TEST: §f☠ Rough Onyx Gemstone
     * REGEX-TEST: §fα Rough Aquamarine Gemstone
     * REGEX-TEST: §a☘ Flawed Citrine Gemstone
     * REGEX-TEST: §9☘ Fine Peridot Gemstone
     * REGEX-TEST: §eTopaz Gemstones
     */
    private val gemstoneItemNamePattern by patternGroup.pattern(
        "gemstone.name",
        "(?:§.)+(?:[❤❈☘⸕✎✧❁☠❂α] )?(?:(?:Rough|Flawed|Fine) )?(?<gem>[^ ]+) Gemstones?"
    )

    /**
     * REGEX-TEST: §8▶ No filter
     * REGEX-TEST: §f▶ Rough
     * REGEX-TEST: §a▶ Flawed
     * REGEX-TEST: §9▶ Fine
     */
    private val gemstoneFilterPattern by patternGroup.pattern(
        "gemstone.filter",
        "(?:§.)+▶ (?<quality>.*)"
    )
    // </editor-fold>

    var isTrophySack = false
    var gemstoneStackFilter: GemstoneQuality? = null
        private set
    private var isRuneSack = false
    private var isGemstoneSack = false
    private var sackRarity: TrophyRarity? = null

    /**
     * TODO merge all 3 lists into one:
     *
     * move item name (currently key) into AbstractSackItem
     * work with instance check
     * add custom function for render behaviour.
     * have only one render display function
     */
    //
    val sackItem = mutableMapOf<String, SackOtherItem>()
    val runeItem = mutableMapOf<String, SackRune>()
    val gemstoneItem = mutableMapOf<String, SackGemstone>()
    private val stackList = mutableMapOf<Int, ItemStack>()
    private const val GEMSTONE_FILTER_SLOT = 41

    var sackListInternalNames = emptySet<String>()
        private set

    var sackListNames = emptySet<String>()
        private set

    @HandleEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        isRuneSack = false
        isGemstoneSack = false
        gemstoneStackFilter = null
        isTrophySack = false
        runeItem.clear()
        gemstoneItem.clear()
        sackItem.clear()
        stackList.clear()
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        val inventoryName = event.inventoryName
        val isNewInventory = inventoryName != lastOpenedInventory
        lastOpenedInventory = inventoryName
        if (!inventory.isInside()) return
        val stacks = event.inventoryItems
        isRuneSack = inventoryName == "Runes Sack"
        isGemstoneSack = inventoryName == "Gemstones Sack"
        if (isGemstoneSack) {
            val filterLore = event.inventoryItems[GEMSTONE_FILTER_SLOT]?.getLore().orEmpty()
            gemstoneFilterPattern.firstMatcher(filterLore) {
                gemstoneStackFilter = GemstoneQuality.getByNameOrNull(group("quality"))
            }
        }
        isTrophySack = inventoryName.contains("Trophy Fishing Sack")
        sackRarity = inventoryName.getTrophyRarity()
        stackList.putAll(stacks)
        SackDisplay.update(isNewInventory)
    }

    private fun String.getTrophyRarity(): TrophyRarity? = when {
        this.startsWith("Bronze") -> TrophyRarity.BRONZE
        this.startsWith("Silver") -> TrophyRarity.SILVER
        else -> null
    }

    private fun NeuInternalName.getSackPrice(stored: Int): Long {
        return getPrice(sackDisplayConfig.priceSource).toLong() * stored
    }

    private fun getGemInternalName(gemType: GemstoneType, quality: GemstoneQuality = GemstoneQuality.ROUGH) =
        "${quality.name}_${gemType.name}_GEM".toInternalName()

    private fun MutableMap.MutableEntry<Int, ItemStack>.processGemstoneItem(savingSacks: Boolean) {
        var gemTypeProp: GemstoneType? = null
        gemstoneItemNamePattern.matchMatcher(value.displayName) {
            val gemName = group("gem") ?: return@matchMatcher
            gemTypeProp = GemstoneType.getByNameOrNull(gemName) ?: return@matchMatcher
        }
        val gemType = gemTypeProp ?: return
        val roughInternalName = getGemInternalName(gemType).takeIf { it.isKnownItem() } ?: return

        val gem = SackGemstone(gemType, internalName = roughInternalName)
        gem.slot = key

        gemstoneCountPattern.matchAll(value.getLore()) {
            val stored = group("stored").formatInt()
            val quality: GemstoneQuality = GemstoneQuality.getByNameOrNull(
                group("quality").takeIf { it != "Amount" }?.uppercase()
                    ?: gemstoneStackFilter?.name
                    ?: return@matchAll
            ) ?: return@matchAll

            val (multiplier, priceUpdater) = when (quality) {
                GemstoneQuality.ROUGH -> Pair(1) { price: Long ->
                    gem.roughPrice = price
                    gem.rough = stored
                }
                GemstoneQuality.FLAWED -> Pair(80) { price: Long ->
                    gem.flawedPrice = price
                    gem.flawed = stored
                }
                GemstoneQuality.FINE -> Pair(80 * 80) { price: Long ->
                    gem.finePrice = price
                    gem.fine = stored
                }
                else -> return@matchAll
            }

            gem.stored += (stored * multiplier)
            val internalName = getGemInternalName(gemType, quality)
            val price = internalName.getSackPrice(stored)
            priceUpdater(price)
            gem.price += price
            if (savingSacks) setSackItem(internalName, stored)
            if (quality == GemstoneQuality.FINE || gemstoneStackFilter != null) gemstoneItem[value.name] = gem
        }
    }

    private fun MutableMap.MutableEntry<Int, ItemStack>.processRuneItem(savingSacks: Boolean) {
        val rune = SackRune()
        numPattern.matchAll(value.getLore()) {
            val level = group("level").romanToDecimal()
            val stored = group("stored").formatInt()
            rune.stack = value
            rune.stored += stored

            when (level) {
                1 -> rune.lvl1 = stored
                2 -> rune.lvl2 = stored
                3 -> {
                    rune.slot = key
                    rune.lvl3 = stored
                    runeItem[value.name] = rune
                }
            }
            if (savingSacks) setSackItem(value.getInternalName(), stored)
        }
    }

    private fun MutableMap.MutableEntry<Int, ItemStack>.processOtherItem(savingSacks: Boolean) {
        val item = SackOtherItem()
        numPattern.firstMatcher(value.getLore()) {
            val stored = group("stored").formatInt()
            val internalName = value.getInternalName()

            item.internalName = internalName
            item.colorCode = group("color")
            item.stored = group("stored").formatInt()
            item.total = group("total").formatInt()
            if (savingSacks) setSackItem(item.internalName, item.stored)

            item.price = if (isTrophySack) {
                val filletValue = FishingApi.getFilletPerTrophy(internalName) * stored
                item.magmaFish = filletValue
                "MAGMA_FISH".toInternalName().getSackPrice(filletValue)
            } else {
                internalName.getSackPrice(stored).coerceAtLeast(0)
            }
            item.slot = key
            sackItem[value.name] = item
        }
    }

    fun getSacksData(savingSacks: Boolean) {
        if (savingSacks) sackData = ProfileStorageData.sackProfiles?.sackContents ?: return
        for (stackEntry in stackList) {
            when {
                isGemstoneSack -> { stackEntry.processGemstoneItem(savingSacks) }
                isRuneSack -> { stackEntry.processRuneItem(savingSacks) }
                else -> { stackEntry.processOtherItem(savingSacks) }
            }
        }
        if (savingSacks) saveSackData()
    }

    var sackData = mapOf<NeuInternalName, SackItem>()
        private set

    data class SackChange(val delta: Int, val internalName: NeuInternalName, val sacks: List<String>)

    // Todo: Move to repo pattern, add regex tests..?
    private val sackChangeRegex = Regex("""([+-][\d,]+) (.+) \((.+)\)""")

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!event.message.removeColor().startsWith("[Sacks]")) return

        val sackAddText = event.chatComponent.siblings.firstNotNullOfOrNull { sibling ->
            sibling.chatStyle?.chatHoverEvent?.value?.formattedText?.removeColor()?.takeIf {
                it.startsWith("Added")
            }
        }.orEmpty()
        val sackRemoveText = event.chatComponent.siblings.firstNotNullOfOrNull { sibling ->
            sibling.chatStyle?.chatHoverEvent?.value?.formattedText?.removeColor()?.takeIf {
                it.startsWith("Removed")
            }
        }.orEmpty()

        val sackChangeText = sackAddText + sackRemoveText
        if (sackChangeText.isEmpty()) return

        val otherItemsAdded = sackAddText.contains("other items")
        val otherItemsRemoved = sackRemoveText.contains("other items")

        val sackChanges = ArrayList<SackChange>()
        for (match in sackChangeRegex.findAll(sackChangeText)) {
            val delta = match.groups[1]!!.value.formatInt()
            val item = match.groups[2]!!.value
            val sacks = match.groups[3]!!.value.split(", ")

            val internalName = NeuInternalName.fromItemName(item)
            sackChanges.add(SackChange(delta, internalName, sacks))
        }
        val sackEvent = SackChangeEvent(sackChanges, otherItemsAdded, otherItemsRemoved)
        updateSacks(sackEvent)
        sackEvent.post()
        if (chatConfig.hideSacksChange) {
            if (chatConfig.hideSacksChange && (!chatConfig.onlyHideSacksChangeOnGarden || IslandType.GARDEN.isInIsland())) {
                event.blockedReason = "sacks_change"
            }
        }
    }

    @HandleEvent
    fun onNeuRepoReload(event: NeuRepositoryReloadEvent) {
        val sacksData = event.readConstant<NeuSacksJson>("sacks").sacks
        val uniqueSackItems = mutableSetOf<NeuInternalName>()

        sacksData.values.flatMap { it.contents }.forEach { uniqueSackItems.add(it) }

        sackListInternalNames = uniqueSackItems.map { it.asString() }.toSet()
        sackListNames = uniqueSackItems.map { it.itemNameWithoutColor.removeNonAscii().trim().uppercase() }.toSet()
    }

    private fun updateSacks(changes: SackChangeEvent) {
        sackData = ProfileStorageData.sackProfiles?.sackContents ?: return

        // if it gets added and subtracted but only 1 shows it will be outdated
        val justChanged = mutableMapOf<NeuInternalName, Int>()

        for (change in changes.sackChanges) {
            if (change.internalName in justChanged) {
                justChanged[change.internalName] = (justChanged[change.internalName] ?: 0) + change.delta
            } else {
                justChanged[change.internalName] = change.delta
            }
        }

        for (item in justChanged) {
            if (sackData.containsKey(item.key)) {
                val oldData = sackData[item.key]
                var newAmount = oldData!!.amount + item.value
                var changed = (newAmount - oldData.amount)
                if (newAmount < 0) {
                    newAmount = 0
                    changed = 0
                }
                sackData = sackData.editCopy { this[item.key] = SackItem(newAmount, changed, oldData.getStatus()) }
            } else {
                val newAmount = if (item.value > 0) item.value else 0
                sackData =
                    sackData.editCopy { this[item.key] = SackItem(newAmount, newAmount, SackStatus.OUTDATED) }
            }
        }

        if (changes.otherItemsAdded || changes.otherItemsRemoved) {
            for (item in sackData) {
                if (item.key in justChanged) continue
                val oldData = sackData[item.key]
                sackData = sackData.editCopy { this[item.key] = SackItem(oldData!!.amount, 0, SackStatus.ALRIGHT) }
            }
        }
        saveSackData()
    }

    private fun setSackItem(item: NeuInternalName, amount: Int) {
        sackData = sackData.editCopy { this[item] = SackItem(amount, 0, SackStatus.CORRECT) }
    }

    private fun fetchSackItem(item: NeuInternalName): SackItem {
        sackData = ProfileStorageData.sackProfiles?.sackContents ?: return SackItem(0, 0, SackStatus.MISSING)

        if (sackData.containsKey(item)) {
            return sackData[item] ?: return SackItem(0, 0, SackStatus.MISSING)
        }

        sackData = sackData.editCopy { this[item] = SackItem(0, 0, SackStatus.MISSING) }
        return sackData[item] ?: return SackItem(0, 0, SackStatus.MISSING)
    }

    private fun saveSackData() {
        ProfileStorageData.sackProfiles?.sackContents = sackData
        SkyHanniMod.configManager.saveConfig(ConfigFileType.SACKS, "saving-data")

        SackDataUpdateEvent.post()
    }

    data class SackGemstone(
        val gemType: GemstoneType,
        var internalName: NeuInternalName = NeuInternalName.NONE,
        var rough: Int = 0,
        var flawed: Int = 0,
        var fine: Int = 0,
        var roughPrice: Long = 0,
        var flawedPrice: Long = 0,
        var finePrice: Long = 0,
    ) : AbstractSackItem() {
        val priceSum: Long
            get() = roughPrice + flawedPrice + finePrice
    }

    data class SackRune(
        var stack: ItemStack? = null,
        var lvl1: Int = 0,
        var lvl2: Int = 0,
        var lvl3: Int = 0,
    ) : AbstractSackItem()

    data class SackOtherItem(
        var internalName: NeuInternalName = NeuInternalName.NONE,
        var colorCode: String = "",
        var total: Int = 0,
        var magmaFish: Int = 0,
    ) : AbstractSackItem()

    abstract class AbstractSackItem(
        var stored: Int = 0,
        var price: Long = 0,
        var slot: Int = -1,
    )

    fun NeuInternalName.getAmountInSacksOrNull(): Int? =
        fetchSackItem(this).takeIf { it.statusIsCorrectOrAlright() }?.amount

    fun NeuInternalName.getAmountInSacks(): Int = getAmountInSacksOrNull() ?: 0

    fun testSackApi(args: Array<String>) {
        if (args.size == 1) {
            if (sackListInternalNames.contains(args[0].uppercase())) {
                ChatUtils.chat("Sack data for ${args[0]}: ${fetchSackItem(args[0].toInternalName())}")
            } else {
                ChatUtils.userError("That item isn't a valid sack item.")
            }
        } else ChatUtils.userError("/shtestsackapi <internal name>")
    }
}

data class SackItem(
    @Expose val amount: Int,
    @Expose val lastChange: Int,
    @Expose private val status: SackStatus?,
) {

    fun getStatus() = status ?: SackStatus.MISSING
    fun statusIsCorrectOrAlright() = getStatus().let { it == SackStatus.CORRECT || it == SackStatus.ALRIGHT }
}

// ideally should be correct but using alright should also be fine unless they sold their whole sacks
enum class SackStatus {
    MISSING,
    CORRECT,
    ALRIGHT,
    OUTDATED,
}
