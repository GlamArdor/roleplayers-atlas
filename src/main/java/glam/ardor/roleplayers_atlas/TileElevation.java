package glam.ardor.roleplayers_atlas;

/**
 * The enum represents the different height levels in biomes.
 */
public enum TileElevation {
	VALLEY("valley"),
	LOW("low"),
	MID("mid"),
	HIGH("high"),
	PEAK("peak");

	public final String name;

	TileElevation(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public String toString() {
		return getName();
	}

	/**
	 * Which tier a piece of land falls into, given how far it stands above the
	 * sea. The thresholds are the player's to set: a world built high would
	 * otherwise read as one endless peak.
	 * <p>
	 * Thresholds out of order simply skip a tier rather than misbehaving – the
	 * cascade takes the first one that matches.
	 */
	public static TileElevation fromBlocksAboveSea(int elevation) {
		AtlasConfig config = RoleplayersAtlas.CONFIG;
		if (elevation < config.elevationLow) {
			return TileElevation.VALLEY;
		} else if (elevation < config.elevationMid) {
			return TileElevation.LOW;
		} else if (elevation < config.elevationHigh) {
			return TileElevation.MID;
		} else if (elevation < config.elevationPeak) {
			return TileElevation.HIGH;
		} else {
			return TileElevation.PEAK;
		}
	}
}
