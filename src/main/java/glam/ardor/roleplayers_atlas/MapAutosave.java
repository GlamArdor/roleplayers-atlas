package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.client.ClientSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Writes the explored map to disk every so often.
 * <p>
 * Surveyor only saves a client's world data when the connection ends cleanly,
 * so a crash – or Alt+F4 – threw away everything walked since joining. An
 * evening's exploring is too much to lose to a task manager.
 * <p>
 * Saving is cheap when nothing changed: Surveyor returns immediately unless the
 * data is dirty, so this costs nothing while standing still.
 * <p>
 * The atlas' own files (layers, tracked marks, the hearth) already write
 * themselves the moment they change and need none of this.
 */
public final class MapAutosave {
	/** Long enough not to grind the disk, short enough that losing it wouldn't sting. */
	private static final int INTERVAL_TICKS = 20 * 60;

	private static int countdown = INTERVAL_TICKS;

	private MapAutosave() {
	}

	public static void tick(MinecraftClient client) {
		// Singleplayer keeps its data on the integrated server, which saves on
		// its own schedule; there is nothing here to write.
		if (client.world == null || client.isInSingleplayer()) {
			countdown = INTERVAL_TICKS;
			return;
		}
		if (--countdown > 0) return;
		countdown = INTERVAL_TICKS;
		save(client.getNetworkHandler());
	}

	public static void save(ClientPlayNetworkHandler handler) {
		if (handler == null) return;
		try {
			ClientSummary summary = ClientSummary.of(handler);
			if (summary == null) return;
			// leaveWorld is Surveyor's own "write this dimension out now" – it
			// saves and nothing else, so it is safe to call while still there.
			for (RegistryKey<World> dimension : SurveyorClient.getSummaries(handler).keySet()) {
				summary.leaveWorld(dimension);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Couldn't save the map", e);
		}
	}
}
