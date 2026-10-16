package daio7771.dioriteisntuseless.worldgen

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder

/**
 * How one abandoned mine is laid out, on a grid of [GRID]x[GRID] cells of [CELL] blocks:
 *
 * - The crib room (CribRoomPiece) takes the 2x2 cells of one corner: the end of the maze.
 * - The way there starts at the opposite corner, behind the entrance (a cell outside the grid,
 *   with beams and broken rails), and winds through the grid to a cell next to the room.
 * - Behind the entrance, a shaft with a ladder goes up to the surface (MineShaftPiece). Where it
 *   can't (water up there, or too little ground), there is no mine at all: one nobody can find
 *   from above is no use, and The Miner would send them to a river.
 * - False forks (about one for every 4 cells of the way) leave it, or leave other forks, and end
 *   in dead ends, which may have crumbs (some diorite, a torch, an old pickaxe).
 * - 2 small spawners (a zombie one and a skeleton one), in the forks or along the way, never in
 *   the entrance nor in the last cells before the room.
 * - 2-4 notes on signs (MineNote): one at the entrance, one near the crib room, maybe one halfway
 *   and one more in a dead end. Never two in the same cell.
 * - Cells nobody walks through stay solid stone.
 *
 * Every corridor cell is a MineCellPiece; its shape (straight, corner, fork, dead end) comes from
 * its openings. It is worked out on an abstract grid and then turned and mirrored at random, so
 * the room can be in any corner.
 */
object MineLayout {

    const val CELL = 5
    const val GRID = 13
    const val SIZE = CELL * GRID

    /** Floor, corridors 3 high, roof; the crib room is one block taller. */
    const val CELL_HEIGHT = 5
    const val HEIGHT = CELL_HEIGHT + 1

    private const val ROOM_START = GRID - 2

    /** Way lengths (cells) accepted at once; otherwise the shortest of [WAY_ATTEMPTS]. */
    private const val MIN_WAY = 30
    private const val MAX_WAY = 60
    private const val WAY_ATTEMPTS = 40

    private const val MIN_BRANCH = 2
    private const val MAX_BRANCH = 6
    private const val WAY_CELLS_PER_FORK = 4
    private const val CRUMB_CHANCE = 0.7f
    private const val BEAM_CHANCE = 0.5f

    /** The first cells of the way also have broken rails, like the entrance. */
    private const val RAIL_CELLS = 3

    private data class Cell(val i: Int, val j: Int) {
        fun neighbours() = listOf(Cell(i + 1, j), Cell(i - 1, j), Cell(i, j + 1), Cell(i, j - 1))
        fun inGrid() = i in 0 until GRID && j in 0 until GRID
        fun inRoom() = i >= ROOM_START && j >= ROOM_START
    }

    /** Where the ground is at a column: the y of the first free block above it, or null if there is water on top. */
    fun interface Ground {
        fun surfaceAt(x: Int, z: Int): Int?
    }

    fun addPieces(builder: StructurePiecesBuilder, origin: BlockPos, random: RandomSource, ground: Ground) {
        val transform = Transform(random.nextBoolean(), random.nextBoolean(), random.nextBoolean())
        val way = findWay(random)
        val used = way.toMutableSet()
        val links = HashSet<Pair<Cell, Cell>>()
        fun link(a: Cell, b: Cell) {
            links += a to b
            links += b to a
        }
        way.zipWithNext().forEach { (a, b) -> link(a, b) }
        val entrance = Cell(-1, 0)
        link(entrance, way.first())
        val shaft = Cell(-2, 0)
        val shaftCorner = transform.cellCorner(origin, shaft)
        val surface = ground.surfaceAt(shaftCorner.x + CELL / 2, shaftCorner.z + CELL / 2)
            ?.takeIf { it > origin.y + HEIGHT + MineShaftPiece.MIN_LENGTH }
            ?: return // No pieces: the structure start is invalid and nothing is built.
        link(shaft, entrance)
        builder.addPiece(MineShaftPiece(shaftCorner, transform.direction(shaft, entrance), surface))
        val roomDoor = way.last().neighbours().first { it.inGrid() && it.inRoom() }
        link(way.last(), roomDoor)

        // False forks: from the way (not its first or last cell) into cells nobody uses.
        val forkCells = ArrayList<Cell>()
        repeat(way.size / WAY_CELLS_PER_FORK + random.nextInt(3)) {
            val starts = (way.subList(1, way.size - 1) + forkCells).filter { cell -> cell.neighbours().any { it.free(used) } }
            if (starts.isEmpty()) return@repeat
            var at = starts[random.nextInt(starts.size)]
            val length = MIN_BRANCH + random.nextInt(MAX_BRANCH - MIN_BRANCH + 1)
            for (step in 0 until length) {
                val options = at.neighbours().filter { it.free(used) }
                if (options.isEmpty()) break
                val next = options[random.nextInt(options.size)]
                used += next
                forkCells += next
                link(at, next)
                at = next
            }
        }
        // A fork that another fork left from is not a dead end any more: only those with one way in.
        val deadEnds = forkCells.filter { cell -> cell.neighbours().count { (cell to it) in links } == 1 }

        // Spawners: in the forks first, otherwise along the middle of the way.
        val spawnerCells = (forkCells.shuffled(random) + way.subList(2, (way.size - 2).coerceAtLeast(2)).shuffled(random))
            .distinct().take(2)

        val notes = placeNotes(random, entrance, way, deadEnds) { cell -> cell.neighbours().count { (cell to it) in links } < 4 }

        val cells = listOf(entrance) + used.sortedWith(compareBy({ it.i }, { it.j }))
        for (cell in cells) {
            val openings = cell.neighbours().filter { (cell to it) in links }.map { transform.direction(cell, it) }
            val straight = openings.size == 2 && openings[0] == openings[1].opposite
            val wayIndex = way.indexOf(cell)
            builder.addPiece(
                MineCellPiece(
                    transform.cellCorner(origin, cell),
                    openings = openings.toSet(),
                    entrance = cell == entrance,
                    beam = cell == entrance || (straight && random.nextFloat() < BEAM_CHANCE),
                    rails = cell == entrance || (straight && wayIndex in 0 until RAIL_CELLS),
                    crumb = if (cell in deadEnds && random.nextFloat() < CRUMB_CHANCE) {
                        MineCellPiece.Crumb.entries[1 + random.nextInt(MineCellPiece.Crumb.entries.size - 1)]
                    } else MineCellPiece.Crumb.NONE,
                    spawner = when (spawnerCells.indexOf(cell)) {
                        0 -> MineCellPiece.Spawner.ZOMBIE
                        1 -> MineCellPiece.Spawner.SKELETON
                        else -> MineCellPiece.Spawner.NONE
                    },
                    note = notes[cell],
                )
            )
        }

        val roomCells = (ROOM_START until GRID).flatMap { i -> (ROOM_START until GRID).map { j -> Cell(i, j) } }
        val roomCorner = roomCells.map { transform.cellCorner(origin, it) }
            .reduce { a, b -> BlockPos(minOf(a.x, b.x), a.y, minOf(a.z, b.z)) }
        val doorSide = transform.direction(roomDoor, way.last())
        val doorCorner = transform.cellCorner(origin, roomDoor)
        // Where the door is along its wall: the middle of the door cell.
        val doorAlong = if (doorSide.axis == Direction.Axis.X) doorCorner.z - roomCorner.z else doorCorner.x - roomCorner.x
        builder.addPiece(CribRoomPiece(roomCorner, doorSide, doorAlong + CELL / 2))
    }

