package daio7771.dioriteisntuseless.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import java.math.BigDecimal

/**
 * Convierte entre el árbol JSON del archivo y DiuConfig.
 *
 * Trabaja sobre el árbol en vez de sobre una clase para poder tocar solo lo necesario: las
 * claves desconocidas se quedan donde están y los valores incorrectos no se reescriben, solo se
 * avisa en el log y se usa otro valor.
 */
internal object ConfigCodec {

    /** Sube cuando cambie el formato del archivo y haya que migrar los de versiones anteriores. */
    const val CURRENT_VERSION = 2

    private const val VERSION_KEY = "configVersion"

    class Decoded(
        val config: DiuConfig,
        /** Valores ajustados o sustituidos (fuera de rango, tipo incorrecto, valor desconocido). */
        val corrections: Int,
        /** Se han añadido claves que faltaban al árbol y hay que reescribir el archivo. */
        val treeChanged: Boolean,
    )

    /** Lee [root] (y le añade las claves que falten, con su valor por defecto). */
    fun decode(root: JsonObject): Decoded {
        val reader = Reader(root)
        reader.checkVersion()
        val defaults = DiuConfig()

        val diorite = reader.section("diorite")
        val treeFelling = reader.section("treeFelling")
        val axe = reader.section("axe")
        val abuseMode = reader.section("abuseMode")
        val client = reader.section("client")
        val config = DiuConfig(
            diorite = DiuConfig.Diorite(
                enabled = reader.boolean(diorite, "enabled", defaults.diorite.enabled),
                hardness = reader.float(diorite, "hardness", defaults.diorite.hardness, DiuConfig.Limits.HARDNESS),
                blastResistance = reader.float(
                    diorite, "blastResistance", defaults.diorite.blastResistance, DiuConfig.Limits.BLAST_RESISTANCE,
                ),
            ),
            treeFelling = DiuConfig.TreeFelling(
                enabled = reader.boolean(treeFelling, "enabled", defaults.treeFelling.enabled),
                maxLogs = reader.int(treeFelling, "maxLogs", defaults.treeFelling.maxLogs, DiuConfig.Limits.MAX_LOGS),
                logsPerDurabilityPoint = reader.int(
                    treeFelling, "logsPerDurabilityPoint", defaults.treeFelling.logsPerDurabilityPoint,
                    DiuConfig.Limits.LOGS_PER_DURABILITY_POINT,
                ),
                dropsAtOrigin = reader.boolean(treeFelling, "dropsAtOrigin", defaults.treeFelling.dropsAtOrigin),
                sneakMode = reader.enum(treeFelling, "sneakMode", defaults.treeFelling.sneakMode, SneakMode.entries),
                treesBeforeBreaking = reader.int(
                    treeFelling, "treesBeforeBreaking", defaults.treeFelling.treesBeforeBreaking,
                    DiuConfig.Limits.TREES_BEFORE_BREAKING,
                ),
            ),
            axe = DiuConfig.Axe(
                durability = reader.int(axe, "durability", defaults.axe.durability, DiuConfig.Limits.AXE_DURABILITY),
                restrictEnchantments = reader.boolean(axe, "restrictEnchantments", defaults.axe.restrictEnchantments),
            ),
            abuseMode = DiuConfig.AbuseMode(
                enabled = reader.boolean(abuseMode, "enabled", defaults.abuseMode.enabled),
                treesPerLevel = reader.intList(
                    abuseMode, "treesPerLevel", defaults.abuseMode.treesPerLevel, DiuConfig.Limits.ABUSE_TREES,
                ),
                treesUntilEnding = reader.int(
                    abuseMode, "treesUntilEnding", defaults.abuseMode.treesUntilEnding, DiuConfig.Limits.ABUSE_TREES,
                ),
            ),
            client = DiuConfig.Client(
                warningShown = reader.boolean(client, "warningShown", defaults.client.warningShown),
            ),
        )
        return Decoded(config, reader.corrections, reader.treeChanged)
    }

