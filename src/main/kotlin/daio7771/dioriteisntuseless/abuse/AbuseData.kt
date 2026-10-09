package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Abuse Mode state of every player, saved with the world (data/dioriteisntuseless_abuse.dat in
 * the Overworld). It lives in the world rather than in the player file so it can be read and
 * changed even while the player is offline.
 *
 * Only used from the server thread.
 */
class AbuseData private constructor() : SavedData() {

    private val players = HashMap<UUID, PlayerAbuse>()

    fun get(uuid: UUID): PlayerAbuse = players.getOrPut(uuid) { PlayerAbuse() }

    fun getIfPresent(uuid: UUID): PlayerAbuse? = players[uuid]

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        for ((uuid, state) in players) {
            list.add(CompoundTag().apply {
                putUUID("Player", uuid)
                state.save(this)
            })
        }
        tag.putInt("DataVersion", DATA_VERSION)
        tag.put("Players", list)
        return tag
    }

    companion object {
        private val NAME = "${Dioriteisntuseless.MOD_ID}_abuse"

        /**
         * Goes up when the format changes and saved data has to be converted.
         * 2: the level advances by trees felled (before, by score and days).
         * 3: also by pickaxe strikes; signals go by actions (trees + strikes).
         */
        private const val DATA_VERSION = 3

        fun get(server: MinecraftServer): AbuseData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::AbuseData, NAME)

        private fun load(tag: CompoundTag): AbuseData {
            val data = AbuseData()
            val version = tag.getInt("DataVersion")
            for (entry in tag.getList("Players", Tag.TAG_COMPOUND.toInt())) {
                val compound = entry as CompoundTag
                if (!compound.hasUUID("Player")) continue
                data.players[compound.getUUID("Player")] = PlayerAbuse.load(compound, version)
            }
            return data
        }
    }
}

/** Progress of one player. Everything goes back to 0 with "Start over" ([resetProgress]). */
class PlayerAbuse {
    /**
     * 0 = nothing; 1 to 4 = levels; 5 = the ending (AbuseTracker.LEVEL_FINAL). Never goes down
     * (except with "Start over" or the testing command).
     */
    var level = 0

    /** Whole trees felled with the dioritine axe (in survival and with the Abuse Mode active). */
    var treesFelled = 0L

    /** Trees felled since reaching the current level: they decide when it goes up and when the ending comes. */
    var treesAtLevel = 0L

    /** 3x3 strikes with the dioritine pickaxe (in survival and with the Abuse Mode active). */
    var strikes = 0L

    /** Strikes since reaching the current level: they add up with [treesAtLevel] (AbuseTracker.stepReached). */
    var strikesAtLevel = 0L

    /** Abuse actions: every tree and every strike is one. They set the pace of the signals. */
    val actions: Long get() = treesFelled + strikes

    /** Ticks played by this player (only counted while online and with the Abuse Mode active). */
    var playTicks = 0L

    /**
     * [playTicks] at which the next signal happens, or -1 if none is on its way. At levels 1 to 4
     * it is set shortly after reaching [signalDueAtActions]; at the ending, by time only.
     */
    var nextSignalAt = -1L

    /** Levels 1 to 4: the next signal is due at these [actions], or -1. */
    var signalDueAtActions = -1L

    /** Id of the last signal, so the same one never comes twice in a row. */
    var lastSignal = ""

    /** Last Morse phrase, for the same reason. */
    var lastMorsePhrase = ""

    /** Last sign word, for the same reason. */
    var lastSignWord = ""

    /** [playTicks] when THE level 4 sign appeared, or -1 if it has not appeared yet. */
    var finalSignPlacedAt = -1L

    /** [playTicks] at which the ending became due (level 4, sign placed and trees or strikes done), or -1. */
    var endingDueAt = -1L

    /** [playTicks] of phase A of the ending (phase B starts), or -1. */
    var endingStartedAt = -1L

    /** Phase B messages already sent (EndingMessages). */
    var endingMessagesSent = 0

    /** Their client has already been told the credits are due. Not saved: repeated on join. */
    var creditsAnnounced = false

    /** Phase A of the ending: for this player the axe does not fell, the pickaxe does not mine 3x3 and smelting diorite gives no crystals. */
    var dioriteUseless = false

    /**
     * What phase A of the ending took away, to give it back on "Start over". Not cleared by
     * [resetProgress]: it is emptied when given back.
     */
    val takenItems = ArrayList<ItemStack>()

    /** "Start over" happened while the player was offline: [takenItems] is given back on join. */
    var returnItemsOnJoin = false

