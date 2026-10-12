package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.entity.TheMinerEntity
import daio7771.dioriteisntuseless.entity.UsefulOwnerEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory

/**
 * The visitors (VisitorEntity). They are never saved with the world and cannot be summoned:
 * only the Abuse Mode brings them (and /diu abuse in development).
 */
object ModEntities {

    private const val PLAYER_WIDTH = 0.6f
    private const val PLAYER_HEIGHT = 1.8f

    /** They are only ever close to the player they visit. */
    private const val CLIENT_TRACKING_CHUNKS = 4

    val THE_MINER: EntityType<TheMinerEntity> = register(
        "the_miner", EntityType.Builder.of(::TheMinerEntity, MobCategory.MISC).sized(PLAYER_WIDTH, PLAYER_HEIGHT)
    )

    /** A neutral id: its name must not show up anywhere in the game (F3, commands). */
    val USEFUL_OWNER: EntityType<UsefulOwnerEntity> = register(
        "figure",
        EntityType.Builder.of(::UsefulOwnerEntity, MobCategory.MISC).sized(
            PLAYER_WIDTH * UsefulOwnerEntity.WIDTH_SCALE,
            PLAYER_HEIGHT * UsefulOwnerEntity.HEIGHT_SCALE,
        )
    )

    private fun <T : Entity> register(name: String, builder: EntityType.Builder<T>): EntityType<T> {
        val id = Dioriteisntuseless.id(name)
        // noSave also skips the data fixer lookup in build(), which a mod's entity does not have.
        val type = builder.noSave().noSummon().fireImmune().clientTrackingRange(CLIENT_TRACKING_CHUNKS).build(id.toString())
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type)
    }

    /** Call from onInitialize: accessing the object registers the types; here, their attributes. */
    fun init() {
        FabricDefaultAttributeRegistry.register(THE_MINER, LivingEntity.createLivingAttributes())
        FabricDefaultAttributeRegistry.register(USEFUL_OWNER, LivingEntity.createLivingAttributes())
    }
}
