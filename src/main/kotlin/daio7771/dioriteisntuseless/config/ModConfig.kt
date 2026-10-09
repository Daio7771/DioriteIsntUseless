package daio7771.dioriteisntuseless.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.block.DioriteStats
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads, validates and saves config/dioriteisntuseless.json, and holds the config in effect.
 *
 * Nothing in the file can crash the game: any problem is logged and valid values are used instead.
 * File operations are synchronized because in single player the config screen (client thread) and
 * /diu reload (integrated server thread) can run at the same time.
 */
object ModConfig {

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val BROKEN_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private val path: Path = FabricLoader.getInstance().configDir.resolve("${Dioriteisntuseless.MOD_ID}.json")

    /**
     * Config in effect. Read without locks from any thread; since it is immutable and replaced as
     * a whole, every read sees a consistent state. If several values are going to be used
     * together, read the field once and use that instance.
     */
    @Volatile
    var current: DiuConfig = DiuConfig()
        private set

    /**
     * Durability the axe was registered with. It does not change until a restart, even if
     * axe.durability changes in the file.
     */
    var axeDurabilityAtStartup: Int = DiuConfig().axe.durability
        private set

    sealed interface ReloadResult {
        /**
         * @param corrections values adjusted or replaced (the details are in the log).
         * @param restartPendingDurability the new axe.durability if it differs from the one in
         *   effect, or null.
         */
        data class Success(val corrections: Int, val restartPendingDurability: Int?) : ReloadResult

        /** The JSON cannot be read. Nothing was touched: the previous values are still in use. */
        data object InvalidJson : ReloadResult

        /** Error reading the file (permissions, etc.). The previous values are still in use. */
        data object ReadError : ReloadResult
    }

    /** Call at the start of onInitialize, before the axe is registered. */
    @Synchronized
    fun loadAtStartup() {
        val config = try {
            loadOrRecover()
        } catch (e: Exception) {
            LOGGER.error("Unexpected error while loading {}; using the default values.", path, e)
            DiuConfig()
        }
        axeDurabilityAtStartup = config.axe.durability
        apply(config)
    }

    /** For /diu reload. If the file cannot be read, nothing changes. */
    @Synchronized
    fun reload(): ReloadResult = try {
        when (val file = readFile()) {
            FileState.Missing -> {
                LOGGER.info("Config file {} not found; creating it with the default values.", path)
                val config = DiuConfig()
                write(ConfigCodec.encode(config, JsonObject()))
                apply(config)
                ReloadResult.Success(0, pendingDurability(config))
            }
            is FileState.Unreadable -> {
                LOGGER.error("Could not read {}; nothing was changed and the previous values are still in use.", path, file.error)
                ReloadResult.ReadError
            }
            is FileState.Corrupt -> {
                LOGGER.error("Config file {} is not valid JSON ({}). Nothing was changed and the previous values are " +
                    "still in use; fix the file and run /diu reload again.", path, file.detail)
                ReloadResult.InvalidJson
            }
            is FileState.Parsed -> {
                val decoded = decode(file.root)
                apply(decoded.config)
                ReloadResult.Success(decoded.corrections, pendingDurability(decoded.config))
            }
        }
    } catch (e: Exception) {
        LOGGER.error("Unexpected error while reloading {}; the previous values are still in use.", path, e)
        ReloadResult.ReadError
    }

    /**
     * Saves [config] (from the config screen) and applies it. Keeps the keys that do not belong to
     * the mod. If the file was broken, it is moved aside as .broken so nothing in it is lost.
     */
    @Synchronized
    fun save(config: DiuConfig) {
        try {
            val root = when (val file = readFile()) {
                is FileState.Parsed -> file.root
                is FileState.Corrupt -> {
                    LOGGER.error("Config file {} is not valid JSON ({}); replacing it with the values from the config screen.",
                        path, file.detail)
                    // If it cannot be moved aside, it is not overwritten: the changes only last this session.
                    if (moveAsideBroken()) JsonObject() else null
                }
                else -> JsonObject()
            }
            if (root != null) write(ConfigCodec.encode(config, root))
        } catch (e: Exception) {
            LOGGER.error("Unexpected error while saving {}.", path, e)
        }
        apply(config)
    }

