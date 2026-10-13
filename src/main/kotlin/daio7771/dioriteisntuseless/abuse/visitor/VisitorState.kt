package daio7771.dioriteisntuseless.abuse.visitor

import net.minecraft.nbt.CompoundTag

/**
 * What The Miner remembers of one player. It lives with the rest of their Abuse Mode state
 * (PlayerAbuse), never in the entity: The Miner is never saved with the world.
 */
class MinerState {
    /** His first appearance (the first Diorite Crystals out of a furnace) has happened. */
    var met = false

    /** PlayerAbuse.playTicks at which he comes back, or -1 if no visit is on its way. */
    var nextVisitAt = -1L

    /** He said goodbye at level 4: he does not come back until "Start over". */
    var gone = false

    /** His last random line (MinerSpeech), so the next one is a different one. */
    var lastLine = ""

    /** The job he gave them (MinerQuests.Type id), or "" if none. */
    var questType = ""

    /** The last kind of job, so the next one is a different kind. */
    var lastQuestType = ""

    /** The notes he has handed over in this cycle (MinerNotes.Note.bit). */
    var notesGiven = 0

    /** How many trees, strikes or crystals the job asks for. */
    var questAmount = 0

    /** Value of the job's counter (trees felled or strikes) when it was given. */
    var questStart = 0L

    /** It pays [questRewardCount] iron ingots (true) or emeralds (false). */
    var questRewardIron = false
    var questRewardCount = 0

    fun clearQuest() {
        questType = ""
        questAmount = 0
        questStart = 0
        questRewardIron = false
        questRewardCount = 0
    }

    /** "Start over": as if they had never met. */
    fun reset() {
        met = false
        nextVisitAt = -1
        gone = false
        clearQuest()
        lastQuestType = ""
        lastLine = ""
        notesGiven = 0
    }

    fun save(): CompoundTag = CompoundTag().apply {
        putBoolean("Met", met)
        putLong("NextVisitAt", nextVisitAt)
        putBoolean("Gone", gone)
        putString("LastLine", lastLine)
        putString("QuestType", questType)
        putString("LastQuestType", lastQuestType)
        putInt("QuestAmount", questAmount)
        putLong("QuestStart", questStart)
        putBoolean("QuestRewardIron", questRewardIron)
        putInt("QuestRewardCount", questRewardCount)
        putInt("NotesGiven", notesGiven)
    }

    fun load(tag: CompoundTag) {
        met = tag.getBoolean("Met")
        nextVisitAt = if (tag.contains("NextVisitAt")) tag.getLong("NextVisitAt").coerceAtLeast(-1) else -1
        gone = tag.getBoolean("Gone")
        lastLine = tag.getString("LastLine")
        questType = tag.getString("QuestType")
        lastQuestType = tag.getString("LastQuestType")
        questAmount = tag.getInt("QuestAmount").coerceAtLeast(0)
        questStart = tag.getLong("QuestStart").coerceAtLeast(0)
        questRewardIron = tag.getBoolean("QuestRewardIron")
        questRewardCount = tag.getInt("QuestRewardCount").coerceIn(0, MAX_REWARD)
        notesGiven = tag.getInt("NotesGiven")
        // A job that makes no sense (an unknown kind, edited data) is dropped: he gives a new one.
        if (MinerQuests.Type.byId(questType) == null || questAmount <= 0) clearQuest()
    }
}

/** More than any job pays: anything above it comes from edited data. */
private const val MAX_REWARD = 64

/** What The "Useful" Owner has already done to one player, and what brings it. */
class OwnerState {
    /** Its only appearance as a statue (in this cycle) has happened. */
    var statueShown = false

    /** Dioritine Axes and Pickaxes worn out until they broke, with the Abuse Mode active (OwnerStatue). */
    var axesWornOut = 0
    var pickaxesWornOut = 0

    /** "Start over": everything back to 0, so the statue can come once more in the new cycle. */
    fun reset() {
        statueShown = false
        axesWornOut = 0
        pickaxesWornOut = 0
    }

    fun save(): CompoundTag = CompoundTag().apply {
        putBoolean("StatueShown", statueShown)
        putInt("AxesWornOut", axesWornOut)
        putInt("PickaxesWornOut", pickaxesWornOut)
    }

    fun load(tag: CompoundTag) {
        statueShown = tag.getBoolean("StatueShown")
        axesWornOut = tag.getInt("AxesWornOut").coerceAtLeast(0)
        pickaxesWornOut = tag.getInt("PickaxesWornOut").coerceAtLeast(0)
    }
}
