package daio7771.dioriteisntuseless.abuse.text

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * List of texts from one of the mod's data files, for example:
 *
 * ```
 * { "replace": false, "<key>": ["text", "..."] }
 * ```
 *
 * Like tags: a datapack can add texts, or replace them all with "replace": true.
 * Reloaded with /reload. A broken file is ignored (with a warning in the log): it never crashes the game.
 *
 * @param path path inside data/dioriteisntuseless/, for example "abuse/sign_words.json".
 * @param onLoad receives the texts (no duplicates, in order) every time they are loaded.
 */
class TextListResource(path: String, private val key: String, private val onLoad: (List<String>) -> Unit) {

    private val file = Dioriteisntuseless.id(path)

    fun register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId() = Dioriteisntuseless.id("text_list/${file.path}")
            override fun onResourceManagerReload(manager: ResourceManager) {
                // A failure here would make /reload or the world loading fail: better no texts than that.
                try {
                    onLoad(read(manager))
                } catch (e: Exception) {
                    LOGGER.error("Could not load {}; it will be empty.", file, e)
                    onLoad(emptyList())
                }
            }
        })
    }

    private fun read(manager: ResourceManager): List<String> {
        val texts = LinkedHashSet<String>()
        for (resource in manager.getResourceStack(file)) {
            try {
                val root = resource.openAsReader().use { JsonParser.parseReader(it) } as? JsonObject
                    ?: throw IllegalArgumentException("the file must contain a JSON object { ... }")
                if ((root.get("replace") as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean == true) texts.clear()
                val list = root.getAsJsonArray(key) ?: throw IllegalArgumentException("missing \"$key\" list")
                for (element in list) {
                    val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim()
                    if (text.isNullOrEmpty()) {
                        LOGGER.warn("{} in pack '{}': ignoring {}, it is not a text.", file, resource.sourcePackId(), element)
                    } else {
                        texts += text
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Could not read {} from pack '{}'; ignoring it.", file, resource.sourcePackId(), e)
            }
        }
        return texts.toList()
    }

    override fun toString(): String = file.toString()
}