    /** "Start over": level, counters, time and signals back to 0, and diorite is useful again. */
    fun resetProgress() {
        level = 0
        treesFelled = 0
        treesAtLevel = 0
        strikes = 0
        strikesAtLevel = 0
        playTicks = 0
        nextSignalAt = -1
        signalDueAtActions = -1
        lastSignal = ""
        lastMorsePhrase = ""
        lastSignWord = ""
        finalSignPlacedAt = -1
        endingDueAt = -1
        endingStartedAt = -1
        endingMessagesSent = 0
        creditsAnnounced = false
        dioriteUseless = false
    }

    fun save(tag: CompoundTag) {
        tag.putInt("Level", level)
        tag.putLong("TreesFelled", treesFelled)
        tag.putLong("TreesAtLevel", treesAtLevel)
        tag.putLong("Strikes", strikes)
        tag.putLong("StrikesAtLevel", strikesAtLevel)
        tag.putLong("PlayTicks", playTicks)
        tag.putLong("NextSignalAt", nextSignalAt)
        tag.putLong("SignalDueAtActions", signalDueAtActions)
        tag.putString("LastSignal", lastSignal)
        tag.putString("LastMorsePhrase", lastMorsePhrase)
        tag.putString("LastSignWord", lastSignWord)
        tag.putLong("FinalSignPlacedAt", finalSignPlacedAt)
        tag.putLong("EndingDueAt", endingDueAt)
        tag.putLong("EndingStartedAt", endingStartedAt)
        tag.putInt("EndingMessagesSent", endingMessagesSent)
        tag.putBoolean("DioriteUseless", dioriteUseless)
        tag.put("TakenItems", ListTag().apply { takenItems.forEach { add(it.save(CompoundTag())) } })
        tag.putBoolean("ReturnItemsOnJoin", returnItemsOnJoin)
    }

    companion object {
        /**
         * [version] is the DataVersion of the file. Version 1 data (level by score and days)
         * keeps its level and starts counting trees from 0 in it. Version 2 data has no strikes:
         * they start at 0 and its actions are its trees.
         */
        fun load(tag: CompoundTag, version: Int) = PlayerAbuse().apply {
            level = tag.getInt("Level").coerceIn(0, AbuseTracker.LEVEL_FINAL)
            treesFelled = tag.getLong("TreesFelled").coerceAtLeast(0)
            treesAtLevel = tag.getLong("TreesAtLevel").coerceIn(0, treesFelled)
            strikes = tag.getLong("Strikes").coerceAtLeast(0)
            strikesAtLevel = tag.getLong("StrikesAtLevel").coerceIn(0, strikes)
            playTicks = tag.getLong("PlayTicks").coerceAtLeast(0)
            // If missing (data from before the signals), -1: it is scheduled on the next tick.
            nextSignalAt = if (tag.contains("NextSignalAt")) tag.getLong("NextSignalAt").coerceAtLeast(-1) else -1
            // In version 1 the signals of levels 1 to 4 went by time: they are rescheduled by trees.
            if (version < 2 && level < AbuseTracker.LEVEL_FINAL) nextSignalAt = -1
            // Up to version 2 it was saved by trees; without strikes, trees and actions are the same.
            val dueKey = if (tag.contains("SignalDueAtActions")) "SignalDueAtActions" else "SignalDueAtTrees"
            signalDueAtActions = if (tag.contains(dueKey)) tag.getLong(dueKey).coerceAtLeast(-1) else -1
            lastSignal = tag.getString("LastSignal")
            lastMorsePhrase = tag.getString("LastMorsePhrase")
            lastSignWord = tag.getString("LastSignWord")
            finalSignPlacedAt = if (tag.contains("FinalSignPlacedAt")) tag.getLong("FinalSignPlacedAt").coerceIn(-1, playTicks) else -1
            endingDueAt = if (tag.contains("EndingDueAt")) tag.getLong("EndingDueAt").coerceIn(-1, playTicks) else -1
            endingStartedAt = if (tag.contains("EndingStartedAt")) tag.getLong("EndingStartedAt").coerceIn(-1, playTicks) else -1
            // If missing (data from before the messages), the ones already due count as sent:
            // never dump them all at once.
            endingMessagesSent = if (tag.contains("EndingMessagesSent")) {
                tag.getInt("EndingMessagesSent").coerceIn(0, EndingMessages.COUNT)
            } else {
                EndingMessages.dueFor(this)
            }
            dioriteUseless = tag.getBoolean("DioriteUseless")
            for (item in tag.getList("TakenItems", Tag.TAG_COMPOUND.toInt())) {
                // An item from a mod that is gone reads as empty: it is lost (there is nothing to give back).
                ItemStack.of(item as CompoundTag).takeUnless { it.isEmpty }?.let(takenItems::add)
            }
            returnItemsOnJoin = tag.getBoolean("ReturnItemsOnJoin")
        }
    }
}
