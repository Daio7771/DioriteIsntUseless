package daio7771.dioriteisntuseless.client

import net.fabricmc.api.ClientModInitializer

class DioriteisntuselessClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientConfigSync.init()
    }
}
