package glam.ardor.roleplayers_atlas.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasKeybindings;
import glam.ardor.roleplayers_atlas.MarkerTexture;
import glam.ardor.roleplayers_atlas.TileTexture;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.ButtonComponent;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.CursorComponent;
import glam.ardor.roleplayers_atlas.gui.core.ScreenState;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import glam.ardor.roleplayers_atlas.util.CodecUtil;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColumnPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.joml.Vector2d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class AtlasScreen extends Component implements AtlasRenderer {
	// Atlas Renderer
	public final int bookWidth;
	public final int bookHeight;
	public final int mapWidth;
	public final int mapHeight;
	public static double mapOffsetX;
	public static double mapOffsetY;
	public static int tilePixels = 16;
	public static int tileChunks = 1;
	private RegistryKey<World> dim;
	public int mapScale;
	public PlayerEntity player;
	public WorldAtlasData worldAtlasData;
	public int prevDimScale = 0; // allows tabbing between dims cleanly if you don't manually touch the map in a 0scale dim.

	// Screen Components
	public final BookmarkButton addMarkerBookmark; // Button for placing a marker at current position, local to this Atlas instance.
	public final BookmarkButton addTerritoryBookmark; // Button for painting a named territory area.
	public final BookmarkButton addLabelBookmark; // Button for placing a free-standing pen inscription (text without an icon).
	public final BookmarkButton addRouteBookmark; // Button for drawing a route (dashed path through clicked points).
	public final java.util.List<BlockPos> pendingRoute = new java.util.ArrayList<>(); // Points clicked in route mode.
	public final java.util.Set<ChunkPos> pendingTerritory = new java.util.HashSet<>(); // Chunks painted in territory mode.
	public final BookmarkButton deleteMarkerBookmark; // Button for deleting local markers.
	public final BookmarkButton markerVisibilityBookmark; // Button for showing/hiding all markers.
	public final TextBookmarkButton resetScaleBookmark; // Button for displaying the scale, and setting the scale to 1 chunk / 1 tile / 16px.
	public final BookmarkButton playerBookmark; // Button for restoring player's position at the center of the Atlas.
	public final BookmarkButton spawnBookmark; // Pans to the respawn point; right-click tracks it, shift-click restyles it.
	public final ScrollBoxComponent markerScrollBox = new ScrollBoxComponent(true, BookmarkButton.HEIGHT + BOOKMARK_SPACING);
	public final ScrollBoxComponent dimensionScrollBox = new ScrollBoxComponent(false, BookmarkButton.WIDTH + BOOKMARK_SPACING);
	public final MarkerModal markerModal = new MarkerModal();
	public final BiomeModal biomeModal = new BiomeModal();
	public final CityModal cityModal = new CityModal();
	public final ShareModal shareModal = new ShareModal();
	public final ScreenshotModal screenshotModal = new ScreenshotModal();
	public final BookmarkButton shareMapBookmark; // Button opening the map export/import modal.
	public final BookmarkButton screenshotBookmark; // Button opening the map snapshot modal.
	public final BookmarkButton editBiomesBookmark; // Button for correcting how a biome is drawn.
	public final BookmarkButton paintCityBookmark; // Button for drawing a player-built town onto the map.
	public final net.minecraft.client.gui.widget.TextFieldWidget searchField; // Bookmark list filter, shown when the search tab is open.
	public BookmarkButton searchTab; // Magnifier tab above the layer tabs toggling the search field.
	public BookmarkButton sortTab; // Letter tab under the magnifier, cycling how the list is ordered.
	public boolean searchOpen = false;
	public final BlinkingMarkerComponent markerCursor = new BlinkingMarkerComponent();
	public final CursorComponent eraser = new CursorComponent();
	public final List<BookmarkButton> markerBookmarks = new CopyOnWriteArrayList<>();
	public final List<BookmarkButton> dimBookmarks = new CopyOnWriteArrayList<>();
	public final Map<BookmarkButton, Landmark> bookmarkLandmarks = new java.util.IdentityHashMap<>(); // bookmark → its landmark, for tracking toggles
	public final BookmarkButton clearTrackingBookmark; // Button clearing all guide arrows at once.
	public final List<BookmarkButton> layerTabs = new CopyOnWriteArrayList<>(); // Layer filter toggles (letter tabs).
	public final Map<BookmarkButton, glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer> layerTabLayers = new java.util.IdentityHashMap<>();
	public BookmarkButton addLayerTab; // "+" tab creating a new layer.
	public final LayerModal layerModal = new LayerModal();
	private Landmark pendingTrackClick; // Landmark under an LMB press on the map; toggles tracking on release if no drag happened.
	private double trackClickX, trackClickY;

	// Screen State
	public final ScreenState<AtlasScreen> state = new ScreenState<>((oldState, newState) -> RoleplayersAtlas.lastState.switchTo(newState, this));
	public Landmark hoveredLandmark = null;
	public PlayerSummary hoveredFriend = null;
	public ButtonComponent selectedButton = null; // prevents marker being cancelled right after being pressed
	public Integer targetOffsetX, targetOffsetY; // only screen has smooth scrolling
	public boolean isMouseOverMap = false;
	public boolean isDragging = false;
	public final boolean fullscreen;

	/** A side button's own height plus the hairline under it. */
	private static final int SIDE_BUTTON_ROW = BookmarkButton.HEIGHT + 1;
	/** Low enough that the scroll arrow, when there is one, lands on the book's edge rather than off it. */
	private static final int SIDE_BUTTONS_TOP = 16;
	/** Tools in the right column — see where they are added. */
	private static final int SIDE_BUTTON_COUNT = 12;

	/**
	 * The tools down the right edge. They live in a scroll box for the same
	 * reason the marker list on the left does: on a small window ten of them
	 * don't fit, and running off the page is worse than scrolling. The arrows
	 * only appear when there is something out of sight, so a book with room to
	 * spare looks exactly as it did.
	 */
	public final ScrollBoxComponent sideScrollBox = new ScrollBoxComponent(true, SIDE_BUTTON_ROW);

	public AtlasScreen() {
		fullscreen = RoleplayersAtlas.CONFIG.fullscreen;
		if (fullscreen) {
			bookWidth = (int) (MinecraftClient.getInstance().getWindow().getScaledWidth() * 0.9 - 40);
			bookHeight = (int) (MinecraftClient.getInstance().getWindow().getScaledHeight() * 0.9 - 10);
		} else {
			bookWidth = DEFAULT_BOOK_WIDTH;
			bookHeight = DEFAULT_BOOK_HEIGHT;
		}
		setSize(bookWidth, bookHeight);
		mapWidth = bookWidth - MAP_BORDER_WIDTH * 2;
		mapHeight = bookHeight - MAP_BORDER_HEIGHT * 2;
		mapScale = calculateMapScale();

		playerBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.followPlayer"), RoleplayersAtlas.id("textures/gui/player.png"), DyeColor.GRAY.getEntityColor(), null, 7, 8, false, false);
		addChild(playerBookmark).offsetGuiCoords(bookWidth - 10, bookHeight - MAP_BORDER_HEIGHT - BookmarkButton.HEIGHT - 10);
		playerBookmark.addListener(b -> {
			selectedButton = playerBookmark;
			// Marker bookmark selection now means "tracked", so following the
			// player must not clear it.
			playerBookmark.setSelected(true);
		});

		// The hearth sits above the player button rather than in the list on the
		// left: it isn't one of your marks, it's where you wake up.
		spawnBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.spawn.button"), glam.ardor.roleplayers_atlas.SpawnMarker.texture().id(), glam.ardor.roleplayers_atlas.SpawnMarker.colorOf().getEntityColor(), null, 16, 16, false, false) {
			// Nothing to point at in this dimension, or the mark is switched
			// off: the button steps out of the way entirely.
			private boolean available() {
				return glam.ardor.roleplayers_atlas.SpawnMarker.pos(dim) != null;
			}

			@Override
			public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
				if (!available()) return;
				super.render(context, mouseX, mouseY, partialTick);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				return available() && super.mouseClicked(mouseX, mouseY, button);
			}
		};
		addChild(spawnBookmark).offsetGuiCoords(bookWidth - 10, bookHeight - MAP_BORDER_HEIGHT - BookmarkButton.HEIGHT * 2 - 11);
		spawnBookmark.addListener(b -> {
			net.minecraft.util.math.BlockPos at = glam.ardor.roleplayers_atlas.SpawnMarker.pos(dim);
			if (at == null) return;
			if (hasShiftDown()) {
				Landmark spawn = glam.ardor.roleplayers_atlas.SpawnMarker.get(dim);
				if (spawn == null) return;
				markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), spawn);
				addChild(markerModal);
				return;
			}
			// Following the player re-centres the map every frame, so it has to
			// be let go of first or the pan is undone before it is seen.
			playerBookmark.setSelected(false);
			setTargetPosition(new net.minecraft.util.math.ColumnPos(at.getX(), at.getZ()));
		});

		addMarkerBookmark = new BookmarkButton(TEXT_ADD_MARKER, ICON_ADD_MARKER, DyeColor.RED.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		addMarkerBookmark.addListener(button -> {
			if (state.is(PLACING_MARKER)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
			} else {
				selectedButton = button;
				state.switchTo(PLACING_MARKER, this);

				// While holding shift, we create a marker on the player's position
				if (hasShiftDown()) {
					double dimX = player.getBlockX();
					double dimZ = player.getBlockZ();
					Map<RegistryKey<World>, Integer> scales = RoleplayersAtlas.CONFIG.dimensions.getScales(MinecraftClient.getInstance().getNetworkHandler());
					int newScale = scales.getOrDefault(dim(), 0);
					int oldScale = scales.getOrDefault(player.getWorld().getRegistryKey(), 0);
					if (newScale * oldScale == 0) return; // no ratio!
					double mult = newScale / (double) oldScale;
					dimX = mult * dimX;
					dimZ = mult * dimZ;

					markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), Landmark.create(SurveyorClient.getClientUuid(), RoleplayersAtlas.id("newmarker"), b -> b.add(LandmarkComponentTypes.POS, player.getBlockPos())));
					addChild(markerModal);

					markerCursor.setTexture(markerModal.selectedTexture.id(), markerModal.selectedTexture.textureWidth(), markerModal.selectedTexture.textureHeight());

					addChildBehind(markerModal, markerCursor).setGuiCoords((int) worldXToScreenX(dimX - MARKER_SIZE / 2.0), (int) worldZToScreenY(dimZ - MARKER_SIZE / 2.0));

					// Un-press all keys to prevent player from walking infinitely:
					KeyBinding.unpressAll();

					selectedButton = null;
					state.switchTo(NORMAL, this);

				}
			}
		});
		addTerritoryBookmark = new BookmarkButton(TEXT_ADD_TERRITORY, ICON_ADD_TERRITORY, DyeColor.PURPLE.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		addTerritoryBookmark.addListener(button -> {
			if (state.is(PLACING_TERRITORY)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
				finishTerritory();
			} else {
				selectedButton = button;
				pendingTerritory.clear();
				editingTerritory = null;
				editFloor = 0;
				state.switchTo(PLACING_TERRITORY, this);
			}
		});

		addLabelBookmark = new BookmarkButton(TEXT_ADD_LABEL, ICON_ADD_LABEL, DyeColor.BROWN.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		addLabelBookmark.addListener(button -> {
			if (state.is(PLACING_LABEL)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
			} else {
				selectedButton = button;
				state.switchTo(PLACING_LABEL, this);
			}
		});

		addRouteBookmark = new BookmarkButton(TEXT_ADD_ROUTE, ICON_ADD_ROUTE, DyeColor.CYAN.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		addRouteBookmark.addListener(button -> {
			if (state.is(PLACING_ROUTE)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
				finishRoute();
			} else {
				selectedButton = button;
				pendingRoute.clear();
				state.switchTo(PLACING_ROUTE, this);
			}
		});

		clearTrackingBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.clearTracking"), ICON_CLEAR_TRACKING, DyeColor.LIGHT_GRAY.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		clearTrackingBookmark.addListener(button -> {
			RoleplayersAtlas.trackedMarkers.clear();
			for (BookmarkButton other : markerBookmarks) other.setSelected(false);
			clearTrackingBookmark.setSelected(false);
			selectedButton = null;
			glam.ardor.roleplayers_atlas.TrackedMarkersStore.save();
		});

		shareMapBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.shareMap"), ICON_SHARE_MAP, DyeColor.ORANGE.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		shareMapBookmark.addListener(button -> {
			shareMapBookmark.setSelected(false);
			selectedButton = null;
			state.switchTo(NORMAL, this);
			shareModal.setData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), dim, worldAtlasData);
			addChild(shareModal);
			KeyBinding.unpressAll();
		});

		screenshotBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.screenshot"), ICON_SCREENSHOT, DyeColor.PINK.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		screenshotBookmark.addListener(button -> {
			screenshotBookmark.setSelected(false);
			selectedButton = null;
			state.switchTo(NORMAL, this);
			screenshotModal.setData(worldAtlasData);
			addChild(screenshotModal);
			KeyBinding.unpressAll();
		});

		deleteMarkerBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.delMarker"), ICON_DELETE_MARKER, DyeColor.YELLOW.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		deleteMarkerBookmark.addListener(button -> {
			if (state.is(DELETING_MARKER)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
			} else {
				selectedButton = button;
				state.switchTo(DELETING_MARKER, this);
			}
		});
		markerVisibilityBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.hideMarkers"), ICON_HIDE_MARKERS, DyeColor.GREEN.getEntityColor(), null, 16, 16, false, false);
		// Added to sideScrollBox below, not straight to the screen.
		markerVisibilityBookmark.addListener(button -> {
			selectedButton = null;
			if (state.is(HIDING_MARKERS)) {
				state.switchTo(NORMAL, this);
			} else {
				selectedButton = null;
				state.switchTo(HIDING_MARKERS, this);
			}
		});
		editBiomesBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.editBiomes"), ICON_EDIT_BIOMES, DyeColor.LIME.getEntityColor(), null, 16, 16, false, false);
		// Pressed once, it waits for a click on the map and opens the window on
		// whatever biome is there. Pressed again, it opens the whole list.
		editBiomesBookmark.addListener(button -> {
			if (state.is(PAINTING_BIOME)) {
				// The area has been marked out; now say what to paint it as.
				selectedButton = null;
				state.switchTo(NORMAL, this);
				finishBiomePatch();
			} else if (state.is(PICKING_BIOME)) {
				selectedButton = null;
				state.switchTo(NORMAL, this);
				openBiomeModal(null);
			} else {
				selectedButton = button;
				state.switchTo(PICKING_BIOME, this);
			}
		});
		// A town players built is invisible to the game, so the cartographer draws
		// it: mark out the cells, then say which piece goes on them.
		paintCityBookmark = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.paintCity"), ICON_PAINT_CITY, DyeColor.MAGENTA.getEntityColor(), null, 16, 16, false, false);
		// First press takes the tool up; pressing it again reaches for the box of
		// pieces, the same as right-clicking the map does. The tool is put down
		// with Enter or Escape.
		paintCityBookmark.addListener(button -> {
			selectedButton = button;
			if (state.is(PAINTING_CITY)) {
				openCityPicker();
			} else {
				state.switchTo(PAINTING_CITY, this);
			}
		});
		resetScaleBookmark = new TextBookmarkButton(Text.translatable("gui.roleplayers_atlas.resetScale"), Text.of("1c"));
		// Added to sideScrollBox below, not straight to the screen.
		// Right-column order, top to bottom.
		addChild(sideScrollBox).offsetGuiCoords(bookWidth - 10, SIDE_BUTTONS_TOP);
		int sideY = 0;
		for (Component tool : List.of(deleteMarkerBookmark, addTerritoryBookmark, addMarkerBookmark, addRouteBookmark, addLabelBookmark, markerVisibilityBookmark, clearTrackingBookmark, editBiomesBookmark, paintCityBookmark, shareMapBookmark, screenshotBookmark, resetScaleBookmark)) {
			sideScrollBox.getViewport().addContent(tool).setRelativeY(sideY);
			sideY += SIDE_BUTTON_ROW;
		}
		sideScrollBox.getViewport().setSize(BookmarkButton.WIDTH, sideButtonRows() * SIDE_BUTTON_ROW - 1);
		resetScaleBookmark.addListener(button -> {
			resetZoom();
			resetScaleBookmark.setSelected(false);
		});

		// Bookmark search: a slim filter field next to the magnifier tab.
		// Created before rebuildLayerTabs(), which positions it.
		searchField = new net.minecraft.client.gui.widget.TextFieldWidget(MinecraftClient.getInstance().textRenderer, getGuiX() + 14, getGuiY() + MAP_BORDER_HEIGHT + 12, 110, 12, Text.translatable("gui.roleplayers_atlas.search"));
		searchField.setEditable(true);
		searchField.setFocusUnlocked(true);
		searchField.setMaxLength(64);
		searchField.setDrawsBackground(false);
		searchField.setPlaceholder(Text.translatable("gui.roleplayers_atlas.search"));
		searchField.setChangedListener(query -> updateBookmarkerList());

		addChild(markerScrollBox).setRelativeCoords(-14, MAP_BORDER_HEIGHT + 8);
		rebuildLayerTabs();

		addChild(dimensionScrollBox).setRelativeCoords(MAP_BORDER_WIDTH + 8, mapHeight + MAP_BORDER_HEIGHT + 3);
		int dimsOnScreen = (mapWidth - 20) / ((BookmarkButton.HEIGHT + BOOKMARK_SPACING) - BOOKMARK_SPACING);
		dimensionScrollBox.getViewport().setSize(dimsOnScreen * (BookmarkButton.HEIGHT + BOOKMARK_SPACING) - BOOKMARK_SPACING, BookmarkButton.WIDTH);

		markerModal.addMarkerListener(markerCursor);

		eraser.setTexture(ERASER, 12, 14, 2, 11);

		state.switchTo(RoleplayersAtlas.lastState.is(HIDING_MARKERS) ? HIDING_MARKERS : NORMAL, this);

		for (Identifier id : overlays.keySet()) {
			overlays.get(id).onScreenInit(this);
		}
	}


	public int calculateMapScale() {
		return switch (RoleplayersAtlas.CONFIG.mapScale) {
			case -2 -> Math.max(1, (int) Math.floor(guiScale() / 2.0));
			case -1 -> Math.max(1, (int) Math.ceil(guiScale() / 2.0));
			case 0 -> (int) guiScale();
			default -> RoleplayersAtlas.CONFIG.mapScale;
		};
	}

	public void prepareToOpen() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));

		this.player = MinecraftClient.getInstance().player;
		this.dim = MinecraftClient.getInstance().world.getRegistryKey();
		updateAtlasData();
		if (!RoleplayersAtlas.CONFIG.keepOffset) {
			playerBookmark.setSelected(true);
			setMapPosition(player.getBlockX(), player.getBlockZ());
		}
		if (!RoleplayersAtlas.CONFIG.keepZoom) {
			resetZoom();
		}
	}

	@Override
	public void init() {
		super.init();

		setGuiCoords((this.width - bookWidth) / 2, (this.height - bookHeight) / 2);

		updateScaleBookmark();
		updateBookmarkerList();
	}

	public static final ResourceMetadataSerializer<DimensionTextureMeta> METADATA = CodecUtil.metadataSerializer(DimensionTextureMeta.CODEC, RoleplayersAtlas.id("dimension"));

	public record DimensionTextureMeta(int color, String name) {
		public static final Codec<DimensionTextureMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("color").xmap(s -> {
				try {
					return Integer.parseUnsignedInt(s.replace("#", ""), 16);
				} catch (NumberFormatException e) {
					return 0xFFFFFF;
				}
			}, i -> "#" + StringUtils.leftPad(Integer.toHexString(i & 0x00_FFFFFF), 6, "0")).forGetter(DimensionTextureMeta::color),
			Codec.STRING.fieldOf("name").forGetter(DimensionTextureMeta::name)
		).apply(instance, DimensionTextureMeta::new));
	}

	public void updateBookmarkerList() {
		dimensionScrollBox.getViewport().removeAllContent();
		dimensionScrollBox.setScrollPos(0);
		dimBookmarks.clear();

		for (RegistryKey<World> dimension : dim == null ? new ArrayList<RegistryKey<World>>() : RoleplayersAtlas.CONFIG.dimensions.getOrder(MinecraftClient.getInstance().getNetworkHandler())) {
			Identifier iconId = dimension.getValue().withPath("textures/atlas/dimension/%s.png"::formatted);
			Resource icon = MinecraftClient.getInstance().getResourceManager().getResource(iconId).orElse(null);
			Integer backgroundTint;
			Text name;
			if (icon == null) {
				iconId = ICON_UNKNOWN;
			}
			try {
				DimensionTextureMeta meta = icon.getMetadata().decode(METADATA).orElseThrow();
				backgroundTint = meta.color();
				name = Text.translatable(meta.name());
			} catch (NullPointerException | IOException | NoSuchElementException e) {
				name = Text.of(WordUtils.capitalizeFully(dimension.getValue().getPath().replaceAll("[/_-]", " ")));
				backgroundTint = DyeColor.byIndex(dimension.getValue().toString().hashCode() & 15).getEntityColor();
			}
			BookmarkButton bookmark = new BookmarkButton(name, iconId, backgroundTint, null, 16, 16, false, true);
			bookmark.setSelected(dimension.equals(dim));
			bookmark.addListener(button -> {
				List<RegistryKey<World>> regKeys = RoleplayersAtlas.CONFIG.dimensions.getOrder(client.getNetworkHandler());
				if (regKeys.contains(dimension) && !dim.equals(dimension)) changeDim(dimension);
			});
			dimBookmarks.add(bookmark);
		}

		final int[] contentX = {0};
		for (BookmarkButton bookmark : dimBookmarks) {
			dimensionScrollBox.getViewport().addContent(bookmark).setRelativeX(contentX[0]);
			contentX[0] += BookmarkButton.HEIGHT + BOOKMARK_SPACING;
		}

		markerScrollBox.getViewport().removeAllContent();
		markerScrollBox.setScrollPos(0);
		markerBookmarks.clear();
		bookmarkLandmarks.clear();
		// Not a bookmark in the list, but registering it here is what gives the
		// hearth button the same right-click-to-track behaviour as the others.
		Landmark spawn = glam.ardor.roleplayers_atlas.SpawnMarker.get(dim);
		if (spawn != null) {
			bookmarkLandmarks.put(spawnBookmark, spawn);
			spawnBookmark.setSelected(RoleplayersAtlas.trackedMarkers.contains(RoleplayersAtlas.trackKey(spawn)));
		}

		if (worldAtlasData == null) return;

		// Order: markers, inscriptions, routes, territories.
		String searchQuery = searchField == null ? "" : searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
		worldAtlasData.getEditableLandmarks().entrySet().stream()
			.sorted(bookmarkOrder())
			.forEach(entry -> {
			Landmark landmark = entry.getKey();
			if (!RoleplayersAtlas.layerVisible(landmark)) return;
			MarkerTexture texture = entry.getValue();
			boolean penLabel = Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL));
			boolean route = landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
			boolean territoryLandmark = !landmark.contains(LandmarkComponentTypes.POS) && landmark.contains(LandmarkComponentTypes.CHUNKS);
			Text fallbackName = Text.translatable(route ? "gui.roleplayers_atlas.unnamedRoute" : territoryLandmark ? "gui.roleplayers_atlas.unnamedZone" : penLabel ? "gui.roleplayers_atlas.unnamedLabel" : "gui.roleplayers_atlas.unnamedMarker");
			// Searched by note as well as by name. The note is where the substance
			// of a mark lives — "здесь брод" is written there, not in the title.
			if (!searchQuery.isEmpty()) {
				String named = landmark.getOrDefault(LandmarkComponentTypes.NAME, fallbackName).getString().toLowerCase(java.util.Locale.ROOT);
				String note = landmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.NOTE, "").toLowerCase(java.util.Locale.ROOT);
				if (!named.contains(searchQuery) && !note.contains(searchQuery)) return;
			}
			String trackKey = RoleplayersAtlas.trackKey(landmark);
			BookmarkButton bookmark = penLabel || route
				? new BookmarkButton(landmark.getOrDefault(LandmarkComponentTypes.NAME, fallbackName), route ? ICON_ADD_ROUTE : ICON_ADD_LABEL, landmark.getOrDefault(LandmarkComponentTypes.COLOR, 0xFFFFFF), null, 16, 16, true, false) {
					@Override
					public void drawIcon(DrawContext iconContext, int x, int y) {
						super.drawIcon(iconContext, x, y);
						// Fixed to the tab corner so it looks identical for markers, inscriptions and territories.
						drawTrackedBadge(iconContext, getGuiX() + 1, getGuiY() + 1, trackKey);
					}
				}
				: new MarkerBookmarkButton(landmark.getOrDefault(LandmarkComponentTypes.NAME, fallbackName), texture, landmark.getOrDefault(LandmarkComponentTypes.COLOR, 0xFFFFFF), true, false) {
					@Override
					public void drawIcon(DrawContext iconContext, int x, int y) {
						super.drawIcon(iconContext, x, y);
						drawTrackedBadge(iconContext, getGuiX() + 1, getGuiY() + 1, trackKey);
					}
				};

			bookmark.addListener(button -> {
				if (state.is(NORMAL)) {
					// LMB pans to the landmark; tracking toggles with RMB here or
					// LMB on the marker itself on the map.
					bookmark.setSelected(RoleplayersAtlas.trackedMarkers.contains(RoleplayersAtlas.trackKey(landmark)));
					playerBookmark.setSelected(false);
					BlockPos anchorPos = landmark.get(LandmarkComponentTypes.POS);
				setTargetPosition(anchorPos != null
					? new ColumnPos(anchorPos.getX(), anchorPos.getZ())
					: glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>())));
				} else if (state.is(DELETING_MARKER)) {
					if (!worldAtlasData.deleteLandmark(dim, landmark)) return;
					if (RoleplayersAtlas.trackedMarkers.remove(RoleplayersAtlas.trackKey(landmark))) glam.ardor.roleplayers_atlas.TrackedMarkersStore.save();
					updateBookmarkerList();
					MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1F, 0.5F));
					if (!hasShiftDown()) {
						state.switchTo(NORMAL, this);
					}
				}
			});

			bookmarkLandmarks.put(bookmark, landmark);
			bookmark.setSelected(RoleplayersAtlas.trackedMarkers.contains(RoleplayersAtlas.trackKey(landmark)));

			markerBookmarks.add(bookmark);
		});

		final int[] contentY = {0};
		for (BookmarkButton bookmark : markerBookmarks) {
			markerScrollBox.getViewport().addContent(bookmark).setRelativeY(contentY[0]);
			contentY[0] += BookmarkButton.HEIGHT + BOOKMARK_SPACING;
		}
	}

	/** Opens the biome corrections window, singling out one biome if given. */
	public void openBiomeModal(@org.jetbrains.annotations.Nullable net.minecraft.util.Identifier focus) {
		biomeModal.setData(focus);
		addChild(biomeModal);
		net.minecraft.client.option.KeyBinding.unpressAll();
	}

	public void clearTargetBookmarks(BookmarkButton except) {
		if (playerBookmark != except) playerBookmark.setSelected(false);
		for (BookmarkButton bookmark : markerBookmarks) {
			if (bookmark != except) bookmark.setSelected(false);
		}
	}

	/** A left tab showing a single letter instead of an icon texture. */
	private class LetterTabButton extends BookmarkButton {
		private final String letter;
		private final int letterInk;
		private final boolean showsVisibility;

		LetterTabButton(Text title, String letter, int color, boolean showsVisibility) {
			this(title, letter, color, showsVisibility, false);
		}

		LetterTabButton(Text title, String letter, int color, boolean showsVisibility, boolean silent) {
			super(title, ICON_ADD_MARKER, color, null, 16, 16, true, false);
			// Silent tabs play their own sound instead of the generic click.
			if (silent) this.clickSound = null;
			this.letter = letter;
			this.showsVisibility = showsVisibility;
			// Dark ink on light tabs, light ink on dark ones.
			double luma = 0.299 * ((color >> 16) & 0xFF) + 0.587 * ((color >> 8) & 0xFF) + 0.114 * (color & 0xFF);
			this.letterInk = luma > 110 ? 0xFF2E1A0C : 0xFFF3E7C9;
		}

		@Override
		public void drawIcon(DrawContext context, int x, int y) {
			boolean shown = !showsVisibility || isSelected();
			Text glyph = Text.literal(letter).formatted(Formatting.BOLD);
			int ink = shown ? letterInk : (letterInk & 0xFFFFFF) | 0x66000000;
			context.drawText(textRenderer, glyph, x + (16 - textRenderer.getWidth(glyph)) / 2, y + 4, ink, false);
			if (!shown) {
				// Hidden layer: the same red slash as the clear-tracking icon
				// (2px dark-red line with a darker upper edge, "/" direction).
				for (int i = 0; i <= 13; i++) {
					int lineX = x + 1 + i;
					int lineY = y + 14 - i;
					context.fill(lineX, lineY - 1, lineX + 1, lineY + 1, 0xFF6E2A1E);
					context.fill(lineX, lineY - 2, lineX + 1, lineY - 1, 0xFF3E1A12);
				}
			}
		}
	}

	/**
	 * How the bookmark list is ordered, per the setting on the sort tab.
	 * <p>
	 * By kind is the old grouping and stays the default. By distance answers
	 * "what is near me"; by date, "what did I do lately"; by name, "where is the
	 * one I can remember the name of". Each falls back to the name so the order
	 * is stable rather than shuffling between openings.
	 */
	private java.util.Comparator<Map.Entry<Landmark, MarkerTexture>> bookmarkOrder() {
		java.util.Comparator<Map.Entry<Landmark, MarkerTexture>> byName =
			java.util.Comparator.comparing(e -> displayName(e.getKey()).getString(), String.CASE_INSENSITIVE_ORDER);
		return switch (RoleplayersAtlas.CONFIG.markerSort) {
			case KIND -> java.util.Comparator.comparingInt((Map.Entry<Landmark, MarkerTexture> e) -> {
				if (e.getKey().contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) return 2;
				if (Boolean.TRUE.equals(e.getKey().get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL))) return 1;
				return e.getKey().contains(LandmarkComponentTypes.POS) ? 0 : 3;
			}).thenComparing(byName);
			case DISTANCE -> java.util.Comparator.comparingDouble((Map.Entry<Landmark, MarkerTexture> e) -> distanceToPlayer(e.getKey())).thenComparing(byName);
			// Newest first: the thing you just drew is the thing you are looking for.
			case DATE -> java.util.Comparator.comparingLong((Map.Entry<Landmark, MarkerTexture> e) ->
				-e.getKey().getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.REAL_TIME, 0L)).thenComparing(byName);
			case NAME -> byName;
		};
	}

	/** How far a mark is from the player, in blocks. A zone counts from its middle. */
	private double distanceToPlayer(Landmark landmark) {
		if (player == null) return Double.MAX_VALUE;
		BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		double x, z;
		if (pos != null) {
			x = pos.getX();
			z = pos.getZ();
		} else if (landmark.contains(LandmarkComponentTypes.CHUNKS)) {
			ColumnPos middle = glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
			x = middle.x();
			z = middle.z();
		} else {
			return Double.MAX_VALUE;
		}
		return Math.hypot(x - player.getX(), z - player.getZ());
	}

	/** The sort tab's tooltip, naming the order in force rather than just "sort". */
	private static Text sortTabTitle() {
		return Text.translatable("gui.roleplayers_atlas.sort", sortOrderName());
	}

	public static Text sortOrderName() {
		return Text.translatable("gui.roleplayers_atlas.sort." + RoleplayersAtlas.CONFIG.markerSort.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** What the list calls a mark, falling back to its kind when it has no name. */
	private static Text displayName(Landmark landmark) {
		boolean route = landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
		boolean penLabel = Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL));
		boolean territory = !landmark.contains(LandmarkComponentTypes.POS) && landmark.contains(LandmarkComponentTypes.CHUNKS);
		return landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.translatable(
			route ? "gui.roleplayers_atlas.unnamedRoute"
				: territory ? "gui.roleplayers_atlas.unnamedZone"
				: penLabel ? "gui.roleplayers_atlas.unnamedLabel"
				: "gui.roleplayers_atlas.unnamedMarker"));
	}

	/** Rebuilds the layer filter tabs (dynamic — the player creates layers) and repositions the bookmark list below them. */
	public void rebuildLayerTabs() {
		for (BookmarkButton tab : layerTabs) removeChild(tab);
		layerTabs.clear();
		layerTabLayers.clear();
		if (addLayerTab != null) removeChild(addLayerTab);
		if (searchTab != null) removeChild(searchTab);
		if (sortTab != null) removeChild(sortTab);

		int y = MAP_BORDER_HEIGHT + 8;

		// Magnifier tab: toggles the bookmark search field beside it.
		searchTab = new BookmarkButton(Text.translatable("gui.roleplayers_atlas.search"), ICON_SEARCH, DyeColor.LIGHT_GRAY.getEntityColor(), null, 16, 16, true, false) {
			{
				clickSound = null; // the spyglass sound replaces the generic click
			}
		};
		addChild(searchTab).setRelativeCoords(-14, y);
		if (searchField != null) searchField.setWidth(100);
		searchTab.setSelected(searchOpen);
		searchTab.addListener(button -> {
			searchOpen = !searchOpen;
			glam.ardor.roleplayers_atlas.AtlasSounds.searchToggle(searchOpen);
			searchTab.setSelected(searchOpen);
			searchField.setFocused(searchOpen);
			if (!searchOpen) searchField.setText("");
			updateBookmarkerList();
		});
		y += BookmarkButton.HEIGHT + 1;

		// Sort tab: one letter for the order in force, cycling through the four.
		// A tab rather than a setting because which order you want changes with
		// what you are doing, and walking to the settings screen to change it
		// would mean never changing it.
		// A book rather than a letter: the layer tabs below are letters, and a
		// fifth letter among them read as one more layer instead of a control.
		sortTab = new BookmarkButton(sortTabTitle(), ICON_SORT, DyeColor.LIGHT_BLUE.getEntityColor(), null, 16, 16, true, false);
		addChild(sortTab).setRelativeCoords(-14, y);
		sortTab.addListener(button -> {
			glam.ardor.roleplayers_atlas.AtlasConfig.MarkerSort[] orders = glam.ardor.roleplayers_atlas.AtlasConfig.MarkerSort.values();
			RoleplayersAtlas.CONFIG.markerSort = orders[(RoleplayersAtlas.CONFIG.markerSort.ordinal() + 1) % orders.length];
			RoleplayersAtlas.CONFIG.setAndSave("markerSort", RoleplayersAtlas.CONFIG.markerSort);
			sortTab.setSelected(false);
			rebuildLayerTabs();
			updateBookmarkerList();
		});
		y += BookmarkButton.HEIGHT + 1;

		for (glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer : glam.ardor.roleplayers_atlas.MarkerLayers.all()) {
			// The deaths layer disappears entirely while death markers are off.
			if (glam.ardor.roleplayers_atlas.MarkerLayers.DEATHS_ID.equals(layer.id()) && !RoleplayersAtlas.CONFIG.deathMarkers) continue;
			String letter = layer.name().isBlank() ? "?" : layer.name().substring(0, 1).toUpperCase();
			BookmarkButton tab = new LetterTabButton(Text.literal(layer.name()), letter, layer.color(), true, true);
			addChild(tab).setRelativeCoords(-14, y);
			y += BookmarkButton.HEIGHT + 1;
			tab.setSelected(!RoleplayersAtlas.hiddenLayers.contains(layer.id()));
			tab.addListener(button -> {
				if (!RoleplayersAtlas.hiddenLayers.remove(layer.id())) RoleplayersAtlas.hiddenLayers.add(layer.id());
				glam.ardor.roleplayers_atlas.AtlasSounds.layerTab();
				tab.setSelected(!RoleplayersAtlas.hiddenLayers.contains(layer.id()));
				updateBookmarkerList();
			});
			layerTabs.add(tab);
			layerTabLayers.put(tab, layer);
		}

		addLayerTab = new LetterTabButton(Text.translatable("gui.roleplayers_atlas.addLayer"), "+", DyeColor.GRAY.getEntityColor(), false);
		addChild(addLayerTab).setRelativeCoords(-14, y);
		addLayerTab.addListener(button -> {
			addLayerTab.setSelected(false);
			selectedButton = null;
			layerModal.setData(SurveyorClient.tryGetSummary(dim), worldAtlasData, null);
			addChild(layerModal);
			KeyBinding.unpressAll();
		});
		y += BookmarkButton.HEIGHT + 1;

		int layerFiltersHeight = y - (MAP_BORDER_HEIGHT + 8) + 4;
		// The list draws a scroll arrow above its first row, so it starts a
		// little below where the layer tabs end instead of touching them.
		int listGap = 7;
		markerScrollBox.setRelativeCoords(-14, MAP_BORDER_HEIGHT + 8 + layerFiltersHeight + listGap);
		// A row occupies its own height plus the gap under it. Dividing by the
		// height alone counted rows that don't fit and ran the list off the page.
		int rowHeight = BookmarkButton.HEIGHT + BOOKMARK_SPACING;
		int markersOnScreen = Math.max(1, (mapHeight - 20 - layerFiltersHeight - listGap) / rowHeight);
		markerScrollBox.getViewport().setSize(BookmarkButton.WIDTH, markersOnScreen * rowHeight - BOOKMARK_SPACING);
	}

	/**
	 * Full-map export: pans the real map view cell by cell, captures each
	 * finished frame between ticks and stitches them — the result is pixel
	 * identical to the in-game map, curved labels included.
	 */
	private static final class FullMapExport {
		final AtlasScreen screen;
		final net.minecraft.client.texture.NativeImage image;
		final int cellsX;
		final int cellsZ;
		final int startBlockX;
		final int startBlockZ;
		final int viewBlocksW;
		final int viewBlocksH;
		final double ppb;
		final double scaleFactor;
		final double savedOffsetX;
		final double savedOffsetY;
		final int savedTilePixels;
		final int savedTileChunks;
		final boolean[] hasContent;
		final int totalContent;
		int index = 0;
		int done = 0;
		int framesSincePos = 0;
		boolean capturing = false;

		FullMapExport(AtlasScreen screen, net.minecraft.client.texture.NativeImage image, int cellsX, int cellsZ, int startBlockX, int startBlockZ, int viewBlocksW, int viewBlocksH, double ppb, double scaleFactor, double savedOffsetX, double savedOffsetY, int savedTilePixels, int savedTileChunks, boolean[] hasContent) {
			this.hasContent = hasContent;
			int content = 0;
			for (boolean cell : hasContent) {
				if (cell) content++;
			}
			this.totalContent = content;
			this.ppb = ppb;
			this.screen = screen;
			this.image = image;
			this.cellsX = cellsX;
			this.cellsZ = cellsZ;
			this.startBlockX = startBlockX;
			this.startBlockZ = startBlockZ;
			this.viewBlocksW = viewBlocksW;
			this.viewBlocksH = viewBlocksH;
			this.scaleFactor = scaleFactor;
			this.savedOffsetX = savedOffsetX;
			this.savedOffsetY = savedOffsetY;
			this.savedTilePixels = savedTilePixels;
			this.savedTileChunks = savedTileChunks;
		}
	}

	private static FullMapExport fullExport = null;

	public boolean isExporting() {
		return fullExport != null && fullExport.screen == this;
	}

	public void scheduleFullCapture(boolean markers, boolean zones, boolean routes, boolean labels) {
		if (fullExport != null || worldAtlasData == null) return;
		glam.ardor.roleplayers_atlas.util.Rect scope = worldAtlasData.getScope();
		int startBlockX = (scope.minX - 1) * 16;
		int startBlockZ = (scope.minY - 1) * 16;
		int endBlockX = (scope.maxX + 2) * 16;
		int endBlockZ = (scope.maxY + 2) * 16;
		double scaleFactor = client.getWindow().getScaleFactor();

		double savedOffsetX = mapOffsetX;
		double savedOffsetY = mapOffsetY;
		int savedTilePixels = tilePixels;
		int savedTileChunks = tileChunks;
		// Fixed export scale: 16px per chunk (the standard 1c zoom).
		tilePixels = 16;
		tileChunks = 1;
		double ppb = 1.0;

		int viewBlocksW = Math.max(16, (int) (mapWidth / ppb));
		int viewBlocksH = Math.max(16, (int) (mapHeight / ppb));
		int cellsX = Math.max(1, (endBlockX - startBlockX + viewBlocksW - 1) / viewBlocksW);
		int cellsZ = Math.max(1, (endBlockZ - startBlockZ + viewBlocksH - 1) / viewBlocksH);
		long totalPxW = (long) ((endBlockX - startBlockX) * ppb * scaleFactor);
		long totalPxH = (long) ((endBlockZ - startBlockZ) * ppb * scaleFactor);
		// Extreme-size guard only: prevents a native-memory hard crash on
		// absurdly large maps (~1.5 GB image); practically unreachable at 1c.
		if (totalPxW <= 0 || totalPxH <= 0 || totalPxW * totalPxH > 400_000_000L) {
			tilePixels = savedTilePixels;
			tileChunks = savedTileChunks;
			if (player != null) player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.tooLarge"), false);
			return;
		}

		// Cells with no explored chunks are skipped: they stay flat parchment
		// instead of costing a captured frame each.
		boolean[] hasContent = new boolean[cellsX * cellsZ];
		for (ChunkPos chunk : worldAtlasData.exploredChunks()) {
			int cellX = (chunk.x * 16 - startBlockX) / viewBlocksW;
			int cellZ = (chunk.z * 16 - startBlockZ) / viewBlocksH;
			if (cellX >= 0 && cellX < cellsX && cellZ >= 0 && cellZ < cellsZ) hasContent[cellX + cellZ * cellsX] = true;
		}
		int firstCell = 0;
		while (firstCell < hasContent.length && !hasContent[firstCell]) firstCell++;
		if (firstCell >= hasContent.length) {
			tilePixels = savedTilePixels;
			tileChunks = savedTileChunks;
			if (player != null) player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.empty"), false);
			return;
		}

		try {
			net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage((int) totalPxW, (int) totalPxH, false);
			image.fillRect(0, 0, (int) totalPxW, (int) totalPxH, 0xFFE7D29E);
			glam.ardor.roleplayers_atlas.ParchmentExport.beginSequenceFilter(markers, zones, routes, labels);
			fullExport = new FullMapExport(this, image, cellsX, cellsZ, startBlockX, startBlockZ, viewBlocksW, viewBlocksH, ppb, scaleFactor, savedOffsetX, savedOffsetY, savedTilePixels, savedTileChunks, hasContent);
			fullExport.index = firstCell;
			positionForCell(firstCell);
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Full map export failed to start", e);
			tilePixels = savedTilePixels;
			tileChunks = savedTileChunks;
		}
	}

	private void positionForCell(int index) {
		int cellX = index % fullExport.cellsX;
		int cellZ = index / fullExport.cellsX;
		double centerX = fullExport.startBlockX + cellX * fullExport.viewBlocksW + fullExport.viewBlocksW / 2.0;
		double centerZ = fullExport.startBlockZ + cellZ * fullExport.viewBlocksH + fullExport.viewBlocksH / 2.0;
		mapOffsetX = -centerX * getPixelsPerBlock();
		mapOffsetY = -centerZ * getPixelsPerBlock();
		targetOffsetX = null;
		targetOffsetY = null;
		panAnimated = false;
		playerBookmark.setSelected(false);
		fullExport.framesSincePos = 0;
	}

	/** End-of-tick driver for the stitched export (registered once in mod init). */
	public static void tickFullExport(MinecraftClient client) {
		FullMapExport export = fullExport;
		if (export == null || export.capturing) return;
		if (client.currentScreen != export.screen) {
			abortFullExport();
			return;
		}
		if (export.framesSincePos < 2) return;
		export.capturing = true;
		int index = export.index;
		net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), full -> {
			try {
				int cellX = index % export.cellsX;
				int cellZ = index / export.cellsX;
				int srcX = (int) ((export.screen.getGuiX() + MAP_BORDER_WIDTH) * export.scaleFactor);
				int srcY = (int) ((export.screen.getGuiY() + MAP_BORDER_HEIGHT) * export.scaleFactor);
				int cellPxW = (int) (export.viewBlocksW * export.ppb * export.scaleFactor);
				int cellPxH = (int) (export.viewBlocksH * export.ppb * export.scaleFactor);
				int dstX = cellX * cellPxW;
				int dstY = cellZ * cellPxH;
				int copyW = Math.min(Math.min(cellPxW, export.image.getWidth() - dstX), full.getWidth() - srcX);
				int copyH = Math.min(Math.min(cellPxH, export.image.getHeight() - dstY), full.getHeight() - srcY);
				for (int y = 0; y < copyH; y++) {
					for (int x = 0; x < copyW; x++) {
						export.image.setColorArgb(dstX + x, dstY + y, full.getColorArgb(srcX + x, srcY + y));
					}
				}
			} catch (Exception e) {
				RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Full map export cell failed", e);
			} finally {
				full.close();
			}
			export.done++;
			do {
				export.index++;
			} while (export.index < export.cellsX * export.cellsZ && !export.hasContent[export.index]);
			if (export.index >= export.cellsX * export.cellsZ) {
				finishFullExport(export);
			} else {
				export.screen.positionForCell(export.index);
				export.capturing = false;
			}
		});
	}

	private static void finishFullExport(FullMapExport export) {
		var player = MinecraftClient.getInstance().player;
		try {
			net.minecraft.client.texture.NativeImage framed = glam.ardor.roleplayers_atlas.ParchmentExport.decorate(export.image);
			java.nio.file.Path file = glam.ardor.roleplayers_atlas.ParchmentExport.save(framed, "atlas_map");
			if (framed != export.image) framed.close();
			if (player != null) {
				player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.saved", glam.ardor.roleplayers_atlas.ParchmentExport.fileLink(file)), false);
				glam.ardor.roleplayers_atlas.AtlasSounds.exportDone();
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Full map export failed", e);
			if (player != null) player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.failed"), false);
		}
		restoreAfterExport(export);
	}

	private static void abortFullExport() {
		FullMapExport export = fullExport;
		if (export == null) return;
		restoreAfterExport(export);
	}

	private static void restoreAfterExport(FullMapExport export) {
		export.image.close();
		glam.ardor.roleplayers_atlas.ParchmentExport.endSequenceFilter();
		AtlasScreen screen = export.screen;
		screen.tilePixels = export.savedTilePixels;
		screen.tileChunks = export.savedTileChunks;
		screen.mapOffsetX = export.savedOffsetX;
		screen.mapOffsetY = export.savedOffsetY;
		screen.updateScaleBookmark();
		fullExport = null;
	}

	/** Schedules a crop-of-view snapshot: captured at end of tick, when the finished frame (with the GUI) is in the framebuffer. */
	public void scheduleViewCapture(boolean markers, boolean zones, boolean routes, boolean labels) {
		double scaleFactor = client.getWindow().getScaleFactor();
		int x = Math.max(0, (int) ((getGuiX() + MAP_BORDER_WIDTH) * scaleFactor));
		int y = Math.max(0, (int) ((getGuiY() + MAP_BORDER_HEIGHT) * scaleFactor));
		glam.ardor.roleplayers_atlas.ParchmentExport.requestViewCapture(x, y, (int) (mapWidth * scaleFactor), (int) (mapHeight * scaleFactor), markers, zones, routes, labels);
	}

	/**
	 * The dating footer of a tooltip. A mark copied from someone else's scroll
	 * is attributed to whoever drew it and dated by their hand — the stamp is
	 * their claim, not something this client recomputes.
	 */
	private static java.util.List<Text> dateLines(Landmark landmark) {
		java.util.List<Text> lines = new ArrayList<>();
		Long day = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.DAY);
		Long realTime = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.REAL_TIME);
		// Both the mark's own switch and the two in the settings have to allow it.
		boolean dated = !Boolean.FALSE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DATE))
			&& glam.ardor.roleplayers_atlas.AtlasTime.datingShown();
		String source = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.SOURCE);
		if (source != null && !source.isEmpty() && !source.equals(glam.ardor.roleplayers_atlas.AtlasTime.selfName())) {
			// Who told you never changes; going there only adds a line under it.
			lines.add((dated
				? glam.ardor.roleplayers_atlas.AtlasTime.hearsay(source, day, realTime)
				: Text.translatable("gui.roleplayers_atlas.marker.hearsayPlain", source)
			).copy().formatted(Formatting.GRAY, Formatting.ITALIC));
			Long verified = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.CONFIRMED_DAY);
			if (verified != null) {
				net.minecraft.text.MutableText seen = dated
					? glam.ardor.roleplayers_atlas.AtlasTime.dateText(verified, landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.CONFIRMED_REAL_TIME))
					: null;
				lines.add((seen == null
					? Text.translatable("gui.roleplayers_atlas.marker.verifiedPlain")
					: Text.translatable("gui.roleplayers_atlas.marker.verifiedDate", seen)
				).formatted(Formatting.DARK_GREEN, Formatting.ITALIC));
			}
			return lines;
		}
		if (dated && day != null) {
			net.minecraft.text.MutableText stamp = glam.ardor.roleplayers_atlas.AtlasTime.dateText(day, realTime);
			if (stamp != null) lines.add(stamp.formatted(Formatting.DARK_GRAY));
		}
		return lines;
	}

	/** Word-wraps a plain note into tooltip-sized lines. */
	static java.util.List<String> wrapPlain(String text, int maxChars) {
		java.util.List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank()) return lines;
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			// Words longer than a line are hard-split so they can't overflow.
			while (word.length() > maxChars) {
				if (!line.isEmpty()) {
					lines.add(line.toString());
					line.setLength(0);
				}
				lines.add(word.substring(0, maxChars));
				word = word.substring(maxChars);
			}
			if (line.length() + word.length() + 1 > maxChars && !line.isEmpty()) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) line.append(' ');
			line.append(word);
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double lenSq = dx * dx + dy * dy;
		double t = lenSq < 1.0E-6 ? 0 : net.minecraft.util.math.MathHelper.clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0, 1);
		return Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
	}

	/** Small guide-arrow badge in the bookmark tab corner while its landmark is tracked. */
	private static void drawTrackedBadge(DrawContext context, int x, int y, String trackKey) {
		if (!RoleplayersAtlas.trackedMarkers.contains(trackKey)) return;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(0.5F, 0.5F);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, GUIDE_ARROW, 0, 0, 0, 0, 16, 16, 16, 16, 0xFFE8C878);
		context.getMatrices().popMatrix();
	}

	/** Toggles the guide arrow for a landmark and syncs the bookmark highlight. */
	public void toggleTracking(Landmark landmark) {
		String key = RoleplayersAtlas.trackKey(landmark);
		if (!RoleplayersAtlas.trackedMarkers.remove(key)) RoleplayersAtlas.trackedMarkers.add(key);
		boolean tracked = RoleplayersAtlas.trackedMarkers.contains(key);
		glam.ardor.roleplayers_atlas.AtlasSounds.trackToggle(tracked);
		bookmarkLandmarks.forEach((button, other) -> {
			if (RoleplayersAtlas.trackKey(other).equals(key)) button.setSelected(tracked);
		});
		glam.ardor.roleplayers_atlas.TrackedMarkersStore.save();
	}

	public void updateMouse(double mouseX, double mouseY) {
		double relativeMouseX = mouseX - getGuiX();
		double relativeMouseY = mouseY - getGuiY();
		isMouseOverMap = relativeMouseX >= MAP_BORDER_WIDTH && relativeMouseX <= MAP_BORDER_WIDTH + mapWidth && relativeMouseY >= MAP_BORDER_HEIGHT && relativeMouseY <= MAP_BORDER_HEIGHT + mapHeight;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX, mouseY);
		updateMouse(mouseX, mouseY);
	}

	private ChunkPos territoryRectStart;
	private final Set<ChunkPos> territoryRectApplied = new java.util.HashSet<>();

	/**
	 * A right press that may yet turn out to be a click rather than a drag.
	 * Dragged it pans the map; released where it went down it means whatever the
	 * tool in hand takes it to mean.
	 */
	private boolean pendingRightClick = false;
	private double rightClickX, rightClickY;

	/** Chunks marked out for repainting, and the rectangle drag that adds to them. */
	public final Set<ChunkPos> pendingPatch = new java.util.HashSet<>();
	private ChunkPos patchRectStart;
	private final Set<ChunkPos> patchRectApplied = new java.util.HashSet<>();

	private void applyPatchRect(ChunkPos corner, boolean erase) {
		if (patchRectStart == null) return;
		int was = pendingPatch.size();
		pendingPatch.removeAll(patchRectApplied);
		patchRectApplied.clear();
		int minX = Math.max(Math.min(patchRectStart.x, corner.x), patchRectStart.x - 96);
		int maxX = Math.min(Math.max(patchRectStart.x, corner.x), patchRectStart.x + 96);
		int minZ = Math.max(Math.min(patchRectStart.z, corner.z), patchRectStart.z - 96);
		int maxZ = Math.min(Math.max(patchRectStart.z, corner.z), patchRectStart.z + 96);
		java.util.List<ChunkPos> lift = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				ChunkPos chunk = new ChunkPos(x, z);
				if (erase) {
					if (!pendingPatch.remove(chunk) && paintedChunks().contains(chunk)) lift.add(chunk);
				} else if (worldAtlasData != null && worldAtlasData.hasTile(chunk) && pendingPatch.add(chunk)) {
					patchRectApplied.add(chunk);
				}
			}
		}
		// Rubbing over ground that is already corrected lifts the correction, in
		// one go for the whole rectangle rather than a redraw per cell.
		if (!lift.isEmpty()) {
			glam.ardor.roleplayers_atlas.BiomeOverrides.setPatches(dim, lift, null);
			glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(true);
		} else if (pendingPatch.size() != was) {
			glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(erase);
		}
	}

	private Set<ChunkPos> paintedChunks() {
		return glam.ardor.roleplayers_atlas.BiomeOverrides.patchedChunks(dim);
	}

	private void paintPatchAt(double mouseX, double mouseY, boolean erase) {
		ChunkPos chunk = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
		if (erase) {
			// The rubber does the obvious thing at each stage: it takes a cell out
			// of the selection if it is in one, and otherwise lifts the correction
			// that is already there. Without the second half, a correction made
			// yesterday could only be undone by repainting over it.
			if (pendingPatch.remove(chunk)) {
				glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(true);
			} else if (paintedChunks().contains(chunk)) {
				glam.ardor.roleplayers_atlas.BiomeOverrides.setPatches(dim, java.util.List.of(chunk), null);
				glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(true);
			}
			return;
		}
		// Painting blank page would take and show nothing; the stroke simply
		// doesn't land there, silently, the way a pencil skips a gap.
		if (worldAtlasData == null || !worldAtlasData.hasTile(chunk)) return;
		// Only a cell that actually changed makes a sound. Dragging back and
		// forth across ground already painted should be silent — the brush is
		// not doing anything, so it should not be heard doing it.
		if (pendingPatch.add(chunk)) glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(false);
	}

	/**
	 * The piece in hand. The town brush works like a pencil rather than a form to
	 * fill in: pick a piece once, then every cell it touches is built on there and
	 * then, and the mode stays in hand until it is let go of.
	 */
	public Identifier heldCityPiece = null;
	private ChunkPos cityRectStart;

	/** Takes a piece off the picker and goes straight back to drawing with it. */
	public void holdCityPiece(Identifier piece) {
		heldCityPiece = piece;
		selectedButton = paintCityBookmark;
		state.switchTo(PAINTING_CITY, this);
	}

	/** Shift-drag fills the rectangle as it grows; nothing already laid is taken back. */
	private void applyCityRect(ChunkPos corner, boolean erase) {
		if (cityRectStart == null) return;
		int minX = Math.max(Math.min(cityRectStart.x, corner.x), cityRectStart.x - 96);
		int maxX = Math.min(Math.max(cityRectStart.x, corner.x), cityRectStart.x + 96);
		int minZ = Math.max(Math.min(cityRectStart.z, corner.z), cityRectStart.z - 96);
		int maxZ = Math.min(Math.max(cityRectStart.z, corner.z), cityRectStart.z + 96);
		boolean any = false;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				any |= buildAt(new ChunkPos(x, z), erase, false);
			}
		}
		if (any) glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(erase);
	}

	/**
	 * Builds on, or clears, one cell. A town can only stand on ground that has
	 * been walked: drawing houses on blank page would show nothing, so the stroke
	 * simply doesn't land there.
	 */
	private boolean buildAt(ChunkPos chunk, boolean erase, boolean sound) {
		if (!erase && (heldCityPiece == null || worldAtlasData == null || !worldAtlasData.hasTile(chunk))) return false;
		boolean changed = glam.ardor.roleplayers_atlas.CityPaint.paint(dim, chunk, erase ? null : heldCityPiece);
		if (changed && sound) glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(erase);
		return changed;
	}

	private void buildAt(double mouseX, double mouseY, boolean erase) {
		buildAt(new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4), erase, true);
	}

	/** Opens the box of pieces, to take a different one. */
	private void openCityPicker() {
		cityModal.setData(dim);
		addChild(cityModal);
		KeyBinding.unpressAll();
	}

	/**
	 * Rings a set of cells on the map. Used to show corrections and hand-drawn
	 * towns while their tool is out — a correction made weeks ago is otherwise
	 * impossible to find again, and undoing it means remembering where it was.
	 */
	private void outlineCells(net.minecraft.client.gui.DrawContext context, java.util.Collection<ChunkPos> cells, int lineSize, int fill, int ink) {
		for (ChunkPos chunk : cells) {
			int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
			int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
			int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
			int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
			if (x2 < 0 || y2 < 0 || x1 > bookWidth || y1 > bookHeight) continue;
			context.fill(x1, y1, x2, y2, fill);
			context.fill(x1, y1, x1 + lineSize, y2, ink);
			context.fill(x2 - lineSize, y1, x2, y2, ink);
			context.fill(x1, y1, x2, y1 + lineSize, ink);
			context.fill(x1, y2 - lineSize, x2, y2, ink);
		}
	}

	/** The area is marked out — open the picker to say what it should become. */
	private void finishBiomePatch() {
		if (pendingPatch.isEmpty()) return;
		biomeModal.setPatchData(dim, new java.util.HashSet<>(pendingPatch));
		pendingPatch.clear();
		addChild(biomeModal);
		net.minecraft.client.option.KeyBinding.unpressAll();
	}

	/** Shift-drag: the selection stretches as a rectangle from the anchor corner. */
	private void applyTerritoryRect(ChunkPos corner, boolean erase) {
		if (territoryRectStart == null) return;
		int was = pendingTerritory.size();
		pendingTerritory.removeAll(territoryRectApplied);
		territoryRectApplied.clear();
		int minX = Math.max(Math.min(territoryRectStart.x, corner.x), territoryRectStart.x - 96);
		int maxX = Math.min(Math.max(territoryRectStart.x, corner.x), territoryRectStart.x + 96);
		int minZ = Math.max(Math.min(territoryRectStart.z, corner.z), territoryRectStart.z - 96);
		int maxZ = Math.min(Math.max(territoryRectStart.z, corner.z), territoryRectStart.z + 96);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				ChunkPos chunk = new ChunkPos(x, z);
				if (erase) {
					pendingTerritory.remove(chunk);
				} else if (pendingTerritory.add(chunk)) {
					territoryRectApplied.add(chunk);
				}
			}
		}
		if (pendingTerritory.size() != was) glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(erase);
	}

	private void paintTerritoryAt(double mouseX, double mouseY, boolean erase) {
		ChunkPos chunk = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
		boolean changed = erase ? pendingTerritory.remove(chunk) : pendingTerritory.add(chunk);
		if (changed) glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(erase);
	}

	/** The route being lengthened, so finishing replaces it instead of drawing a second one. */
	private Landmark extendingRoute = null;
	/** How many points it already had, to warn when the undo starts eating them. */
	private int extendFloor = 0;

	/**
	 * Picks an existing route back up: its points become the ones already
	 * placed, and every new click carries on from the last of them.
	 */
	public void startExtendingRoute(Landmark route) {
		java.util.List<BlockPos> points = route.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
		if (points == null || points.isEmpty()) return;
		extendingRoute = route;
		extendFloor = points.size();
		pendingRoute.clear();
		pendingRoute.addAll(points);
		selectedButton = addRouteBookmark;
		state.switchTo(PLACING_ROUTE, this);
	}

	/** Takes the last point off the path being drawn. */
	private void undoRoutePoint() {
		if (pendingRoute.isEmpty()) return;
		pendingRoute.remove(pendingRoute.size() - 1);
		glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(true);
	}

	private void finishRoute() {
		if (pendingRoute.size() < 2) {
			// Every point taken back and confirmed anyway: the road is gone.
			// Escape still leaves it alone, so this can only be meant.
			if (extendingRoute != null) {
				folk.sisby.surveyor.WorldSummary summary = SurveyorClient.tryGetSummary(dim);
				if (summary != null && summary.landmarks() != null) {
					summary.landmarks().remove(extendingRoute.owner(), extendingRoute.id());
					updateBookmarkerList();
				}
			}
			pendingRoute.clear();
			extendingRoute = null;
			return;
		}
		long sumX = 0;
		long sumZ = 0;
		for (BlockPos point : pendingRoute) {
			sumX += point.getX();
			sumZ += point.getZ();
		}
		BlockPos center = new BlockPos((int) (sumX / pendingRoute.size()), 0, (int) (sumZ / pendingRoute.size()));
		java.util.List<BlockPos> routePoints = java.util.List.copyOf(pendingRoute);

		// Lengthening a road settles it straight away: its name, colour and layer
		// were chosen once already, and asking again to change nothing is a step
		// for the sake of a step.
		if (extendingRoute != null) {
			Landmark old = extendingRoute;
			extendingRoute = null;
			extendFloor = 0;
			pendingRoute.clear();
			folk.sisby.surveyor.WorldSummary summary = SurveyorClient.tryGetSummary(dim);
			if (summary != null && summary.landmarks() != null) {
				// The id carries the path's middle, which has just moved — so the
				// entry is written under a new one and the old is taken away.
				String name = old.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
				Identifier newId = RoleplayersAtlas.id("route/" + center.getX() + "/" + center.getZ() + "/" + Integer.toHexString(name.hashCode()));
				summary.landmarks().remove(old.owner(), old.id());
				summary.landmarks().put(WorldAtlasData.copyLandmarkWith(old, newId, m -> {
					m.set(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE, routePoints);
					m.set(LandmarkComponentTypes.POS, center);
				}));
				glam.ardor.roleplayers_atlas.AtlasSounds.routeNode();
				updateBookmarkerList();
			}
			return;
		}

		markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(),
			Landmark.create(SurveyorClient.getClientUuid(), RoleplayersAtlas.id("newroute"), b -> b.add(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE, routePoints).add(LandmarkComponentTypes.POS, center)));
		pendingRoute.clear();
		addChild(markerModal);
		KeyBinding.unpressAll();
	}

	/** The zone being reshaped, so finishing replaces it instead of drawing a second one. */
	private Landmark editingTerritory = null;
	/** How many chunks it covered when it was picked up, to warn when the erasing goes deep. */
	private int editFloor = 0;

	/**
	 * Picks an existing zone back up: its chunks become the ones already painted,
	 * and the brush adds to or takes from them as if they had just been drawn.
	 */
	public void startEditingTerritory(Landmark territory) {
		Map<RegionPos, java.util.BitSet> regions = territory.get(LandmarkComponentTypes.CHUNKS);
		if (regions == null || regions.isEmpty()) return;
		editingTerritory = territory;
		pendingTerritory.clear();
		pendingTerritory.addAll(RegionPos.regionsToChunks(regions));
		editFloor = pendingTerritory.size();
		selectedButton = addTerritoryBookmark;
		state.switchTo(PLACING_TERRITORY, this);
	}

	private void finishTerritory() {
		// Reshaping a zone settles it straight away: its name, colour and layer
		// were chosen once already, and asking again to change nothing is a step
		// for the sake of a step.
		if (editingTerritory != null) {
			Landmark old = editingTerritory;
			editingTerritory = null;
			editFloor = 0;
			Set<ChunkPos> cells = Set.copyOf(pendingTerritory);
			pendingTerritory.clear();
			folk.sisby.surveyor.WorldSummary summary = SurveyorClient.tryGetSummary(dim);
			if (summary == null || summary.landmarks() == null) return;
			summary.landmarks().remove(old.owner(), old.id());
			// Every chunk rubbed out and confirmed anyway: the zone is gone.
			// Escape still leaves it alone, so this can only be meant.
			if (!cells.isEmpty()) {
				Map<RegionPos, java.util.BitSet> shape = glam.ardor.roleplayers_atlas.util.TerritoryUtil.chunksToRegions(cells);
				ColumnPos center = glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(shape);
				// The id carries the zone's middle, which may have just moved — so
				// the entry is written under a new one and the old is taken away.
				String name = old.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
				Identifier newId = RoleplayersAtlas.id("territory/" + center.x() + "/" + center.z() + "/" + Integer.toHexString(name.hashCode()));
				summary.landmarks().put(WorldAtlasData.copyLandmarkWith(old, newId, m -> m.set(LandmarkComponentTypes.CHUNKS, shape)));
				glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(false);
			}
			updateBookmarkerList();
			return;
		}
		if (pendingTerritory.isEmpty()) return;
		Map<RegionPos, java.util.BitSet> regions = glam.ardor.roleplayers_atlas.util.TerritoryUtil.chunksToRegions(pendingTerritory);
		pendingTerritory.clear();
		markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), Landmark.create(SurveyorClient.getClientUuid(), RoleplayersAtlas.id("newterritory"), b -> b.add(LandmarkComponentTypes.CHUNKS, regions)));
		addChild(markerModal);
		KeyBinding.unpressAll();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseState) {
		updateMouse(mouseX, mouseY);
		if (screenshotModal.getParent() != null) {
			return screenshotModal.mouseClicked(mouseX, mouseY, mouseState);
		}
		if (layerModal.getParent() != null) {
			return layerModal.mouseClicked(mouseX, mouseY, mouseState);
		}
		if (shareModal.getParent() != null) {
			return shareModal.mouseClicked(mouseX, mouseY, mouseState);
		}
		if (markerModal.getParent() != null) {
			return markerModal.mouseClicked(mouseX, mouseY, mouseState);
		}
		if (cityModal.getParent() != null) {
			return cityModal.mouseClicked(mouseX, mouseY, mouseState);
		}

		// Bookmark search field focus (only while the magnifier tab is open).
		if (searchOpen) {
			boolean overSearch = mouseX >= searchField.getX() && mouseX < searchField.getX() + searchField.getWidth() && mouseY >= searchField.getY() && mouseY < searchField.getY() + searchField.getHeight();
			searchField.setFocused(overSearch);
			if (overSearch) {
				searchField.mouseClicked(mouseX, mouseY, mouseState);
				return true;
			}
		}

		// RMB on a layer tab edits that layer (the personal layer is fixed).
		if (mouseState == GLFW.GLFW_MOUSE_BUTTON_2 && state.is(NORMAL)) {
			for (BookmarkButton tab : layerTabs) {
				glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer = layerTabLayers.get(tab);
				if (layer != null && !glam.ardor.roleplayers_atlas.MarkerLayers.DEFAULT_ID.equals(layer.id()) && tab.isMouseOver((int) mouseX, (int) mouseY)) {
					layerModal.setData(SurveyorClient.tryGetSummary(dim), worldAtlasData, layer);
					addChild(layerModal);
					KeyBinding.unpressAll();
					return true;
				}
			}
		}

		// Ctrl-LMB on a bookmark travels to it, the same as ctrl-clicking the
		// mark itself on the map.
		if (mouseState == GLFW.GLFW_MOUSE_BUTTON_1 && state.is(NORMAL)
			&& hasControlDown()
			&& glam.ardor.roleplayers_atlas.MapTeleport.allowed()) {
			for (Map.Entry<BookmarkButton, Landmark> entry : bookmarkLandmarks.entrySet()) {
				if (!entry.getKey().isMouseOver((int) mouseX, (int) mouseY)) continue;
				BlockPos target = glam.ardor.roleplayers_atlas.MapTeleport.targetOf(entry.getValue());
				if (target == null) continue;
				glam.ardor.roleplayers_atlas.MapTeleport.to(SurveyorClient.tryGetSummary(dim), dim, target.getX(), target.getY() != 0 ? target.getY() : null, target.getZ());
				close();
				return true;
			}
		}

		// RMB on a bookmark in the list toggles its guide arrow (LMB pans there).
		if (mouseState == GLFW.GLFW_MOUSE_BUTTON_2 && state.is(NORMAL)) {
			for (Map.Entry<BookmarkButton, Landmark> entry : bookmarkLandmarks.entrySet()) {
				if (entry.getKey().isMouseOver((int) mouseX, (int) mouseY)) {
					toggleTracking(entry.getValue());
					return true;
				}
			}
		}

		if (super.mouseClicked(mouseX, mouseY, mouseState)) return true;

		// Ctrl-click travels: to whatever mark is under the cursor, or to the
		// spot itself. Only where the game lets the player teleport at all.
		if (state.is(NORMAL) && mouseState == GLFW.GLFW_MOUSE_BUTTON_1 && isMouseOverMap
			&& hasControlDown()
			&& glam.ardor.roleplayers_atlas.MapTeleport.allowed()) {
			BlockPos target = hoveredLandmark != null ? glam.ardor.roleplayers_atlas.MapTeleport.targetOf(hoveredLandmark) : null;
			int x = target != null ? target.getX() : screenXToWorldX(mouseX);
			int z = target != null ? target.getZ() : screenYToWorldZ(mouseY);
			Integer y = target != null && target.getY() != 0 ? target.getY() : null;
			glam.ardor.roleplayers_atlas.MapTeleport.to(SurveyorClient.tryGetSummary(dim), dim, x, y, z);
			close();
			return true;
		}

		// LMB pressed on a marker/territory on the map: remember it — the guide
		// arrow toggles on release, unless the press turned into a map drag.
		if (state.is(NORMAL) && mouseState == GLFW.GLFW_MOUSE_BUTTON_1 && isMouseOverMap && hoveredLandmark != null
			&& (hoveredLandmark.contains(LandmarkComponentTypes.POS) || hoveredLandmark.contains(LandmarkComponentTypes.CHUNKS))
			// The hearth is global-owned so nothing else can touch it, but it is
			// still yours to track and to restyle.
			&& (glam.ardor.roleplayers_atlas.SpawnMarker.is(hoveredLandmark) || !hoveredLandmark.owner().equals(WorldLandmarks.GLOBAL))) {
			pendingTrackClick = hoveredLandmark;
			trackClickX = mouseX;
			trackClickY = mouseY;
		}

		// If clicked on the map, start dragging
		if (state.is(NORMAL) && hoveredLandmark != null && (hoveredLandmark.contains(LandmarkComponentTypes.POS) || hoveredLandmark.contains(LandmarkComponentTypes.CHUNKS)) && (glam.ardor.roleplayers_atlas.SpawnMarker.is(hoveredLandmark) || (!hoveredLandmark.owner().equals(WorldLandmarks.GLOBAL) && SurveyorClient.canModify(hoveredLandmark.owner()))) && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
			markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), hoveredLandmark);
			addChild(markerModal);

			markerCursor.setTexture(markerModal.selectedTexture.id(), MARKER_SIZE, MARKER_SIZE);
			addChildBehind(markerModal, markerCursor).setGuiCoords((int) mouseX - MARKER_SIZE / 2, (int) mouseY - MARKER_SIZE / 2);

			// Un-press all keys to prevent player from walking infinitely:
			KeyBinding.unpressAll();

			state.switchTo(NORMAL, this);
			return true;
		} else if (!state.is(NORMAL) && !state.is(HIDING_MARKERS)) {
			if (state.is(PLACING_TERRITORY)) {
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
					isDragging = true;
					return true;
				}
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
					if (hasShiftDown()) {
						territoryRectStart = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
						territoryRectApplied.clear();
						applyTerritoryRect(territoryRectStart, hasControlDown());
					} else {
						territoryRectStart = null;
						paintTerritoryAt(mouseX, mouseY, hasControlDown());
					}
					return true;
				}
				return false;
			}
			if (state.is(PAINTING_CITY)) {
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
					// Dragged it pans; clicked in place it opens the box of pieces.
					isDragging = true;
					rightClickX = mouseX;
					rightClickY = mouseY;
					pendingRightClick = true;
					return true;
				}
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
					if (hasShiftDown()) {
						cityRectStart = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
						applyCityRect(cityRectStart, hasControlDown());
					} else {
						cityRectStart = null;
						buildAt(mouseX, mouseY, hasControlDown());
					}
					return true;
				}
				return false;
			}
			if (state.is(PLACING_ROUTE)) {
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
					isDragging = true;
					rightClickX = mouseX;
					rightClickY = mouseY;
					pendingRightClick = true;
					return true;
				}
				if (isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
					pendingRoute.add(new BlockPos(screenXToWorldX(mouseX), 0, screenYToWorldZ(mouseY)));
					glam.ardor.roleplayers_atlas.AtlasSounds.routeNode();
					return true;
				}
				return false;
			}
			if (state.is(PLACING_LABEL) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
				markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), Landmark.create(SurveyorClient.getClientUuid(), RoleplayersAtlas.id("newlabel"), b -> b.add(LandmarkComponentTypes.POS, new BlockPos(screenXToWorldX(mouseX), 0, screenYToWorldZ(mouseY))).add(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL, true)));
				addChild(markerModal);

				markerCursor.setTexture(ICON_ADD_LABEL, 16, 16);
				addChildBehind(markerModal, markerCursor).setGuiCoords((int) mouseX - MARKER_SIZE / 2, (int) mouseY - MARKER_SIZE / 2);

				// Un-press all keys to prevent player from walking infinitely:
				KeyBinding.unpressAll();

				state.switchTo(NORMAL, this);
				return true;
			}
			if (state.is(PLACING_MARKER) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
				markerModal.setMarkerData(SurveyorClient.tryGetSummary(dim), player.getWorld().getRegistryManager(), Landmark.create(SurveyorClient.getClientUuid(), RoleplayersAtlas.id("newmarker"), b -> b.add(LandmarkComponentTypes.POS, new BlockPos(screenXToWorldX(mouseX), 0, screenYToWorldZ(mouseY)))));
				addChild(markerModal);

				markerCursor.setTexture(markerModal.selectedTexture.id(), MARKER_SIZE, MARKER_SIZE);
				addChildBehind(markerModal, markerCursor).setGuiCoords((int) mouseX - MARKER_SIZE / 2, (int) mouseY - MARKER_SIZE / 2);

				// Un-press all keys to prevent player from walking infinitely:
				KeyBinding.unpressAll();

				state.switchTo(NORMAL, this);
				return true;
			} else if (state.is(DELETING_MARKER) && hoveredLandmark != null && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
				if (worldAtlasData.deleteLandmark(dim, hoveredLandmark)) {
					updateBookmarkerList();
					MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1F, 0.5F));
				}
			} else if (state.is(PICKING_BIOME) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
				// Held still it names a biome; dragged it pans. Which one it was
				// is only known on release.
				isDragging = true;
				rightClickX = mouseX;
				rightClickY = mouseY;
				pendingRightClick = true;
				return true;
			} else if (state.is(PICKING_BIOME) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
				// Left button starts marking out an area to repaint.
				pendingPatch.clear();
				state.switchTo(PAINTING_BIOME, this);
				selectedButton = editBiomesBookmark;
				if (hasShiftDown()) {
					patchRectStart = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
					patchRectApplied.clear();
					applyPatchRect(patchRectStart, hasControlDown());
				} else {
					patchRectStart = null;
					paintPatchAt(mouseX, mouseY, hasControlDown());
				}
				return true;
			} else if (state.is(PAINTING_BIOME) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
				// The right button does both, told apart by whether the mouse
				// moved: dragged it pans, clicked in place it corrects the biome
				// there. One gesture, and no mode to remember being in.
				isDragging = true;
				rightClickX = mouseX;
				rightClickY = mouseY;
				pendingRightClick = true;
				return true;
			} else if (state.is(PAINTING_BIOME) && isMouseOverMap && mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
				if (hasShiftDown()) {
					patchRectStart = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
					patchRectApplied.clear();
					applyPatchRect(patchRectStart, hasControlDown());
				} else {
					patchRectStart = null;
					paintPatchAt(mouseX, mouseY, hasControlDown());
				}
				return true;
			}
			if (!hasShiftDown() || !state.is(DELETING_MARKER)) {
				state.switchTo(NORMAL, this);
			}
		} else if (isMouseOverMap && selectedButton == null) {
			isDragging = true;
			return true;
		}

		return false;
	}

	/** A line said once and then gone — what was just taken back, and the like. */
	private Text flashText = null;
	private long flashUntil = 0L;

	private void flash(Text text) {
		flashText = text;
		flashUntil = net.minecraft.util.Util.getMeasuringTimeMs() + 2500L;
	}

	/** Whichever dialog is up, topmost first, or null when the book is bare. */
	private Component openModal() {
		for (Component modal : List.of(screenshotModal, layerModal, shareModal, biomeModal, cityModal, markerModal)) {
			if (modal.getParent() != null) return modal;
		}
		return null;
	}

	private void changeDim(RegistryKey<World> newDim) {
		Map<RegistryKey<World>, Integer> scales = RoleplayersAtlas.CONFIG.dimensions.getScales(MinecraftClient.getInstance().getNetworkHandler());
		int newScale = scales.getOrDefault(newDim, 0);
		int oldScale = scales.getOrDefault(this.dim, 0);
		int newPrevDimScale = 0;
		if (oldScale == 0 && prevDimScale != 0) {
			oldScale = prevDimScale;
		} else if (newScale == 0) {
			newPrevDimScale = oldScale;
		}
		dim = newDim;
		if (newScale * oldScale > 0) {
			double mult = newScale / (double) oldScale;
			mapOffsetX = mult * mapOffsetX;
			mapOffsetY = mult * mapOffsetY;
			if (newScale < oldScale) {
				while (zoomIn(false, (16 << RoleplayersAtlas.CONFIG.maxTilePixels))) {
					oldScale /= 2;
					if ((newScale / (double) oldScale) >= 1) break;
				}
			} else if (oldScale < newScale) {
				while (zoomOut(false, (1 << RoleplayersAtlas.CONFIG.maxTileChunks))) {
					oldScale *= 2;
					if ((newScale / (double) oldScale) <= 1) break;
				}
			}
		}
		if (newPrevDimScale != 0) prevDimScale = newPrevDimScale;
		client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.1F));
		updateAtlasData();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchField != null && searchField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
				searchField.setFocused(false);
				if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					searchOpen = false;
					if (searchTab != null) searchTab.setSelected(false);
					searchField.setText("");
					updateBookmarkerList();
				}
				return true;
			}
			searchField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		// A window open on top of the book gets every key to itself. Without this
		// the book's own shortcuts — Enter, Delete, Tab, the arrows — fired behind
		// the dialog, and Enter never reached the Done button it belonged to.
		Component modal = openModal();
		if (modal != null) {
			modal.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		// Ctrl+Z and Ctrl+Y anywhere in the book, whatever tool is in hand.
		if ((keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y) && hasControlDown()) {
			boolean forward = keyCode == GLFW.GLFW_KEY_Y;
			Text moved = forward ? glam.ardor.roleplayers_atlas.AtlasUndo.redo() : glam.ardor.roleplayers_atlas.AtlasUndo.undo();
			if (moved == null) {
				flash(Text.translatable(forward ? "gui.roleplayers_atlas.redo.nothing" : "gui.roleplayers_atlas.undo.nothing").formatted(Formatting.GRAY));
			} else {
				flash(Text.translatable(forward ? "gui.roleplayers_atlas.redo.done" : "gui.roleplayers_atlas.undo.done", moved).formatted(Formatting.GOLD));
				glam.ardor.roleplayers_atlas.AtlasSounds.paintTerritory(!forward);
				updateBookmarkerList();
			}
			return true;
		}
		if ((AtlasKeybindings.ATLAS_KEYMAPPING.matchesKey(keyCode, scanCode) && this.markerModal.getParent() == null && this.shareModal.getParent() == null && this.layerModal.getParent() == null && this.screenshotModal.getParent() == null)) {
			close();
			return true;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_UP -> navigateMap(0, NAVIGATE_STEP);
			case GLFW.GLFW_KEY_DOWN -> navigateMap(0, -NAVIGATE_STEP);
			case GLFW.GLFW_KEY_LEFT -> navigateMap(NAVIGATE_STEP, 0);
			case GLFW.GLFW_KEY_RIGHT -> navigateMap(-NAVIGATE_STEP, 0);
			case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoomIn(true, (16 << RoleplayersAtlas.CONFIG.maxTilePixels));
			case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoomOut(true, (1 << RoleplayersAtlas.CONFIG.maxTileChunks));
			case GLFW.GLFW_KEY_TAB -> {
				List<RegistryKey<World>> regKeys = RoleplayersAtlas.CONFIG.dimensions.getOrder(client.getNetworkHandler());
				if (regKeys.contains(dim)) changeDim(regKeys.get((regKeys.size() + regKeys.indexOf(dim) + 1) % regKeys.size()));
			}
			case GLFW.GLFW_KEY_DELETE -> {
				// Del toggles marker-deletion mode, same as the eraser bookmark.
				if (state.is(DELETING_MARKER)) {
					selectedButton = null;
					state.switchTo(NORMAL, this);
				} else {
					selectedButton = deleteMarkerBookmark;
					state.switchTo(DELETING_MARKER, this);
				}
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				// Enter finishes whatever is being drawn, same as pressing its tool again.
				if (state.is(PICKING_BIOME)) {
					selectedButton = null;
					state.switchTo(NORMAL, this);
					openBiomeModal(null);
				} else if (state.is(PAINTING_BIOME)) {
					selectedButton = null;
					state.switchTo(NORMAL, this);
					finishBiomePatch();
				} else if (state.is(PAINTING_CITY)) {
					// Every stroke is already down; Enter settles them and puts the
					// tool away, exactly as pressing its button again would.
					selectedButton = null;
					glam.ardor.roleplayers_atlas.CityPaint.flush();
					state.switchTo(NORMAL, this);
				} else if (state.is(PLACING_TERRITORY)) {
					selectedButton = null;
					state.switchTo(NORMAL, this);
					finishTerritory();
				} else if (state.is(PLACING_ROUTE)) {
					selectedButton = null;
					state.switchTo(NORMAL, this);
					finishRoute();
				}
			}
			case GLFW.GLFW_KEY_ESCAPE -> {
				// Escape steps out of whatever tool is in hand before it closes the
				// book — losing an unfinished drawing to a stray keypress, and the
				// map with it, is a poor trade.
				if (!state.is(NORMAL)) {
					// Town strokes are already on the map; leaving the tool is what
					// settles them onto disk.
					glam.ardor.roleplayers_atlas.CityPaint.flush();
					pendingPatch.clear();
					pendingRoute.clear();
					pendingTerritory.clear();
					extendingRoute = null;
					editingTerritory = null;
					editFloor = 0;
					selectedButton = null;
					state.switchTo(NORMAL, this);
				} else {
					close();
				}
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		updateMouse(mouseX, mouseY);
		if (super.mouseScrolled(mouseX, mouseY, dx, dy)) return true;
		// A window is up and the wheel missed its lists: it still belongs to the
		// window. Letting it through zoomed the map behind — so scrolling past the
		// end of a list quietly rearranged the page you were choosing from.
		if (openModal() != null) return true;
		if (dy != 0) {
			int direction = dy > 0 ? 1 : -1;
			if ((dy > 0 ? zoomIn(true, (16 << RoleplayersAtlas.CONFIG.maxTilePixels)) : zoomOut(true, 1 << RoleplayersAtlas.CONFIG.maxTileChunks)) && (isMouseOverMap || isDragging)) { // Keep mouse over the same block.
				double xOffset = (getGuiX() + MAP_BORDER_WIDTH + (double) mapWidth / 2 - mouseX) * direction;
				double yOffset = (getGuiY() + MAP_BORDER_HEIGHT + (double) mapHeight / 2 - mouseY) * direction;
				if (Math.abs(xOffset) > 5 || Math.abs(yOffset) > 5) { // Stay centered if mouse is roughly in the center (e.g. on a centered player pin)
					mapOffsetX += xOffset / (direction < 0 ? 2.0 : 1.0);
					mapOffsetY += yOffset / (direction < 0 ? 2.0 : 1.0);
					clearTargetBookmarks(null);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchField != null && searchField.isFocused()) {
			searchField.charTyped(chr, modifiers);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int mouseState) {
		// A right press that never moved was a click: correct the biome there.
		if (pendingRightClick && mouseState == GLFW.GLFW_MOUSE_BUTTON_2) {
			pendingRightClick = false;
			boolean movedOn = Math.abs(mouseX - rightClickX) >= 4 || Math.abs(mouseY - rightClickY) >= 4;
			if (!movedOn && (state.is(PAINTING_BIOME) || state.is(PICKING_BIOME))) {
				ChunkPos chunk = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
				pendingPatch.clear();
				selectedButton = null;
				isDragging = false;
				state.switchTo(NORMAL, this);
				openBiomeModal(worldAtlasData == null ? null : worldAtlasData.biomeAt(chunk));
				return true;
			}
			if (!movedOn && state.is(PLACING_ROUTE)) {
				isDragging = false;
				undoRoutePoint();
				return true;
			}
			if (!movedOn && state.is(PAINTING_CITY)) {
				isDragging = false;
				openCityPicker();
				return true;
			}
		}
		// A clean click (no drag) on a marker or territory toggles its guide arrow.
		if (mouseState == GLFW.GLFW_MOUSE_BUTTON_1 && pendingTrackClick != null) {
			if (Math.abs(mouseX - trackClickX) < 4 && Math.abs(mouseY - trackClickY) < 4) {
				toggleTracking(pendingTrackClick);
			}
			pendingTrackClick = null;
		}
		// The hand came off the button: whatever the town brush laid down during
		// the drag gets written to disk now, once, instead of per cell.
		if (mouseState == GLFW.GLFW_MOUSE_BUTTON_1) {
			cityRectStart = null;
			glam.ardor.roleplayers_atlas.CityPaint.flush();
		}
		boolean result = false;
		if (mouseState != -1) {
			result = selectedButton != null || isDragging;
			selectedButton = null;
			isDragging = false;
		}
		return super.mouseReleased(mouseX, mouseY, mouseState) || result;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int lastMouseButton, double deltaX, double deltaY) {
		if (state.is(PLACING_TERRITORY) && lastMouseButton == GLFW.GLFW_MOUSE_BUTTON_1) {
			updateMouse(mouseX, mouseY);
			if (isMouseOverMap) {
				if (hasShiftDown() && territoryRectStart != null) {
					applyTerritoryRect(new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4), hasControlDown());
				} else {
					paintTerritoryAt(mouseX, mouseY, hasControlDown());
				}
			}
			return true;
		}
		if (state.is(PAINTING_CITY) && lastMouseButton == GLFW.GLFW_MOUSE_BUTTON_1) {
			updateMouse(mouseX, mouseY);
			if (isMouseOverMap) {
				if (hasShiftDown() && cityRectStart != null) {
					applyCityRect(new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4), hasControlDown());
				} else {
					buildAt(mouseX, mouseY, hasControlDown());
				}
			}
			return true;
		}
		if (state.is(PAINTING_BIOME) && lastMouseButton == GLFW.GLFW_MOUSE_BUTTON_1) {
			updateMouse(mouseX, mouseY);
			if (isMouseOverMap) {
				if (hasShiftDown() && patchRectStart != null) {
					applyPatchRect(new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4), hasControlDown());
				} else {
					paintPatchAt(mouseX, mouseY, hasControlDown());
				}
			}
			return true;
		}
		boolean result = false;
		if (isDragging) {
			prevDimScale = 0;
			clearTargetBookmarks(null);
			mapOffsetX += deltaX;
			mapOffsetY += deltaY;
			result = true;
		}
		return super.mouseDragged(mouseX, mouseY, lastMouseButton, deltaX, deltaY) || result;
	}

	@Override
	public void tick() {
		super.tick();
		// The atlas pauses the game, so nothing else is draining the queue of
		// chunks left to draw. Without this a biome correction made from here
		// would only show after closing the map.
		if (worldAtlasData != null) {
			folk.sisby.surveyor.WorldSummary summary = SurveyorClient.tryGetSummary(dim);
			if (summary != null) worldAtlasData.drawQueued(summary, RoleplayersAtlas.CONFIG.chunkTickLimit);
		}
		if (player == null) return;

		double dimX = player.getBlockX();
		double dimZ = player.getBlockZ();
		Map<RegistryKey<World>, Integer> scales = RoleplayersAtlas.CONFIG.dimensions.getScales(MinecraftClient.getInstance().getNetworkHandler());
		int newScale = scales.getOrDefault(dim(), 0);
		int oldScale = scales.getOrDefault(player.getWorld().getRegistryKey(), 0);
		if (newScale * oldScale > 0) {
			double mult = newScale / (double) oldScale;
			dimX = mult * dimX;
			dimZ = mult * dimZ;
			if (playerBookmark.isSelected() && (mapOffsetX != -dimX * getPixelsPerBlock() || mapOffsetY != -dimZ * getPixelsPerBlock())) {
				ColumnPos playerPos = new ColumnPos((int) dimX, (int) dimZ);
				if (panAnimated && targetOffsetX != null) {
					// A glide towards the player is already running: retarget it
					// without restarting the 1s timer.
					targetOffsetX = playerPos.x();
					targetOffsetY = playerPos.z();
				} else {
					// Far away (the arrow was just clicked): the same smooth 1s pan
					// as other bookmarks. Nearby: snap, so following stays tight.
					double distX = Math.abs(mapOffsetX + dimX * getPixelsPerBlock());
					double distY = Math.abs(mapOffsetY + dimZ * getPixelsPerBlock());
					setTargetPosition(playerPos, Math.max(distX, distY) > 8 * getPixelsPerBlock());
				}
			}
		}

		// Non-animated targets (following the player) snap directly; animated
		// pans run per-frame in updatePanAnimation for smoothness.
		if (!panAnimated) {
			if (targetOffsetX != null) {
				mapOffsetX = getTargetPositionX();
				targetOffsetX = null;
			}
			if (targetOffsetY != null) {
				mapOffsetY = getTargetPositionY();
				targetOffsetY = null;
			}
		}
	}

	public void updateAtlasData() {
		worldAtlasData = WorldAtlasData.getOrCreate(dim);
		updateBookmarkerList();
		updateScaleBookmark();
	}

	public void navigateMap(int dx, int dy) {
		mapOffsetX += dx;
		mapOffsetY += dy;
		clearTargetBookmarks(null);
	}

	public void softNavigateMap(int dx, int dy) {
		mapOffsetX += dx;
		mapOffsetY += dy;
	}

	public void setMapPosition(int x, int z) {
		mapOffsetX = (int) (-x * getPixelsPerBlock());
		mapOffsetY = (int) (-z * getPixelsPerBlock());
	}

	public void setTargetPosition(ColumnPos pos) {
		setTargetPosition(pos, true);
	}

	public void setTargetPosition(ColumnPos pos, boolean animate) {
		targetOffsetX = pos.x();
		targetOffsetY = pos.z();
		panAnimated = animate;
		if (animate) {
			panStartOffsetX = mapOffsetX;
			panStartOffsetY = mapOffsetY;
			panStartMs = net.minecraft.util.Util.getMeasuringTimeMs();
		}
	}

	/** Fixed-duration pan to the target: always {@value #PAN_MS} ms, eased. */
	private static final long PAN_MS = 1000;
	private double panStartOffsetX, panStartOffsetY;
	private long panStartMs;
	private boolean panAnimated;

	private void updatePanAnimation() {
		if (targetOffsetX == null || targetOffsetY == null || !panAnimated) return;
		float t = Math.min((net.minecraft.util.Util.getMeasuringTimeMs() - panStartMs) / (float) PAN_MS, 1.0F);
		float eased = glam.ardor.roleplayers_atlas.AtlasHoldMode.easeInOutCubic(t);
		mapOffsetX = panStartOffsetX + (getTargetPositionX() - panStartOffsetX) * eased;
		mapOffsetY = panStartOffsetY + (getTargetPositionY() - panStartOffsetY) * eased;
		if (t >= 1.0F) {
			targetOffsetX = null;
			targetOffsetY = null;
			panAnimated = false;
		}
	}

	public double getTargetPositionX() {
		return -targetOffsetX * getPixelsPerBlock();
	}

	public double getTargetPositionY() {
		return -targetOffsetY * getPixelsPerBlock();
	}

	public void updateScaleBookmark() {
		int tileSizeBlocks = (tileChunks * 16 * 16) / tilePixels;
		int defaultTileSizeBlocks = 16;
		int rulerSizeBlocks = (int) (tileSizeBlocks / getEffectiveScale());
		resetScaleBookmark.setLabel(Text.literal(
			rulerSizeBlocks == 16 | rulerSizeBlocks >= 32 ? "%dc".formatted(rulerSizeBlocks / 16) : "%db".formatted(rulerSizeBlocks)).formatted(
			tileSizeBlocks < defaultTileSizeBlocks ? Formatting.DARK_RED : tileSizeBlocks == defaultTileSizeBlocks ? Formatting.BLACK : Formatting.DARK_BLUE
		));
	}

	public boolean zoomIn(boolean playSound, int maxTilePixels) {
		prevDimScale = 0;
		if (tileChunks == 1) {
			if (tilePixels >= maxTilePixels) return false;
			tilePixels <<= 1;
			if (playSound) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_SPYGLASS_USE, 1.0F));
		} else {
			tileChunks >>= 1;
			if (playSound) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		}
		mapOffsetX *= 2;
		mapOffsetY *= 2;
		updateScaleBookmark();
		return true;
	}

	public boolean zoomOut(boolean playSound, int maxTileChunks) {
		prevDimScale = 0;
		if (tilePixels == 16) {
			if (tileChunks >= maxTileChunks) return false;
			tileChunks <<= 1;
			if (playSound) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
		} else {
			tilePixels >>= 1;
			if (playSound) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_SPYGLASS_USE, 1.0F));
		}
		mapOffsetX /= 2;
		mapOffsetY /= 2;
		updateScaleBookmark();
		return true;
	}

	@SuppressWarnings("StatementWithEmptyBody")
	public void resetZoom() {
		if (zoomIn(true, 8)) {
			while (zoomIn(false, 8)) ;
		} else if (zoomOut(true, 1)) {
			while (zoomOut(false, 1)) ;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		// No manual renderBackground: since 1.21.6 the framework backgrounds the
		// screen in renderWithTooltip before render() and forbids a second blur.
		updatePanAnimation();
		int trueMouseX = mouseX;
		int trueMouseY = mouseY;
		if (markerModal.getParent() != null || shareModal.getParent() != null || layerModal.getParent() != null || screenshotModal.getParent() != null || isExporting()) {
			mouseX = -100;
			mouseY = -100;
		}
		mapScale = calculateMapScale();

		if (isExporting()) {
			// A flat parchment fill: the book textures carry the spine crease and
			// edge shading, which would stripe every stitched export cell.
			context.fill(getGuiX(), getGuiY(), getGuiX() + bookWidth, getGuiY() + bookHeight, 0xFFE7D29E);
		} else if (fullscreen) {
			int left_width = bookWidth / 2 - 15;
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FULLSCREEN, getGuiX(), getGuiY(), left_width, bookHeight);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FULLSCREEN_M, getGuiX() + left_width, getGuiY(), 29, bookHeight);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FULLSCREEN_R, getGuiX() + left_width + 29, getGuiY(), left_width + 1, bookHeight);
		} else {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, BOOK, getGuiX(), getGuiY(), 0, 0, bookWidth, bookHeight, bookWidth, bookHeight);
		}

		if (worldAtlasData == null) return;

		context.enableScissor(getGuiX() + MAP_BORDER_WIDTH, getGuiY() + MAP_BORDER_HEIGHT, getGuiX() + MAP_BORDER_WIDTH + mapWidth, getGuiY() + MAP_BORDER_HEIGHT + mapHeight);

		renderTiles(AtlasPainter.gui(context), MAX_LIGHT, state.is(DELETING_MARKER) ? 0x80FFFFFF : 0xFFFFFFFF);

		// Overlay the frame so that edges of the map are smooth (skipped while
		// exporting — its vignette would stripe the stitched cells):
		if (!isExporting()) {
			if (fullscreen) {
				int left_width = bookWidth / 2 - 15;
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_FULLSCREEN, getGuiX(), getGuiY(), left_width, bookHeight);
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_FULLSCREEN_M, getGuiX() + left_width, getGuiY(), 29, bookHeight);
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_FULLSCREEN_R, getGuiX() + left_width + 29, getGuiY(), left_width + 1, bookHeight);
			} else {
				context.drawTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME, getGuiX(), getGuiY(), 0, 0, bookWidth, bookHeight, bookWidth, bookHeight);
			}
		}
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(getGuiX(), getGuiY());
		float markerScale = getEffectiveScale() * (tilePixels / 16.0F);

		Map<UUID, PlayerSummary> friends = RoleplayersAtlas.getOrderedFriends();

		for (Identifier id : overlays.keySet()) {
			overlays.get(id).onScreenRender(new AtlasOverlay.AtlasScreenRenderContext(this, context, mouseX, mouseY, markerScale, friends));
		}

		hoveredLandmark = null;
		hoveredFriend = null;
		// Nothing on the map is hovered while a window is up. The dimmed backdrop
		// is not a surface — reaching through it lit up marks underneath and threw
		// their tooltips over the top of the window that was meant to be in front.
		if (!state.is(HIDING_MARKERS) && openModal() == null) {
			if (isMouseOverMap) {
				double bestDistance = Double.MAX_VALUE;
				for (Map.Entry<Landmark, MarkerTexture> entry : worldAtlasData.getAllMarkers(tileChunks).entrySet()) {
					Landmark landmark = entry.getKey();
					if (!RoleplayersAtlas.layerVisible(landmark)) continue;
					MarkerTexture texture = entry.getValue();
					BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
					if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) {
						// Routes hover anywhere along the path (or its name just above
						// the line), so the eraser and right-click work on the line.
						java.util.List<BlockPos> routePoints = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
						if (routePoints != null && routePoints.size() >= 2) {
							double threshold = 12 * Math.max(1, getEffectiveScale());
							for (int i = 0; i < routePoints.size() - 1; i++) {
								double ax = worldXToScreenX(routePoints.get(i).getX() + 0.5);
								double ay = worldZToScreenY(routePoints.get(i).getZ() + 0.5);
								double bx = worldXToScreenX(routePoints.get(i + 1).getX() + 0.5);
								double by = worldZToScreenY(routePoints.get(i + 1).getZ() + 0.5);
								double distance = distToSegment(mouseX, mouseY, ax, ay, bx, by);
								if (distance < threshold && distance * distance < bestDistance) {
									bestDistance = distance * distance;
									hoveredLandmark = landmark;
								}
							}
						}
						continue;
					}
					if (pos == null) {
						Set<ChunkPos> chunks = RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
						for (ChunkPos chunk : chunks) {
							double screenX = worldXToScreenX(chunk.getStartX());
							double screenEndX = worldXToScreenX(chunk.getStartX() + 16);
							double screenY = worldZToScreenY(chunk.getStartZ());
							double screenEndY = worldZToScreenY(chunk.getStartZ() + 16);
							boolean isInside = mouseX >= screenX && mouseX < screenEndX && mouseY >= screenY && mouseY < screenEndY;
							if (isInside && 10 < bestDistance) {
								hoveredLandmark = landmark;
								bestDistance = 10;
							}
						}
					} else {
						double markerX = worldXToScreenX(pos.getX());
						double markerY = worldZToScreenY(pos.getZ());
						Vector2d markerCenter = texture.getCenter(tileChunks);
						double squaredDistance = Vector2d.distanceSquared(markerX + markerScale * markerCenter.x, markerY + markerScale * markerCenter.y, mouseX, mouseY);
						if (squaredDistance > 0 && squaredDistance < bestDistance && squaredDistance < (texture.getSquaredSize(tileChunks) * markerScale * markerScale) / 4.0) {
							bestDistance = squaredDistance;
							hoveredLandmark = landmark;
						}
					}
				}
				for (Map.Entry<UUID, PlayerSummary> entry : friends.entrySet()) {
					UUID uuid = entry.getKey();
					PlayerSummary friend = entry.getValue();

					boolean self = uuid.equals(SurveyorClient.getClientUuid());
					boolean inDim = friend.dimension().equals(dim);
					if (!self && !inDim) continue;
					double dimX = friend.pos().getX();
					double dimZ = friend.pos().getZ();

					if (!dim.equals(friend.dimension())) {
						Map<RegistryKey<World>, Integer> scales = RoleplayersAtlas.CONFIG.dimensions.getScales(MinecraftClient.getInstance().getNetworkHandler());
						int newScale = scales.getOrDefault(dim(), 0);
						int oldScale = scales.getOrDefault(friend.dimension(), 0);
						if (newScale * oldScale == 0) continue; // no ratio!
						double mult = newScale / (double) oldScale;
						dimX = mult * dimX;
						dimZ = mult * dimZ;
					}

					double markerX = worldXToScreenX(dimX);
					double markerY = worldZToScreenY(dimZ);
					double squaredDistance = Vector2d.distanceSquared(markerX, markerY, mouseX, mouseY);
					if (squaredDistance > 0 && squaredDistance < bestDistance && squaredDistance < (PLAYER_ICON_HEIGHT * PLAYER_ICON_WIDTH * 1.5) / 4.0) {
						bestDistance = squaredDistance;
						hoveredFriend = friend;
						hoveredLandmark = null;
					}
				}
			}
			// The hearth is held back and drawn after everything else: markers
			// come out of a hash map in no fixed order, and sharing a layer with
			// a route made the two trade places from frame to frame.
			Landmark spawnMark = null;
			MarkerTexture spawnTexture = null;
			for (Map.Entry<Landmark, MarkerTexture> entry : worldAtlasData.getAllMarkers(tileChunks).entrySet()) {
				if (glam.ardor.roleplayers_atlas.SpawnMarker.is(entry.getKey())) {
					spawnMark = entry.getKey();
					spawnTexture = entry.getValue();
				}
			}
			worldAtlasData.getAllMarkers(tileChunks).forEach((landmark, texture) -> {
				if (glam.ardor.roleplayers_atlas.SpawnMarker.is(landmark)) return;
				// The road being lengthened is hidden while it is: the preview
				// already draws the whole of it, and two copies of the same path
				// laid over each other read as a zigzag.
				if (extendingRoute != null && landmark.id().equals(extendingRoute.id()) && landmark.owner().equals(extendingRoute.owner())) return;
				// Likewise the zone being reshaped: the brush already shows its
				// whole outline, and the saved copy under it only muddies the edge.
				if (editingTerritory != null && landmark.id().equals(editingTerritory.id()) && landmark.owner().equals(editingTerritory.owner())) return;
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				// Categories excluded from a pending snapshot are hidden while it captures.
				if (!glam.ardor.roleplayers_atlas.ParchmentExport.visibleForCapture(landmark)) return;
				boolean hovering = hoveredLandmark == landmark && markerModal.getParent() == null;
				boolean editable = !landmark.owner().equals(WorldLandmarks.GLOBAL) && SurveyorClient.canModify(landmark.owner());
				BiFunction<Double, Double, Float> alpha = (x, y) -> state.is(PLACING_MARKER) || (state.is(DELETING_MARKER) && !editable) || (hovering && x <= MAP_BORDER_WIDTH || x >= mapWidth + MAP_BORDER_WIDTH || y <= MAP_BORDER_HEIGHT || y >= mapHeight + MAP_BORDER_HEIGHT) ? 0.5f : 1.0f;
				// Not pinned: markers outside the visible map are scissored away
				// instead of piling up along the page edges.
				renderMarker(AtlasPainter.gui(context), landmark, texture, 0, MAX_LIGHT, alpha, false, hovering, markerScale);
				// At 8c and further markers crowd together, so labels only show
				// for the hovered marker.
				if (tileChunks < 8 || hovering) {
					renderMarkerLabel(AtlasPainter.gui(context), landmark, state.is(PLACING_MARKER) ? 0.5F : 1.0F, MAX_LIGHT, markerScale);
				}
				if (!landmark.contains(LandmarkComponentTypes.POS) && landmark.contains(LandmarkComponentTypes.CHUNKS)) {
					renderTerritoryLabel(AtlasPainter.gui(context), landmark, state.is(PLACING_MARKER) ? 0.5F : 1.0F, MAX_LIGHT);
				}
				if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) {
					renderRouteLabel(AtlasPainter.gui(context), landmark, state.is(PLACING_MARKER) ? 0.5F : 1.0F, MAX_LIGHT);
				}
			});
			if (spawnMark != null && glam.ardor.roleplayers_atlas.ParchmentExport.visibleForCapture(spawnMark)) {
				boolean hovering = hoveredLandmark == spawnMark && markerModal.getParent() == null;
				BiFunction<Double, Double, Float> alpha = (x, y) -> state.is(PLACING_MARKER) ? 0.5f : 1.0f;
				renderMarker(AtlasPainter.gui(context), spawnMark, spawnTexture, 0, MAX_LIGHT, alpha, false, hovering, markerScale);
				if (tileChunks < 8 || hovering) {
					renderMarkerLabel(AtlasPainter.gui(context), spawnMark, state.is(PLACING_MARKER) ? 0.5F : 1.0F, MAX_LIGHT, markerScale);
				}
			}
		}

		if (state.is(PLACING_ROUTE) && !pendingRoute.isEmpty()) {
			// The path so far is drawn the way it will be kept — curved. Drawing
			// the preview as a broken line instead put two different paths
			// through the same points, which reads as a zigzag.
			renderRoutePath(AtlasPainter.gui(context), pendingRoute, 0, MAX_LIGHT, 0.9F, 0x703A14, false);
			if (isMouseOverMap) {
				// The reach to the cursor stays straight and faint: it is where
				// the next point would go, not a piece of road yet.
				renderRoutePath(AtlasPainter.gui(context), java.util.List.of(
					pendingRoute.get(pendingRoute.size() - 1),
					new BlockPos(screenXToWorldX(mouseX), 0, screenYToWorldZ(mouseY))
				), 0, MAX_LIGHT, 0.5F, 0x703A14, true);
			}
		}

		if (state.is(PLACING_TERRITORY)) {
			for (ChunkPos chunk : pendingTerritory) {
				int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
				int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
				int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
				int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
				context.fill(x1, y1, x2, y2, 0x66E8C878);
				// Same border thickness as saved territories, thinning as the map zooms out.
				int lineSize = tilePixels / 16;
				int borderArgb = 0xE0703A14;
				if (lineSize > 0) {
					if (!pendingTerritory.contains(new ChunkPos(chunk.x - 1, chunk.z))) context.fill(x1, y1, x1 + lineSize, y2, borderArgb);
					if (!pendingTerritory.contains(new ChunkPos(chunk.x + 1, chunk.z))) context.fill(x2 - lineSize, y1, x2, y2, borderArgb);
					if (!pendingTerritory.contains(new ChunkPos(chunk.x, chunk.z - 1))) context.fill(x1, y1, x2, y1 + lineSize, borderArgb);
					if (!pendingTerritory.contains(new ChunkPos(chunk.x, chunk.z + 1))) context.fill(x1, y2 - lineSize, x2, y2, borderArgb);
				}
			}
		}

		// While the biome tool is out, everything already painted is outlined in
		// green — otherwise a correction made weeks ago is impossible to find
		// again, and undoing it means remembering where it was.
		if (state.is(PICKING_BIOME) || state.is(PAINTING_BIOME)) {
			int lineSize = Math.max(1, tilePixels / 16);
			// What came in on someone else's scroll is outlined violet instead, so
			// it is plain at a glance which corrections are yours to undo here.
			for (ChunkPos chunk : glam.ardor.roleplayers_atlas.BiomeOverrides.importedChunks(dim)) {
				if (pendingPatch.contains(chunk)) continue;
				int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
				int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
				int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
				int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
				if (x2 < 0 || y2 < 0 || x1 > bookWidth || y1 > bookHeight) continue;
				context.fill(x1, y1, x2, y2, 0x229B6EC4);
				context.fill(x1, y1, x1 + lineSize, y2, 0xB07A55A0);
				context.fill(x2 - lineSize, y1, x2, y2, 0xB07A55A0);
				context.fill(x1, y1, x2, y1 + lineSize, 0xB07A55A0);
				context.fill(x1, y2 - lineSize, x2, y2, 0xB07A55A0);
			}
			for (ChunkPos chunk : glam.ardor.roleplayers_atlas.BiomeOverrides.patchedChunks(dim)) {
				if (pendingPatch.contains(chunk)) continue;
				int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
				int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
				int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
				int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
				if (x2 < 0 || y2 < 0 || x1 > bookWidth || y1 > bookHeight) continue;
				context.fill(x1, y1, x2, y2, 0x2255C46E);
				context.fill(x1, y1, x1 + lineSize, y2, 0xB03E8C55);
				context.fill(x2 - lineSize, y1, x2, y2, 0xB03E8C55);
				context.fill(x1, y1, x2, y1 + lineSize, 0xB03E8C55);
				context.fill(x1, y2 - lineSize, x2, y2, 0xB03E8C55);
			}
		}

		// The patch marked out so far, in the same blue as the cursor ring.
		if (state.is(PAINTING_BIOME)) {
			int lineSize = Math.max(1, tilePixels / 16);
			for (ChunkPos chunk : pendingPatch) {
				int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
				int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
				int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
				int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
				context.fill(x1, y1, x2, y2, 0x669BD1E8);
				if (!pendingPatch.contains(new ChunkPos(chunk.x - 1, chunk.z))) context.fill(x1, y1, x1 + lineSize, y2, 0xFF2E6B8A);
				if (!pendingPatch.contains(new ChunkPos(chunk.x + 1, chunk.z))) context.fill(x2 - lineSize, y1, x2, y2, 0xFF2E6B8A);
				if (!pendingPatch.contains(new ChunkPos(chunk.x, chunk.z - 1))) context.fill(x1, y1, x2, y1 + lineSize, 0xFF2E6B8A);
				if (!pendingPatch.contains(new ChunkPos(chunk.x, chunk.z + 1))) context.fill(x1, y2 - lineSize, x2, y2, 0xFF2E6B8A);
			}
		}

		// The town tool shows what has already been drawn — yours outlined amber,
		// what came in on a scroll violet — and the cells marked out so far.
		if (state.is(PAINTING_CITY)) {
			int lineSize = Math.max(1, tilePixels / 16);
			outlineCells(context, glam.ardor.roleplayers_atlas.CityPaint.importedChunks(dim), lineSize, 0x229B6EC4, 0xB07A55A0);
			outlineCells(context, glam.ardor.roleplayers_atlas.CityPaint.ownChunks(dim), lineSize, 0x22E8A83C, 0xB0B07A1E);
		}

		// Anything drawn chunk by chunk shows the cell under the cursor, so it is
		// plain where the next stroke would land.
		if ((state.is(PICKING_BIOME) || state.is(PAINTING_BIOME) || state.is(PLACING_TERRITORY) || state.is(PAINTING_CITY)) && isMouseOverMap) {
			ChunkPos chunk = new ChunkPos(screenXToWorldX(mouseX) >> 4, screenYToWorldZ(mouseY) >> 4);
			int x1 = (int) (worldXToScreenX(chunk.getStartX()) - getGuiX());
			int y1 = (int) (worldZToScreenY(chunk.getStartZ()) - getGuiY());
			int x2 = (int) (worldXToScreenX(chunk.getStartX() + 16) - getGuiX());
			int y2 = (int) (worldZToScreenY(chunk.getStartZ() + 16) - getGuiY());
			int ink = state.is(PLACING_TERRITORY) ? 0xFF703A14 : state.is(PAINTING_CITY) ? 0xFF8A4E14 : 0xFF2E6B8A;
			context.fill(x1, y1, x2, y2, state.is(PLACING_TERRITORY) ? 0x33E8C878 : state.is(PAINTING_CITY) ? 0x33E8A83C : 0x449BD1E8);
			int lineSize = Math.max(1, tilePixels / 16);
			context.fill(x1, y1, x1 + lineSize, y2, ink);
			context.fill(x2 - lineSize, y1, x2, y2, ink);
			context.fill(x1, y1, x2, y1 + lineSize, ink);
			context.fill(x1, y2 - lineSize, x2, y2, ink);
		}

		// Drawing modes have no obvious end, so the way out is written down.
		String hintKey = state.is(PAINTING_CITY) ? "gui.roleplayers_atlas.city.hint"
			: state.is(PAINTING_BIOME) ? "gui.roleplayers_atlas.biomes.patchHint"
			: state.is(PLACING_TERRITORY) ? (editingTerritory != null ? "gui.roleplayers_atlas.territoryEditHint" : "gui.roleplayers_atlas.territoryHint")
			: state.is(PLACING_ROUTE) ? "gui.roleplayers_atlas.routeHint"
			: state.is(PICKING_BIOME) ? "gui.roleplayers_atlas.biomes.pickHint"
			: state.is(PLACING_MARKER) ? "gui.roleplayers_atlas.markerHint"
			: state.is(PLACING_LABEL) ? "gui.roleplayers_atlas.labelHint"
			: state.is(DELETING_MARKER) ? "gui.roleplayers_atlas.eraseHint"
			: null;
		// Wrapped to the page: on a small window a single line ran off both
		// edges, taking the half that mattered with it.
		int room = mapWidth - 16;
		java.util.List<net.minecraft.text.OrderedText> lines = new java.util.ArrayList<>();
		// What was just taken back, said once and then gone. Shown whether or not
		// the controls are: it is news, not a reminder.
		if (flashText != null && net.minecraft.util.Util.getMeasuringTimeMs() < flashUntil) {
			lines.addAll(textRenderer.wrapLines(flashText, room));
		}
		// Holding ctrl over the map says where that click would send you — and
		// says nothing at all where travel isn't allowed.
		if (state.is(NORMAL) && isMouseOverMap && RoleplayersAtlas.CONFIG.showHints
			&& hasControlDown()
			&& glam.ardor.roleplayers_atlas.MapTeleport.allowed()) {
			lines.addAll(textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.teleport.hint",
				screenXToWorldX(mouseX), screenYToWorldZ(mouseY)).formatted(net.minecraft.util.Formatting.GOLD), room));
		}
		if (hintKey != null && RoleplayersAtlas.CONFIG.showHints) {
			// Undoing past where the road already ended is worth saying out loud:
			// those points were drawn on another day and are being taken away.
			if (extendingRoute != null && pendingRoute.size() < extendFloor) {
				lines.addAll(textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.route.eatingOriginal", extendFloor - pendingRoute.size()).formatted(net.minecraft.util.Formatting.GOLD), room));
			}
			// The brush has to say what it is holding: every other tool draws the
			// same thing every time, this one doesn't.
			if (state.is(PAINTING_CITY)) {
				lines.addAll(textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.city.held",
					heldCityPiece == null
						? Text.translatable("gui.roleplayers_atlas.city.heldNone")
						: CityModal.pieceName(heldCityPiece)).formatted(net.minecraft.util.Formatting.GOLD), room));
			}
			// A zone rubbed out to nothing is about to be deleted rather than saved.
			if (editingTerritory != null && pendingTerritory.isEmpty() && editFloor > 0) {
				lines.addAll(textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.territory.erasedAll").formatted(net.minecraft.util.Formatting.GOLD), room));
			}
			lines.addAll(textRenderer.wrapLines(Text.translatable(hintKey), room));
		}
		if (!lines.isEmpty()) {
			int lineH = textRenderer.fontHeight + 1;
			int boxH = lines.size() * lineH + 5;
			int boxTop = mapHeight + MAP_BORDER_HEIGHT - 3 - boxH;
			int widest = 0;
			for (net.minecraft.text.OrderedText line : lines) widest = Math.max(widest, textRenderer.getWidth(line));
			int boxX = (bookWidth - widest) / 2;
			context.fill(boxX - 4, boxTop, boxX + widest + 4, boxTop + boxH, 0xAA1A1208);
			int lineY = boxTop + 3;
			for (net.minecraft.text.OrderedText line : lines) {
				context.drawText(textRenderer, line, (bookWidth - textRenderer.getWidth(line)) / 2, lineY, 0xFFF0E4C8, true);
				lineY += lineH;
			}
		}

		context.getMatrices().popMatrix();

		context.disableScissor();

		if (fullscreen) {
			int left_width = bookWidth / 2 - 15;
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_NARROW_FULLSCREEN, getGuiX(), getGuiY(), left_width, bookHeight);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_NARROW_FULLSCREEN_M, getGuiX() + left_width, getGuiY(), 29, bookHeight);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_NARROW_FULLSCREEN_R, getGuiX() + left_width + 29, getGuiY(), left_width + 1, bookHeight);
		} else {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, BOOK_FRAME_NARROW, getGuiX(), getGuiY(), 0, 0, bookWidth, bookHeight, bookWidth, bookHeight);
		}

		markerScrollBox.getViewport().setClipped(state.is(HIDING_MARKERS));

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(getGuiX(), getGuiY());
		friends.forEach((uuid, friend) -> {
			if (isExporting()) return; // player arrows stay off the exported map
			boolean self = uuid.equals(SurveyorClient.getClientUuid());
			boolean inDim = friend.dimension().equals(dim);
			if (!self && !inDim) return;
			boolean hovering = hoveredFriend == friend && markerModal.getParent() == null;
			if (state.is(HIDING_MARKERS) && (!playerBookmark.isSelected() || !self)) return;
			renderPlayer(AtlasPainter.gui(context), 0, MAX_LIGHT, friend, getEffectiveScale(), state.is(PLACING_MARKER) ? 0.5F : 1.0F, hovering, self);
		});
		// After the player arrows so quads and distance plates land in the same
		// submission order every frame (interleaving them caused text flicker).
		if (!state.is(HIDING_MARKERS) && !isExporting()) {
			renderGuideArrows(AtlasPainter.gui(context), 0, MAX_LIGHT, state.is(PLACING_MARKER) ? 0.5F : 1.0F, Math.max(1.0F, getEffectiveScale()));
		}
		context.getMatrices().popMatrix();

		if (state.is(PLACING_MARKER)) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, markerModal.selectedTexture.id(), mouseX + markerModal.selectedTexture.offsetX(), mouseY + markerModal.selectedTexture.offsetY(), 0, 0, markerModal.selectedTexture.textureWidth(), markerModal.selectedTexture.textureHeight(), markerModal.selectedTexture.textureWidth(), markerModal.selectedTexture.textureHeight(), 0x80FFFFFF);
		}

		if (state.is(PLACING_LABEL)) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, ICON_ADD_LABEL, mouseX - 8, mouseY - 8, 0, 0, 16, 16, 16, 16, 0x80FFFFFF);
		}

		addMarkerBookmark.setTitle(hasShiftDown() ? TEXT_ADD_MARKER_HERE : TEXT_ADD_MARKER);

		if (worldAtlasData.isLoading()) {
			context.drawText(textRenderer, Text.literal("...").formatted(Formatting.GRAY), getGuiX() + MAP_BORDER_WIDTH + mapWidth - 10, getGuiY() + MAP_BORDER_HEIGHT + mapHeight - 10, 0xFFFFFFFF, true);
		}

		if (hasAltDown() && !isDragging && isMouseOverMap && openModal() == null) {
			int x = screenXToWorldX((int) getMouseX());
			int z = screenYToWorldZ((int) getMouseY());
			ChunkPos pos = new ChunkPos(new BlockPos(x, 0, z));
			context.drawText(textRenderer, Text.literal("%d,%d (%d,%d)".formatted(pos.x, pos.z, x, z)), getGuiX(), getGuiY() - 12, 0xFFFFFFFF, true);
			if (hoveredLandmark != null) {
				MarkerTexture texture = worldAtlasData.getMarkerTexture(hoveredLandmark);
				context.drawText(textRenderer, Text.literal(hoveredLandmark.id().toString()), getGuiX() + bookWidth - textRenderer.getWidth(Text.literal(hoveredLandmark.id().toString())), getGuiY() - 12, 0xFFFFFFFF, true);
				if (texture != null) context.drawText(textRenderer, Text.literal(texture.displayId()), getGuiX() + bookWidth - textRenderer.getWidth(Text.literal(texture.displayId())), getGuiY() + bookHeight, 0xFFFFFFFF, true);
			} else {
				TileTexture texture = worldAtlasData.getTile(pos);
				Identifier providerId = worldAtlasData.getProvider(pos);
				String predicate = worldAtlasData.getTilePredicate(pos);
				if (texture != null) {
					if (predicate != null) context.drawText(textRenderer, Text.literal(predicate), getGuiX() + bookWidth - textRenderer.getWidth(Text.literal(predicate)), getGuiY() - 12, 0xFFFFFFFF, true);
					if (providerId != null) context.drawText(textRenderer, Text.literal(providerId.toString()), getGuiX(), getGuiY() + bookHeight + 14, 0xFFFFFFFF, true);
					context.drawText(textRenderer, Text.literal(texture.displayId()), getGuiX() + bookWidth - textRenderer.getWidth(Text.literal(texture.displayId())), getGuiY() + bookHeight, 0xFFFFFFFF, true);
				}
			}
		}

		if (markerModal.getParent() != null) {
			markerModal.setClipped(true);
			super.render(context, mouseX, mouseY, partialTick);
			markerModal.setClipped(false);
			markerModal.render(context, trueMouseX, trueMouseY, partialTick);
		} else if (shareModal.getParent() != null) {
			shareModal.setClipped(true);
			super.render(context, mouseX, mouseY, partialTick);
			shareModal.setClipped(false);
			shareModal.render(context, trueMouseX, trueMouseY, partialTick);
		} else if (layerModal.getParent() != null) {
			layerModal.setClipped(true);
			super.render(context, mouseX, mouseY, partialTick);
			layerModal.setClipped(false);
			layerModal.render(context, trueMouseX, trueMouseY, partialTick);
		} else if (screenshotModal.getParent() != null) {
			screenshotModal.setClipped(true);
			super.render(context, mouseX, mouseY, partialTick);
			screenshotModal.setClipped(false);
			screenshotModal.render(context, trueMouseX, trueMouseY, partialTick);
		} else {
			super.render(context, mouseX, mouseY, partialTick);
		}

		if (isExporting()) {
			// Progress bar in the top border strip — outside the captured map
			// area, so it never lands in the stitched image.
			int percent = fullExport.totalContent == 0 ? 100 : Math.min(100, fullExport.done * 100 / fullExport.totalContent);
			int barW = 220;
			int barX = this.width / 2 - barW / 2;
			int barY = 3;
			context.fill(barX - 2, barY - 2, barX + barW + 2, barY + 11, 0xCC201409);
			context.fill(barX, barY, barX + barW * percent / 100, barY + 9, 0xFFE0B45E);
			Text progressLabel = Text.translatable("gui.roleplayers_atlas.screenshot.progress", percent);
			context.drawText(textRenderer, progressLabel, this.width / 2 - textRenderer.getWidth(progressLabel) / 2, barY + 1, 0xFF2E1A0C, false);
			fullExport.framesSincePos++;
		} else if (searchOpen) {
			// A small neat grey box right next to the magnifier tab, with the
			// text inside it. Positioned here with live coordinates — at
			// construction time the screen origin is still (0,0).
			int boxX = getGuiX() + 12;
			int boxY = searchTab.getGuiY() + 2;
			searchField.setPosition(boxX + 4, boxY + 3);
			context.fill(boxX, boxY, boxX + 110, boxY + 14, 0xAA4A4640);
			searchField.render(context, mouseX, mouseY, partialTick);
		}

		// Real mouse coords (no matrix translate): the tooltip positioner clamps
		// against the screen using these, so a translated matrix would misplace
		// larger tooltips.
		int tooltipX = (int) getMouseX();
		int tooltipY = (int) getMouseY();
		if (hoveredLandmark != null) {
			// A marker can carry a note without a name — the tooltip follows what
			// there is to show rather than requiring a title.
			java.util.List<Text> tooltip = new ArrayList<>();
			Text name = hoveredLandmark.get(LandmarkComponentTypes.NAME);
			if (name != null && !name.getString().isEmpty()) tooltip.add(name);
			hoveredLandmark.getOrDefault(LandmarkComponentTypes.LORE, new ArrayList<Text>()).forEach(t -> tooltip.add(t.copy().formatted(Formatting.GRAY)));
			String note = hoveredLandmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.NOTE, "");
			for (String chunk : wrapPlain(note, 36)) {
				tooltip.add(Text.literal(chunk).formatted(Formatting.GRAY, Formatting.ITALIC));
			}
			java.util.List<net.minecraft.util.math.BlockPos> hoveredRoute = hoveredLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
			if (hoveredRoute != null && hoveredRoute.size() > 1 && !Boolean.FALSE.equals(hoveredLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DISTANCE))) {
				long blocks = Math.round(glam.ardor.roleplayers_atlas.util.RouteUtil.length(hoveredRoute));
				tooltip.add(Text.translatable("gui.roleplayers_atlas.marker.routeLength", blocks).formatted(Formatting.GRAY));
			}
			tooltip.addAll(dateLines(hoveredLandmark));
			// Said only where it is true: on a server that grants nobody the
			// command, an invitation to travel would be a lie.
			if (state.is(NORMAL) && glam.ardor.roleplayers_atlas.MapTeleport.allowed() && glam.ardor.roleplayers_atlas.MapTeleport.targetOf(hoveredLandmark) != null) {
				tooltip.add(Text.translatable("gui.roleplayers_atlas.teleport.markerHint").formatted(Formatting.DARK_GRAY));
			}
			if (!tooltip.isEmpty()) context.drawTooltip(textRenderer, tooltip, tooltipX, tooltipY);
		} else if (hoveredFriend != null) {
			boolean self = hoveredFriend.username().equals(MinecraftClient.getInstance().player.getGameProfile().getName());
			boolean inDim = hoveredFriend.dimension().equals(dim);
			if (self && inDim) return;
			context.drawTooltip(textRenderer, (self ? Text.translatable("gui.roleplayers_atlas.followPlayer") : Text.literal(hoveredFriend.username())).formatted(hoveredFriend.online() ? (self ? Formatting.WHITE : Formatting.LIGHT_PURPLE) : Formatting.GRAY), tooltipX, tooltipY);
		}
	}

	@Override
	public double guiScale() {
		return MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	@Override
	public RegistryKey<World> dim() {
		return dim;
	}

	@Override
	public void close() {
		// Strokes of the town brush live in memory until the hand comes off the
		// button; shutting the book with the keybind never released one, so the
		// last drag could go unwritten.
		glam.ardor.roleplayers_atlas.CityPaint.flush();
		super.close();
		markerModal.closeChild();
		shareModal.closeChild();
		layerModal.closeChild();
		screenshotModal.closeChild();
		if (cityModal.getParent() != null) cityModal.closeChild();
		if (biomeModal.getParent() != null) biomeModal.closeChild();
		removeChild(markerCursor);
	}

	@Override
	public void onChildClosed(Component child) {
		if (child.equals(markerModal)) {
			removeChild(markerCursor);
		}
		if (child.equals(layerModal)) {
			rebuildLayerTabs();
			updateBookmarkerList();
		}
	}

	float getEffectiveScale() {
		return (float) (mapScale() / guiScale());
	}

	@Override
	public double getPixelsPerBlock() {
		return (double) getEffectiveScale() * ((double) tilePixels()) / ((double) tileChunks() * 16.0);
	}

	@Override
	public int bookX() {
		return getGuiX();
	}

	@Override
	public int bookY() {
		return getGuiY();
	}

	@Override
	public int bookHeight() {
		return bookHeight;
	}

	@Override
	public int mapWidth() {
		return mapWidth;
	}

	@Override
	public int mapHeight() {
		return mapHeight;
	}

	@Override
	public double mapOffsetX() {
		return mapOffsetX;
	}

	@Override
	public double mapOffsetY() {
		return mapOffsetY;
	}

	@Override
	public int mapScale() {
		return mapScale;
	}

	@Override
	public int tilePixels() {
		return tilePixels;
	}

	@Override
	public int tileChunks() {
		return tileChunks;
	}

	@Override
	public PlayerEntity player() {
		return player;
	}

	@Override
	public int bookWidth() {
		return bookWidth;
	}

	@Override
	public WorldAtlasData worldAtlasData() {
		return worldAtlasData;
	}

	/**
	 * How many tools fit down the right edge without reaching the hearth and
	 * player buttons pinned at the bottom. Room is left below for the scroll
	 * arrow, so when there is one it doesn't sit on top of them.
	 */
	private int sideButtonRows() {
		int floor = bookHeight - MAP_BORDER_HEIGHT - BookmarkButton.HEIGHT * 2 - 11;
		int room = floor - SIDE_BUTTONS_TOP - ScrollBoxComponent.ARROW_SIZE;
		return Math.max(1, Math.min(SIDE_BUTTON_COUNT, room / SIDE_BUTTON_ROW));
	}
}
