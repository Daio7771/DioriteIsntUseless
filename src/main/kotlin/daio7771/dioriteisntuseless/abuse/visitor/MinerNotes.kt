package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.worldgen.MineLocator
import net.minecraft.ChatFormatting
import net.minecraft.locale.Language
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.WrittenBookItem
import net.minecraft.world.phys.Vec3

/**
 * The notes The Miner hands over at the key moments (written books, "Note from The Miner"):
 *
 * - [Note.WARNING]: with the first warning he cannot finish (TheMinerEntity: at levels 2-3 his
 *   first greeting is always one of those, so it comes at level 2 if they talk to him there).
 * - [Note.LEVEL_3]: the first time they talk to him at level 3, after everything he says.
 * - [Note.FAREWELL]: at the end of his goodbye, at level 4.
 * - [Note.INSTRUCTIONS]: his instructions book, tossed at their feet on his first visit, when they
 *   come near ([throwTo]; TheMinerEntity). It has a title of its own.
 * - [Note.MINE]: the first time he pays a job, where the nearest abandoned mine is (MineLocator):
 *   its shaft, and how deep its corridors are. If there is none near, it waits for another time.
 *
 * Each one once per cycle (MinerState.notesGiven): "Start over" makes them come again, but never
 * takes the ones they have. They are collectibles, the one thing it leaves in their hands. Into
 * their inventory or, if it is full, at his feet (an item that never despawns). A quiet line above
 * the hotbar tells them.
 *
 * Texts in en_us.json: book.dioriteisntuseless.miner_note.<note>.<page> (page count in [Note]),
 * .title and .author (these two resolved when the book is made: a book keeps them as plain text).
 */
object MinerNotes {

    /** The order matters: each one's bit (its ordinal) is saved in MinerState.notesGiven. New ones go at the end. */
    enum class Note(val id: String, val pages: Int, val ownTitle: Boolean = false) {
        WARNING("warning", 2),
        LEVEL_3("level3", 1),
        FAREWELL("farewell", 2),
        INSTRUCTIONS("instructions", 3, ownTitle = true),
        MINE("mine", 2);

        val bit: Int get() = 1 shl ordinal
    }

    /** The abuse level of [Note.LEVEL_3]. */
    const val LEVEL_3_NOTE_LEVEL = 3

    /** "Tattered" in its tooltip (vanilla's generation 3): old paper, and it cannot be copied. */
    private const val TATTERED = 3

    /** A written book's title can be 32 characters at most (WrittenBookItem). */
    private const val MAX_TITLE = 32

    /** In the book's tag: which note it is, for anything that needs to tell them apart. */
    private const val NOTE_TAG = "${Dioriteisntuseless.MOD_ID}:miner_note"

    private const val LANG = "${Dioriteisntuseless.MOD_ID}.miner_note"

    fun given(miner: MinerState, note: Note): Boolean = miner.notesGiven and note.bit != 0

    /**
     * Hands [note] to [player], from [from] (The Miner): into their inventory or, if it does not
     * fit, on the ground at his feet. Marks it as given in [miner]. Does nothing if it already was.
     */
    fun give(player: ServerPlayer, from: Entity, miner: MinerState, note: Note) {
        if (given(miner, note)) return
        // The mine's note says where it is: X and Z of the way in, then the depth of its corridors.
        val args: Array<Any> = if (note == Note.MINE) {
            val mine = MineLocator.nearest(player.serverLevel(), player.blockPosition()) ?: return
            arrayOf(mine.wayIn.x, mine.wayIn.z, mine.floorY)
        } else emptyArray()
        miner.notesGiven = miner.notesGiven or note.bit
        val inPocket = handTo(player, from, create(note, args))
        player.displayClientMessage(
            Component.translatable("chat.${Dioriteisntuseless.MOD_ID}.miner.note_${if (inPocket) "given" else "dropped"}")
                .withStyle(ChatFormatting.GRAY),
            true,
        )
    }

