package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColumnPos;
import net.minecraft.world.World;

import java.util.OptionalInt;

/**
 * Travelling by map, where the game allows it.
 *
 * <p>The atlas is a client mod and cannot move anybody by itself: it sends the
 * same command a player would type, which is why the command is an option —
 * a server running Essentials answers to {@code minecraft:tp}, and some hand
 * travel out through a warp of their own.
 */
public final class MapTeleport {
	/** Any layer height above the world's roof works: depths are stored relative to it. */
	private static final int REFERENCE_TOP = 999;

	private MapTeleport() {
	}

	/**
	 * Whether the map offers to send the player anywhere.
	 *
	 * <p>Under AUTO this asks the only thing that actually knows: the command
	 * tree the server sent this client, which lists exactly the commands this
	 * player may run. That covers singleplayer with cheats, an operator's seat,
	 * and a permissions plugin handing the command to one rank and not another
	 * — none of which the client's own permission level would tell apart.
	 */
	public static boolean allowed() {
		return switch (RoleplayersAtlas.CONFIG.teleport) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> {
				ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
				yield handler != null && handler.getCommandDispatcher().getRoot().getChild(commandName()) != null;
			}
		};
	}

	/** The first word of the configured command: what has to exist in the command tree. */
	private static String commandName() {
		String command = RoleplayersAtlas.CONFIG.teleportCommand.trim();
		if (command.startsWith("/")) command = command.substring(1);
		int space = command.indexOf(' ');
		return space < 0 ? command : command.substring(0, space);
	}

	/** Where a mark, an inscription, a road or a zone would send you. */
	@SuppressWarnings("rawtypes")
	public static BlockPos targetOf(Landmark landmark) {
		if (landmark == null) return null;
		BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		if (pos != null) return pos;
		if (landmark.contains(LandmarkComponentTypes.CHUNKS)) {
			ColumnPos centre = glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new java.util.HashMap<>()));
			if (centre != null) return new BlockPos(centre.x(), 0, centre.z());
		}
		return null;
	}

	/**
	 * The floor the atlas drew at this spot, read back out of the survey — so
	 * clicking a hillside lands on the hillside rather than inside it. Empty
	 * where that chunk has never been walked.
	 */
	public static OptionalInt surfaceY(WorldSummary summary, int x, int z) {
		if (summary == null) return OptionalInt.empty();
		WorldTerrain terrain = summary.terrain();
		if (terrain == null) return OptionalInt.empty();
		ChunkSummary chunk = terrain.get(new ChunkPos(x >> 4, z >> 4));
		if (chunk == null) return OptionalInt.empty();
		LayerSummary.Raw layer = chunk.toSingleLayer(null, null, REFERENCE_TOP);
		if (layer == null) return OptionalInt.empty();
		int index = (x & 15) * 16 + (z & 15);
		if (!layer.exists().get(index)) return OptionalInt.empty();
		// depths are measured down from the reference height, and the water
		// column on top of the floor is where a boat would sit.
		return OptionalInt.of(REFERENCE_TOP - layer.depths()[index] + layer.waterDepths()[index] + 1);
	}

	/**
	 * Sends the travel command. A height of its own is used when the map has
	 * one (a mark remembers the floor it was planted on); otherwise the survey
	 * answers, and failing that the command is sent with the player's own
	 * height, which vanilla resolves against the ground on arrival.
	 */
	public static void to(WorldSummary summary, RegistryKey<World> dim, int x, Integer y, int z) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.getNetworkHandler() == null) return;
		int height;
		if (y != null && y != 0) {
			height = y;
		} else {
			OptionalInt surveyed = surfaceY(summary, x, z);
			height = surveyed.orElse((int) Math.round(player.getY()));
		}
		String command = RoleplayersAtlas.CONFIG.teleportCommand
			.replace("{x}", String.valueOf(x))
			.replace("{y}", String.valueOf(height))
			.replace("{z}", String.valueOf(z))
			.replace("{dim}", dim.getValue().toString())
			.trim();
		if (command.startsWith("/")) command = command.substring(1);
		if (command.isEmpty()) return;
		// A map open on another world sends you there as well, unless the
		// command already says which world it means.
		if (dim != null && !dim.equals(player.getWorld().getRegistryKey()) && !RoleplayersAtlas.CONFIG.teleportCommand.contains("{dim}")) {
			command = "execute in " + dim.getValue() + " run " + command;
		}
		client.getNetworkHandler().sendChatCommand(command);
		player.sendMessage(Text.translatable("gui.roleplayers_atlas.teleport.sent", x, height, z), true);
	}
}
