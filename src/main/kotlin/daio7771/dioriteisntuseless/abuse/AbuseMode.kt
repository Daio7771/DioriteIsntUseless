package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.config.ModConfig

/**
 * Main switch of the Abuse Mode (see docs/claudeplans/HORROR_DESIGN.md).
 *
 * Golden rule 1: nothing in the Abuse Mode may crash the game. All of its code goes through
 * [guard]; if something fails, it is logged and the system shuts down until the server restarts
 * (or the world is closed in single player). The rest of the mod keeps working.
 */
object AbuseMode {

    /** An internal error has shut the system down for this session. Only used from the server thread. */
    private var failed = false

    /** true if it should count and react: enabled in the config and no errors in this session. */
    val active: Boolean get() = !failed && ModConfig.current.abuseMode.enabled

    /**
     * No errors in this session, whether it is enabled or not. Undoing ("Start over" and pending
     * restorations) only needs this: disabling the Abuse Mode does not prevent undoing what was done.
     */
    val healthy: Boolean get() = !failed

    /** When each server starts (also the integrated one when a world is opened). */
    fun resetSession() {
        failed = false
    }

    /**
     * Runs [block] and, if it throws anything, shuts the system down. It is inline so [block] can
     * use return. Errors of the JVM itself (out of memory, etc.) are not swallowed.
     */
    inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            fail(what, e)
        }
    }

    @PublishedApi
    internal fun fail(what: String, error: Throwable) {
        if (!failed) {
            LOGGER.error("Abuse mode: internal error in {}. It is disabled until the world is reopened; " +
                "the rest of the mod keeps working.", what, error)
        }
        failed = true
    }
}