    private fun apply(config: DiuConfig) {
        current = config
        DioriteStats.applyLocal(config.diorite)
    }

    private fun pendingDurability(config: DiuConfig): Int? =
        config.axe.durability.takeIf { it != axeDurabilityAtStartup }

    private fun loadOrRecover(): DiuConfig = when (val file = readFile()) {
        FileState.Missing -> {
            LOGGER.info("Config file {} not found; creating it with the default values.", path)
            DiuConfig().also { write(ConfigCodec.encode(it, JsonObject())) }
        }
        is FileState.Unreadable -> {
            // Not overwritten: the file may be fine and the problem may be permissions.
            LOGGER.error("Could not read {}; using the default values. The file was left untouched.", path, file.error)
            DiuConfig()
        }
        is FileState.Corrupt -> {
            LOGGER.error("Config file {} is not valid JSON ({}).", path, file.detail)
            if (moveAsideBroken()) {
                write(ConfigCodec.encode(DiuConfig(), JsonObject()))
                LOGGER.error("A new {} with the default values has been created.", path.fileName)
            } else {
                LOGGER.error("Using the default values; the broken file was left in place.")
            }
            DiuConfig()
        }
        is FileState.Parsed -> decode(file.root).config
    }

    /** Validates the tree and, if keys were missing, rewrites the file with them. */
    private fun decode(root: JsonObject): ConfigCodec.Decoded {
        val decoded = ConfigCodec.decode(root)
        if (decoded.corrections > 0) {
            LOGGER.warn("Config: {} invalid value(s) corrected; see the warnings above.", decoded.corrections)
        }
        if (decoded.treeChanged) write(root)
        return decoded
    }

    private sealed interface FileState {
        data object Missing : FileState
        class Parsed(val root: JsonObject) : FileState
        class Corrupt(val detail: String) : FileState
        class Unreadable(val error: IOException) : FileState
    }

    private fun readFile(): FileState {
        if (Files.notExists(path)) return FileState.Missing
        val text = try {
            Files.readString(path)
        } catch (e: CharacterCodingException) {
            return FileState.Corrupt("not valid UTF-8 text")
        } catch (e: IOException) {
            return FileState.Unreadable(e)
        }
        val root = try {
            // Gson's lenient mode: accepts comments and unquoted keys.
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return FileState.Corrupt(rootMessage(e))
        }
        if (!root.isJsonObject) return FileState.Corrupt("the file must contain a JSON object { ... }")
        return FileState.Parsed(root.asJsonObject)
    }

    /** Gson's useful message ("Unterminated object at line 5 column 3 path $.diorite"). */
    private fun rootMessage(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        // That advice is for programmers; the player only needs to know where the error is.
        return (root.message ?: root.toString())
            .replace("Use JsonReader.setLenient(true) to accept malformed JSON", "Malformed JSON")
    }

    /**
     * Renames the broken file to dioriteisntuseless.json.broken-<date> so it is not lost.
     * Returns false if that failed (and then it must not be overwritten).
     */
    private fun moveAsideBroken(): Boolean {
        val base = "${path.fileName}.broken-${LocalDateTime.now().format(BROKEN_SUFFIX_FORMAT)}"
        var target = path.resolveSibling(base)
        var n = 2
        while (Files.exists(target)) target = path.resolveSibling("$base-${n++}")
        return try {
            Files.move(path, target)
            LOGGER.error("The broken config file has been renamed to {}.", target.fileName)
            true
        } catch (e: IOException) {
            LOGGER.error("Could not rename the broken config file {}.", path, e)
            false
        }
    }

    /**
     * Writes a temporary file first and then moves it into place, so closing the game halfway
     * through never leaves a half-written JSON. If it fails, it is only logged: the config in
     * memory is still valid.
     */
    private fun write(root: JsonObject) {
        var temp: Path? = null
        try {
            Files.createDirectories(path.parent)
            temp = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
            val bytes = ByteBuffer.wrap((GSON.toJson(root) + "\n").toByteArray(Charsets.UTF_8))
            FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
            temp = null
        } catch (e: IOException) {
            LOGGER.error("Could not write {}; the values in memory are still in use.", path, e)
        } finally {
            if (temp != null) runCatching { Files.deleteIfExists(temp) }
        }
    }
}
