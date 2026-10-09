package daio7771.dioriteisntuseless.client.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.loader.api.FabricLoader

/**
 * "modmenu" entrypoint: Mod Menu only loads this class if it is installed. The screen also needs
 * Cloth Config; without it, Mod Menu shows no config button and the JSON is edited by hand.
 */
class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) return super.getModConfigScreenFactory()
        // DiuConfigScreen (and Cloth Config with it) is not loaded until the screen is opened.
        return ConfigScreenFactory { parent -> DiuConfigScreen.create(parent) }
    }
}
