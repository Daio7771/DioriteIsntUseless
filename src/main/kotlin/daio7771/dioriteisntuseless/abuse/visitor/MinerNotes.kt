package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.ChatFormatting
import net.minecraft.locale.Language
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.WrittenBookItem

/**
 * The notes The Miner hands over at the key moments (written books, "Note from The Miner"):
 *
 * - [Note.WARNING]: with the first warning he cannot finish (TheMinerEntity: at levels 2-3 his
 *   first greeting is always one of those, so it comes at level 2 if they talk to him there).
 * - [Note.LEVEL_3]: the first time they talk to him at level 3, after everything he says.
 * - [Note.FAREWELL]: at the end of his goodbye, at level 4.
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

    enum class Note(val id: String, val pages: Int) {
        WARNING("warning", 2),
        LEVEL_3("level3", 1),
        FAREWELL("farewell", 2);

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
        miner.notesGiven = miner.notesGiven or note.bit
        val book = create(note)
        val inPocket = player.inventory.add(book)
        if (!inPocket) {
            val level = player.serverLevel()
            val item = ItemEntity(level, from.x, from.y + 0.1, from.z, book, 0.0, 0.0, 0.0)
            item.setDefaultPickUpDelay()
            item.setUnlimitedLifetime()
            level.addFreshEntity(item)
        }
        player.containerMenu.broadcastChanges()
        player.displayClientMessage(
            Component.translatable("chat.${Dioriteisntuseless.MOD_ID}.miner.note_${if (inPocket) "given" else "dropped"}")
                .withStyle(ChatFormatting.GRAY),
            true,
        )
    }

    /** The book of [note]. Its pages stay translatable: they are shown in the reader's language. */
    fun create(note: Note): ItemStack = ItemStack(Items.WRITTEN_BOOK).apply {
        val tag = orCreateTag
        tag.putString(WrittenBookItem.TAG_TITLE, text("book.$LANG.title", "Note from The Miner").take(MAX_TITLE))
        tag.putString(WrittenBookItem.TAG_AUTHOR, text("book.$LANG.author", "The Miner"))
        tag.putInt(WrittenBookItem.TAG_GENERATION, TATTERED)
        tag.put(WrittenBookItem.TAG_PAGES, ListTag().apply {
            for (page in 1..note.pages) {
                add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("book.$LANG.${note.id}.$page"))))
            }
        })
        tag.putBoolean(WrittenBookItem.TAG_RESOLVED, true)
        tag.putString(NOTE_TAG, note.id)
    }

    /** From en_us.json (Fabric loads the mod's English on a dedicated server too); [fallback] just in case. */
    private fun text(key: String, fallback: String): String = Language.getInstance().getOrDefault(key, fallback)
}