    /**
     * Which cells get a note: one from the entrance, one near the crib room (its last 3 cells of
     * the way), and up to 2 more (one halfway, one in a dead end). Only cells with a wall to hang
     * it on ([hasWall]), each note at most once.
     */
    private fun placeNotes(
        random: RandomSource, entrance: Cell, way: List<Cell>, deadEnds: List<Cell>, hasWall: (Cell) -> Boolean,
    ): Map<Cell, MineNote> {
        val notes = HashMap<Cell, MineNote>()
        val left = MineNote.entries.toMutableList()
        fun place(zone: MineNote.Zone, candidates: List<Cell>) {
            val cell = candidates.filter { it !in notes && hasWall(it) }.shuffled(random).firstOrNull() ?: return
            val options = left.filter { it.zone == zone }
            if (options.isEmpty()) return
            val note = options[random.nextInt(options.size)]
            left -= note
            notes[cell] = note
        }
        val third = way.size / 3
        place(MineNote.Zone.ENTRANCE, listOf(entrance))
        place(MineNote.Zone.CENTER, way.takeLast(3))
        val extra = random.nextInt(3)
        if (extra >= 1) place(MineNote.Zone.MIDDLE, way.subList(third, way.size - third))
        if (extra >= 2) place(MineNote.Zone.ENTRANCE, deadEnds)
        return notes
    }

    private fun Cell.free(used: Set<Cell>) = inGrid() && !inRoom() && this !in used

    /** From the corner opposite the room to a cell next to it, never through the room. */
    private fun findWay(random: RandomSource): List<Cell> {
        var best: List<Cell>? = null
        repeat(WAY_ATTEMPTS) {
            val way = search(Cell(0, 0), ArrayList(), HashSet(), random) ?: return@repeat
            if (way.size in MIN_WAY..MAX_WAY) return way
            if (best == null || way.size < best!!.size) best = way
        }
        // There is always one: the grid has no walls of its own.
        return best ?: search(Cell(0, 0), ArrayList(), HashSet(), random)!!
    }

    /** A random depth-first walk that ends at the first cell next to the room. */
    private fun search(at: Cell, way: MutableList<Cell>, seen: MutableSet<Cell>, random: RandomSource): List<Cell>? {
        way += at
        seen += at
        if (at.neighbours().any { it.inGrid() && it.inRoom() }) return way
        for (next in at.neighbours().shuffled(random)) {
            if (!next.inGrid() || next.inRoom() || next in seen) continue
            search(next, way, seen, random)?.let { return it }
        }
        way.removeAt(way.size - 1)
        return null
    }

    private fun <T> List<T>.shuffled(random: RandomSource): List<T> {
        val list = toMutableList()
        for (i in list.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            list[i] = list[j].also { list[j] = list[i] }
        }
        return list
    }

    /** From the abstract grid to the world: swapping the axes and mirroring them. */
    private class Transform(val swap: Boolean, val mirrorX: Boolean, val mirrorZ: Boolean) {

        private fun world(cell: Cell): Pair<Int, Int> {
            var x = if (swap) cell.j else cell.i
            var z = if (swap) cell.i else cell.j
            if (mirrorX) x = GRID - 1 - x
            if (mirrorZ) z = GRID - 1 - z
            return x to z
        }

        fun cellCorner(origin: BlockPos, cell: Cell): BlockPos {
            val (x, z) = world(cell)
            return origin.offset(x * CELL, 0, z * CELL)
        }

        /** The world direction from [from] to its neighbour [to]. */
        fun direction(from: Cell, to: Cell): Direction {
            val (fx, fz) = world(from)
            val (tx, tz) = world(to)
            return when {
                tx > fx -> Direction.EAST
                tx < fx -> Direction.WEST
                tz > fz -> Direction.SOUTH
                else -> Direction.NORTH
            }
        }
    }
}
