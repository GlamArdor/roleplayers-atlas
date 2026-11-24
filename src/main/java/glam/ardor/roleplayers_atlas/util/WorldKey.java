package glam.ardor.roleplayers_atlas.util;

import folk.sisby.surveyor.client.ClientSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

/**
 * Names the world the player is in, for anything the atlas keeps on disk per
 * world.
 * <p>
 * Not the server's address: behind a proxy the lobby and the world it sends you
 * on to answer to the same one, so anything filed under it would leak from one
 * into the other. Surveyor tells worlds apart by their biome seed and names its
 * save folder after it – the same distinction, already made, so the atlas
 * borrows it rather than inventing a second one.
 */
public final class WorldKey {
	private WorldKey() {
	}

	public static @Nullable String current(Minecraft client) {
		if (client.isLocalServer() && client.getSingleplayerServer() != null) {
			return "sp:" + client.getSingleplayerServer().getWorldData().getLevelName();
		}
		ClientPacketListener handler = client.getConnection();
		if (handler != null) {
			try {
				ClientSummary summary = ClientSummary.of(handler);
				if (summary != null && summary.saveFile != null && summary.saveFile.getParentFile() != null) {
					return "world:" + summary.saveFile.getParentFile().getName();
				}
			} catch (Exception ignored) {
				// Surveyor hasn't settled yet; the address will do until it has.
			}
		}
		ServerData entry = client.getCurrentServer();
		return entry != null ? "mp:" + entry.ip : null;
	}

	public static @Nullable String current() {
		return current(Minecraft.getInstance());
	}
}