    /**
     * Puts [stack] (something of his) into [player]'s inventory or, if it does not fit, on the
     * ground at the feet of [from] (an item that never despawns). Returns whether it fit.
     */
    fun handTo(player: ServerPlayer, from: Entity, stack: ItemStack): Boolean {
        val inPocket = player.inventory.add(stack)
        if (!inPocket) {
            val level = player.serverLevel()
            val item = ItemEntity(level, from.x, from.y + 0.1, from.z, stack, 0.0, 0.0, 0.0)
            item.setDefaultPickUpDelay()
            item.setUnlimitedLifetime()
            level.addFreshEntity(item)
        }
        player.containerMenu.broadcastChanges()
        return inPocket
    }

    /**
     * Tosses [note] at [player]'s feet from [from] (The Miner), the way a villager tosses a gift:
     * from about his chest, just hard enough to land half a block short of them (2-6 blocks away).
     * An item anyone can see, that never despawns. Marks it as given in [miner].
     */
    fun throwTo(player: ServerPlayer, from: LivingEntity, miner: MinerState, note: Note) {
        if (given(miner, note)) return
        miner.notesGiven = miner.notesGiven or note.bit
        toss(player, from, create(note))
    }

    /** Tosses [stack] at [player]'s feet from [from], like [throwTo]. */
    fun toss(player: ServerPlayer, from: LivingEntity, stack: ItemStack) {
        val level = player.serverLevel()
        val item = ItemEntity(level, from.x, from.eyeY - THROW_DROP, from.z, stack)
        var towards = Vec3(player.x - from.x, 0.0, player.z - from.z)
        if (towards.lengthSqr() < 1.0E-4) towards = Vec3.directionFromRotation(0f, from.yRot)
        val distance = towards.length().coerceIn(MIN_THROW, MAX_THROW)
        val push = towards.normalize().scale(THROW_SPEED_PER_BLOCK * distance - THROW_SPEED_OFFSET)
        item.setDeltaMovement(push.x, THROW_LIFT, push.z)
        item.setThrower(from.uuid)
        item.setDefaultPickUpDelay()
        item.setUnlimitedLifetime()
        level.addFreshEntity(item)
    }

    /** Worked out from ItemEntity's gravity and friction: lands about half a block short of the target. */
    private const val THROW_DROP = 0.3
    private const val THROW_LIFT = 0.2
    private const val THROW_SPEED_PER_BLOCK = 0.075
    private const val THROW_SPEED_OFFSET = 0.045
    private const val MIN_THROW = 2.0
    private const val MAX_THROW = 6.0

    /**
     * The book of [note]. Its pages stay translatable: they are shown in the reader's language.
     * Every page gets all of [args] (a page picks its own with %1${'$'}s, %2${'$'}s...).
     */
    fun create(note: Note, args: Array<Any> = emptyArray()): ItemStack = ItemStack(Items.WRITTEN_BOOK).apply {
        val tag = orCreateTag
        val title = if (note.ownTitle) text("book.$LANG.${note.id}.title", "The Miner's Instructions") else text("book.$LANG.title", "Note from The Miner")
        tag.putString(WrittenBookItem.TAG_TITLE, title.take(MAX_TITLE))
        tag.putString(WrittenBookItem.TAG_AUTHOR, text("book.$LANG.author", "The Miner"))
        tag.putInt(WrittenBookItem.TAG_GENERATION, TATTERED)
        tag.put(WrittenBookItem.TAG_PAGES, ListTag().apply {
            for (page in 1..note.pages) {
                add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.$LANG.${note.id}.$page", *args))))
            }
        })
        tag.putBoolean(WrittenBookItem.TAG_RESOLVED, true)
        tag.putString(NOTE_TAG, note.id)
    }

    /** From en_us.json (Fabric loads the mod's English on a dedicated server too); [fallback] just in case. */
    private fun text(key: String, fallback: String): String = Language.getInstance().getOrDefault(key, fallback)
}