    /** Escribe [config] en [root], conservando las claves que no son del mod. */
    fun encode(config: DiuConfig, root: JsonObject): JsonObject {
        root.addProperty(VERSION_KEY, CURRENT_VERSION)
        section(root, "diorite").apply {
            addProperty("enabled", config.diorite.enabled)
            addProperty("hardness", config.diorite.hardness)
            addProperty("blastResistance", config.diorite.blastResistance)
        }
        section(root, "treeFelling").apply {
            addProperty("enabled", config.treeFelling.enabled)
            addProperty("maxLogs", config.treeFelling.maxLogs)
            addProperty("logsPerDurabilityPoint", config.treeFelling.logsPerDurabilityPoint)
            addProperty("dropsAtOrigin", config.treeFelling.dropsAtOrigin)
            addProperty("sneakMode", config.treeFelling.sneakMode.name)
            addProperty("treesBeforeBreaking", config.treeFelling.treesBeforeBreaking)
        }
        section(root, "axe").apply {
            addProperty("durability", config.axe.durability)
            addProperty("restrictEnchantments", config.axe.restrictEnchantments)
        }
        section(root, "abuseMode").apply {
            addProperty("enabled", config.abuseMode.enabled)
            add("treesPerLevel", intArray(config.abuseMode.treesPerLevel))
            addProperty("treesUntilEnding", config.abuseMode.treesUntilEnding)
        }
        section(root, "client").apply {
            addProperty("warningShown", config.client.warningShown)
        }
        return root
    }

    private fun intArray(values: List<Int>): JsonArray = JsonArray().apply { values.forEach(::add) }

    /** La sección [name] de [root]; si falta o no es un objeto, la crea (en el mismo sitio). */
    private fun section(root: JsonObject, name: String): JsonObject =
        root.get(name) as? JsonObject ?: JsonObject().also { root.add(name, it) }

    private class Reader(private val root: JsonObject) {
        var corrections = 0
            private set
        var treeChanged = false
            private set

        fun checkVersion() {
            val element = root.get(VERSION_KEY)
            if (element == null) {
                // Va la primera, como en un archivo nuevo: se saca todo y se vuelve a meter detrás.
                val entries = root.entrySet().map { it.key to it.value }
                entries.forEach { root.remove(it.first) }
                root.addProperty(VERSION_KEY, CURRENT_VERSION)
                entries.forEach { root.add(it.first, it.second) }
                treeChanged = true
                return
            }
            val version = element.asNumberOrNull()
            when {
                version == null || !version.isIntegral() || version < BigDecimal.ONE -> {
                    LOGGER.warn("Config: {} = {} is not a valid version number; reading the file as version {}.",
                        VERSION_KEY, element, CURRENT_VERSION)
                    corrections++
                }
                version > BigDecimal(CURRENT_VERSION) -> LOGGER.warn(
                    "Config: the file is from a newer version of the mod ({} = {}, this version understands {}). " +
                        "Reading the options this version knows; the rest are left untouched.",
                    VERSION_KEY, element, CURRENT_VERSION,
                )
                version < BigDecimal(CURRENT_VERSION) -> migrate(version.intValueExact())
            }
        }

        /** Convierte un archivo de una versión anterior, paso a paso, hasta [CURRENT_VERSION]. */
        private fun migrate(from: Int) {
            if (from < 2) migrateToVersion2()
            root.addProperty(VERSION_KEY, CURRENT_VERSION)
            treeChanged = true
        }

        /**
         * Versión 2: el Abuse Mode avanza por árboles talados. La puntuación y los días ya no
         * existen y sus opciones se quitan. El hacha pasa a aguantar 17 árboles en vez de 7, salvo
         * que se hubiera cambiado ese valor.
         */
        private fun migrateToVersion2() {
            val abuseMode = root.get("abuseMode") as? JsonObject
            val removed = listOf("levelThresholds", "minDaysBetweenLevels", "daysUntilEnding")
                .filter { abuseMode?.remove(it) != null }
            if (removed.isNotEmpty()) {
                LOGGER.info("Config: removed abuseMode.{}; the abuse mode now advances by trees felled (abuseMode.treesPerLevel).",
                    removed.joinToString(", abuseMode."))
            }
            val treeFelling = root.get("treeFelling") as? JsonObject ?: return
            val old = DiuConfig.TreeFelling.OLD_DEFAULT_TREES_BEFORE_BREAKING
            if (treeFelling.get("treesBeforeBreaking")?.asNumberOrNull()?.compareTo(BigDecimal(old)) == 0) {
                val new = DiuConfig().treeFelling.treesBeforeBreaking
                treeFelling.addProperty("treesBeforeBreaking", new)
                LOGGER.info("Config: treeFelling.treesBeforeBreaking had the old default value {}; it is now {}.", old, new)
            }
        }

        /** La sección [name]. Si falta, se crea vacía (y se rellena con los valores por defecto). */
        fun section(name: String): Section {
            val element = root.get(name)
            if (element == null) {
                val created = JsonObject()
                root.add(name, created)
                return Section(name, created)
            }
            if (element.isJsonObject) return Section(name, element.asJsonObject)
            LOGGER.warn("Config: {} should be an object with options, but it is {}; using the default value of every option in it.",
                name, element)
            corrections++
            return Section(name, null)
        }

        fun boolean(section: Section, key: String, default: Boolean): Boolean {
            val element = element(section, key, JsonPrimitive(default)) ?: return default
            if (element is JsonPrimitive && element.isBoolean) return element.asBoolean
            return wrongType(section.path(key), element, "true or false", default)
        }

        fun int(section: Section, key: String, default: Int, range: IntRange): Int {
            val element = element(section, key, JsonPrimitive(default)) ?: return default
            val number = element.asNumberOrNull()
            if (number == null || !number.isIntegral()) return wrongType(section.path(key), element, "a whole number", default)
            return clamp(section.path(key), element, number, range.first.toBigDecimal(), range.last.toBigDecimal())
                .intValueExact()
        }

        fun float(section: Section, key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float {
            val element = element(section, key, JsonPrimitive(default)) ?: return default
            val number = element.asNumberOrNull() ?: return wrongType(section.path(key), element, "a number", default)
            return clamp(section.path(key), element, number, range.start.toBigDecimal(), range.endInclusive.toBigDecimal())
                .toFloat()
        }

        /**
         * Una lista de enteros del mismo tamaño que [default]. Si no lo es (o algún elemento no
         * es un entero), se usa la lista por defecto entera; cada elemento se ajusta a [range].
         */
        fun intList(section: Section, key: String, default: List<Int>, range: IntRange): List<Int> {
            val element = element(section, key, intArray(default)) ?: return default
            val expected = "a list of ${default.size} whole numbers"
            if (element !is JsonArray || element.size() != default.size) {
                return wrongType(section.path(key), element, expected, default)
            }
            val numbers = element.map { it.asNumberOrNull()?.takeIf { n -> n.isIntegral() } }
            if (numbers.any { it == null }) return wrongType(section.path(key), element, expected, default)
            return numbers.mapIndexed { i, number ->
                clamp("${section.path(key)}[$i]", element[i], number!!, range.first.toBigDecimal(), range.last.toBigDecimal())
                    .intValueExact()
            }
        }

        fun <E : Enum<E>> enum(section: Section, key: String, default: E, values: List<E>): E {
            val element = element(section, key, JsonPrimitive(default.name)) ?: return default
            val expected = values.joinToString(" or ") { "\"${it.name}\"" }
            val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
                ?: return wrongType(section.path(key), element, expected, default)
            values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return it }
            LOGGER.warn("Config: {} = {} is not a valid value (expected {}); using \"{}\".",
                section.path(key), element, expected, default.name)
            corrections++
            return default
        }

        /**
         * El valor de [key], o null si hay que usar el valor por defecto sin avisar: porque la
         * sección no es válida (ya se avisó) o porque falta la clave (se añade con [default]).
         */
        private fun element(section: Section, key: String, default: JsonElement): JsonElement? {
            val json = section.json ?: return null
            val element = json.get(key)
            if (element == null) {
                json.add(key, default)
                treeChanged = true
                LOGGER.info("Config: added missing option {} = {}.", section.path(key), default)
            }
            return element
        }

        private fun <T> wrongType(path: String, element: JsonElement, expected: String, default: T): T {
            LOGGER.warn("Config: {} = {} has the wrong type (expected {}); using the default value {}.",
                path, element, expected, default)
            corrections++
            return default
        }

        private fun clamp(path: String, element: JsonElement, value: BigDecimal, min: BigDecimal, max: BigDecimal): BigDecimal {
            val applied = value.max(min).min(max)
            if (applied.compareTo(value) != 0) {
                LOGGER.warn("Config: {} = {} is out of range [{}, {}]; using {}.", path, element, min, max, applied)
                corrections++
            }
            return applied
        }
    }

    /** [json] es null si la sección existe pero no es un objeto. */
    private class Section(val name: String, val json: JsonObject?) {
        fun path(key: String) = "$name.$key"
    }

    /** El número de un valor JSON, o null si no es un número (por ejemplo "64", con comillas). */
    private fun JsonElement.asNumberOrNull(): BigDecimal? {
        if (this !is JsonPrimitive || !isNumber) return null
        // Un exponente enorme (1e9999999999) no cabe ni en BigDecimal.
        return try { asBigDecimal } catch (e: NumberFormatException) { null }
    }

    private fun BigDecimal.isIntegral(): Boolean = signum() == 0 || stripTrailingZeros().scale() <= 0
}
