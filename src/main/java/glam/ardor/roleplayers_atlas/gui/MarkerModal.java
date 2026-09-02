package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.MarkerTexture;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.gui.core.ToggleButtonRadioGroup;
import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import glam.ardor.roleplayers_atlas.util.ColorUtil;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This GUI is used select marker icon and enter a label.
 * When the user clicks on the confirmation button, the call to MarkerAPI is made.
 *
 * @author Hunternif
 */
public class MarkerModal extends Component {
	protected WorldSummary summary;
	protected DynamicRegistryManager manager;
	protected Landmark baseLandmark = null;

	protected MarkerTexture selectedTexture = MarkerTexture.DEFAULT;
	protected DyeColor selectedColor = DyeColor.WHITE;
	protected boolean zoneTitleEnabled = true;
	protected int zoneRadius = 32;
	protected int markerOpacity = 100;
	protected boolean hideLabel = false;

	protected ButtonWidget btnZoneTitle;
	protected ButtonWidget btnHideLabel;
	protected ButtonWidget btnLayer;
	protected ButtonWidget btnDate;
	protected ButtonWidget btnDistance;
	protected boolean dateEnabled = true;
	protected boolean showDistance = true;
	protected String markerLayer = "personal";
	protected net.minecraft.client.gui.widget.SliderWidget radiusSlider;
	protected net.minecraft.client.gui.widget.SliderWidget opacitySlider;
	protected int labelRotation = 0;
	protected boolean labelNoShadow = false;
	protected net.minecraft.client.gui.widget.SliderWidget rotationSlider;
	protected ButtonWidget btnShadow;

	public static final int BUTTON_WIDTH = 120;
	public static final int BUTTON_SPACING = 8;

	public static final int TYPE_SPACING = 1;

	protected ButtonWidget btnDone;
	protected ButtonWidget btnCancel;
	protected ButtonWidget btnExtend;
	protected ButtonWidget btnEditArea;
	protected TextFieldWidget textField;
	protected String noteText = "";
	protected boolean noteFocused = false;
	protected static final int NOTE_W = BUTTON_WIDTH * 2 + BUTTON_SPACING;
	protected static final int NOTE_H = 60;
	protected ScrollBoxComponent textureScrollBox;
	protected ToggleButtonRadioGroup<TexturePreviewButton<MarkerTexture>> textureRadioGroup;
	protected ScrollBoxComponent colorScrollBox;
	protected ToggleButtonRadioGroup<TexturePreviewButton<DyeColor>> colorRadioGroup;
	protected Map<MarkerTexture, TexturePreviewButton<MarkerTexture>> textureButtons = new LinkedHashMap<>();
	protected Map<DyeColor, TexturePreviewButton<DyeColor>> colorButtons = new LinkedHashMap<>();

	protected final List<IMarkerTypeSelectListener> markerListeners = new ArrayList<>();

	public MarkerModal() {
	}

	void setMarkerData(WorldSummary summary, DynamicRegistryManager manager, Landmark baseLandmark) {
		this.summary = summary;
		this.manager = manager;
		this.baseLandmark = baseLandmark;
		this.selectedColor = Arrays.stream(DyeColor.values()).filter(d -> baseLandmark.contains(LandmarkComponentTypes.COLOR) && d.getEntityColor() == baseLandmark.get(LandmarkComponentTypes.COLOR)).findAny().orElse(DyeColor.WHITE);
		this.selectedTexture = MarkerTextures.getInstance().fromLandmark(baseLandmark);
		this.zoneTitleEnabled = !Boolean.FALSE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ZONE_TITLE));
		Integer storedRadius = baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ZONE_RADIUS);
		this.zoneRadius = storedRadius != null ? storedRadius : glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.zoneTitleRadius;
		Integer storedOpacity = baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.OPACITY);
		this.markerOpacity = storedOpacity != null ? storedOpacity : 100;
		this.hideLabel = Boolean.TRUE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.HIDE_LABEL));
		this.markerLayer = baseLandmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.LAYER, "personal");
		this.noteText = baseLandmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.NOTE, "");
		this.dateEnabled = !Boolean.FALSE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DATE));
		this.showDistance = !Boolean.FALSE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DISTANCE));
		this.labelRotation = baseLandmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_ROTATION, 0);
		this.labelNoShadow = Boolean.TRUE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW));
		this.noteFocused = false;
		if (isSpawn()) {
			selectedTexture = glam.ardor.roleplayers_atlas.SpawnMarker.texture();
			selectedColor = glam.ardor.roleplayers_atlas.SpawnMarker.colorOf();
		} else if (!isTerritory() && !isSimple() && !selectedTexture.keyId().getPath().startsWith("custom/")) {
			selectedTexture = textureButtons.keySet().stream().findFirst().orElse(MarkerTexture.DEFAULT);
		}
		if (colorRadioGroup != null) updateSelected();
	}

	private Text zoneTitleText() {
		return Text.translatable("gui.roleplayers_atlas.marker.zoneTitle", Text.translatable(zoneTitleEnabled ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	/** Marks that carry a map label get an extra row (rotation/shadow), so everything below shifts down. */
	private boolean hasLabelControls() {
		return isTerritory() || isSimple();
	}

	/** Where the confirm row sits — the spawn mark's dialog is much shorter. */
	private int bottomY() {
		return isSpawn() ? 78 : isSimple() ? 171 : isTerritory() ? 205 : 182;
	}

	/** The layer/date row — pushed down by one row where the label controls exist. */
	private int labelRowY() {
		return this.height / 2 + (isSimple() ? 81 : isTerritory() ? 115 : 92);
	}

	/**
	 * The confirm row, but never below the screen: at a normal GUI scale it sits
	 * center-relative as before, and only clamps up to the bottom edge when the
	 * dialog would otherwise run off (high GUI scale / small window), so "Готово"
	 * and "Изменить границы" stay on screen instead of being cut off.
	 */
	private int confirmRowY() {
		return Math.min(this.height / 2 + bottomY(), this.height - 46);
	}

	private int noteBoxY() {
		return this.height / 2 + (isSimple() ? 105 : isTerritory() ? 139 : 116);
	}

	private int noteBoxX() {
		return this.width / 2 - NOTE_W / 2;
	}

	/** Pixel-accurate word wrap; words wider than the box are hard-split. */
	private java.util.List<String> wrapNote(String text, int maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			while (textRenderer.getWidth(word) > maxWidth) {
				if (!line.isEmpty()) {
					lines.add(line.toString());
					line.setLength(0);
				}
				int cut = 1;
				while (cut < word.length() && textRenderer.getWidth(word.substring(0, cut + 1)) <= maxWidth) cut++;
				lines.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (textRenderer.getWidth(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	/** Multiline note box: vanilla-textfield look, word-wrapped content, blinking cursor. */
	private void renderNoteBox(DrawContext context) {
		int x = noteBoxX();
		int y = noteBoxY();
		context.fill(x - 1, y - 1, x + NOTE_W + 1, y + NOTE_H + 1, noteFocused ? 0xFFFFFFFF : 0xFFA0A0A0);
		context.fill(x, y, x + NOTE_W, y + NOTE_H, 0xFF000000);
		if (noteText.isEmpty() && !noteFocused) {
			context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.marker.note"), x + 4, y + 4, 0xFF707070, false);
			return;
		}
		java.util.List<String> lines = wrapNote(noteText, NOTE_W - 10);
		if (lines.isEmpty()) lines = new java.util.ArrayList<>(java.util.List.of(""));
		int maxLines = (NOTE_H - 8) / 9;
		int start = Math.max(0, lines.size() - maxLines);
		boolean cursorBlink = noteFocused && (net.minecraft.util.Util.getMeasuringTimeMs() / 400) % 2 == 0;
		for (int i = start; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean last = i == lines.size() - 1;
			context.drawText(textRenderer, Text.literal(last && cursorBlink ? line + "_" : line), x + 4, y + 4 + (i - start) * 9, 0xFFE0E0E0, false);
		}
	}

	private boolean isOverNoteBox(double mouseX, double mouseY) {
		return mouseX >= noteBoxX() && mouseX < noteBoxX() + NOTE_W && mouseY >= noteBoxY() && mouseY < noteBoxY() + NOTE_H;
	}

	private Text layerText() {
		glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer = glam.ardor.roleplayers_atlas.MarkerLayers.get(markerLayer);
		return Text.translatable("gui.roleplayers_atlas.marker.layer", layer != null ? layer.name() : markerLayer);
	}

	private Text hideLabelText() {
		return Text.translatable("gui.roleplayers_atlas.marker.hideLabel", Text.translatable(hideLabel ? "gui.roleplayers_atlas.marker.hideLabel.hidden" : "gui.roleplayers_atlas.marker.hideLabel.shown"));
	}

	private static Text onOff(String key, boolean value) {
		return Text.translatable(key, Text.translatable(value ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	/** "Тень: ВКЛ/ВЫКЛ" — the label's shadow (or a route's backing ribbon). */
	private Text shadowText() {
		return onOff("gui.roleplayers_atlas.marker.shadow", !labelNoShadow);
	}

	private class RadiusSlider extends net.minecraft.client.gui.widget.SliderWidget {
		RadiusSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(), (Math.max(4, Math.min(256, zoneRadius)) - 4) / 252.0);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("gui.roleplayers_atlas.marker.zoneRadius", zoneRadius));
		}

		@Override
		protected void applyValue() {
			zoneRadius = 4 + (int) Math.round(value * 252.0);
			updateMessage();
		}
	}

	private class OpacitySlider extends net.minecraft.client.gui.widget.SliderWidget {
		OpacitySlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(), Math.max(0, Math.min(100, markerOpacity)) / 100.0);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("gui.roleplayers_atlas.marker.opacity", markerOpacity));
		}

		@Override
		protected void applyValue() {
			markerOpacity = (int) Math.round(value * 100.0);
			updateMessage();
		}
	}

	private class RotationSlider extends net.minecraft.client.gui.widget.SliderWidget {
		RotationSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(), ((labelRotation % 360) + 360) % 360 / 360.0);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("gui.roleplayers_atlas.marker.rotation", ((labelRotation % 360) + 360) % 360));
		}

		@Override
		protected void applyValue() {
			labelRotation = ((int) Math.round(value * 360.0)) % 360;
			updateMessage();
		}
	}

	void addMarkerListener(IMarkerTypeSelectListener listener) {
		markerListeners.add(listener);
	}

	/** Territories have no icon on the map (always the colored diamond), so the texture selector is hidden for them. */
	protected boolean isTerritory() {
		return baseLandmark != null && baseLandmark.contains(LandmarkComponentTypes.CHUNKS) && !baseLandmark.contains(LandmarkComponentTypes.POS);
	}

	/** Pen inscriptions are text-only, so the texture selector is hidden for them too. */
	protected boolean isPenLabel() {
		return baseLandmark != null && Boolean.TRUE.equals(baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL));
	}

	/** Routes render as a dashed path — no icon, no zone settings. */
	protected boolean isRoute() {
		return baseLandmark != null && baseLandmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
	}

	/**
	 * The respawn mark. It isn't a landmark anyone owns, so there is nothing to
	 * name, file into a layer, annotate or date — only how it looks is the
	 * player's to choose, and that lives in the config.
	 */
	protected boolean isSpawn() {
		return glam.ardor.roleplayers_atlas.SpawnMarker.is(baseLandmark);
	}

	/** Simplified modal (name/color/opacity/layer only) for inscriptions and routes. */
	protected boolean isSimple() {
		return isPenLabel() || isRoute();
	}

	protected void updateSelected() {
		colorRadioGroup.setSelectedButton(colorButtons.get(selectedColor));
		if (textureRadioGroup != null) textureRadioGroup.setSelectedButton(textureButtons.get(selectedTexture));
	}

	@Override
	public void init() { // set up in here because it scales to parent size
		removeAllChildren();
		super.init();

		addDrawableChild(btnDone = ButtonWidget.builder(Text.translatable("gui.done"), (button) -> {
			if (isSpawn()) {
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerIcon = selectedTexture.keyId().getPath().replaceFirst("^custom/", "");
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerColor = selectedColor.getId();
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerOpacity = markerOpacity;
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.setAndSave("spawnMarkerIcon", glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerIcon);
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.setAndSave("spawnMarkerColor", glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerColor);
				glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.setAndSave("spawnMarkerOpacity", glam.ardor.roleplayers_atlas.RoleplayersAtlas.CONFIG.spawnMarkerOpacity);
				((AtlasScreen) getParent()).updateBookmarkerList();
				closeChild();
				return;
			}
			if (isPenLabel() && textField.getText().isBlank()) return;
			MutableText label = Text.literal(textField.getText());
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks != null) {
				// What was there before this dialog opened, so the whole edit — or
				// the whole creation — can be taken back in one step.
				Landmark previous = landmarks.contains(baseLandmark.owner(), baseLandmark.id()) ? baseLandmark : null;
				boolean territory = baseLandmark.contains(LandmarkComponentTypes.CHUNKS) && !baseLandmark.contains(LandmarkComponentTypes.POS);
				boolean penLabel = isPenLabel();
				boolean route = isRoute();
				boolean simple = penLabel || route;
				net.minecraft.util.math.ColumnPos territoryCenter = territory ? glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(baseLandmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new java.util.HashMap<>())) : null;
				Landmark written = WorldAtlasData.copyLandmarkWith(
					baseLandmark,
					territory
						? glam.ardor.roleplayers_atlas.RoleplayersAtlas.id("territory/" + territoryCenter.x() + "/" + territoryCenter.z() + "/" + Integer.toHexString(label.getString().hashCode()))
						: route
						? glam.ardor.roleplayers_atlas.RoleplayersAtlas.id("route/" + baseLandmark.get(LandmarkComponentTypes.POS).getX() + "/" + baseLandmark.get(LandmarkComponentTypes.POS).getZ() + "/" + Integer.toHexString(label.getString().hashCode()))
						: penLabel
						? glam.ardor.roleplayers_atlas.RoleplayersAtlas.id("label/" + baseLandmark.get(LandmarkComponentTypes.POS).getX() + "/" + baseLandmark.get(LandmarkComponentTypes.POS).getZ() + "/" + Integer.toHexString(label.getString().hashCode()))
						: selectedTexture.keyId().withSuffixedPath("/" + selectedColor.getId() + "/" + baseLandmark.get(LandmarkComponentTypes.POS).getX() + "/" + baseLandmark.get(LandmarkComponentTypes.POS).getZ()),
					copy -> {
					if (!territory && !simple) {
						Item item = manager.getOrThrow(RegistryKeys.ITEM).get(selectedTexture.item());
						if (item != null && !item.getDefaultStack().isEmpty()) copy.set(LandmarkComponentTypes.STACK, item.getDefaultStack().copy());
					}
					copy.set(LandmarkComponentTypes.COLOR, selectedColor.getEntityColor());
					copy.set(LandmarkComponentTypes.NAME, label);
					// Inscriptions and routes have no zone settings — only color and
					// opacity. Everything is written explicitly: the copy inherits the
					// old landmark's components, so defaults must overwrite stale values.
					if (!simple) {
						copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.ZONE_TITLE, zoneTitleEnabled);
						copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.ZONE_RADIUS, zoneRadius);
						copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.HIDE_LABEL, hideLabel);
					}
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.OPACITY, markerOpacity);
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.LAYER, markerLayer);
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.NOTE, noteText.trim());
					if (route) copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DISTANCE, showDistance);
					// The shadow/backing toggle rides on every mark with a drawn
					// label; the turn only on names written across the map.
					if (territory || simple) copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW, labelNoShadow);
					if (territory || (simple && !route)) copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_ROTATION, labelRotation);
					// A mark keeps the date it was first drawn on; editing it later
					// doesn't rewrite history, it only decides whether to show it.
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.SHOW_DATE, dateEnabled);
					Long drawnDay = baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.DAY);
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.DAY, drawnDay != null ? drawnDay : glam.ardor.roleplayers_atlas.AtlasTime.gameDay());
					Long drawnReal = baseLandmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.REAL_TIME);
					copy.set(glam.ardor.roleplayers_atlas.AtlasComponents.REAL_TIME, drawnReal != null ? drawnReal : glam.ardor.roleplayers_atlas.AtlasTime.realMillis());
				});
				WorldAtlasData.swapLandmark(summary.dimension(), previous, written,
					Text.translatable(previous == null ? "gui.roleplayers_atlas.undo.markerAdded" : "gui.roleplayers_atlas.undo.markerEdited", label));
			}
			((AtlasScreen) getParent()).updateBookmarkerList();
			ClientPlayerEntity player = MinecraftClient.getInstance().player;
			if (player != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F));
			closeChild();
		}).dimensions(this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, confirmRowY(), BUTTON_WIDTH, 20).build());
		addDrawableChild(btnCancel = ButtonWidget.builder(Text.translatable("gui.cancel"), (button) -> closeChild())
			.dimensions(this.width / 2 + BUTTON_SPACING / 2, confirmRowY(), BUTTON_WIDTH, 20).build());
		// A road walked further than it was drawn: pick the pencil back up at its
		// far end rather than starting again.
		addDrawableChild(btnExtend = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.route.extend"), (button) -> {
			if (getParent() instanceof AtlasScreen screen) {
				closeChild();
				screen.startExtendingRoute(baseLandmark);
			}
		}).dimensions(this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, confirmRowY() + 24, BUTTON_WIDTH * 2 + BUTTON_SPACING, 20).build());
		// A zone that grew or shrank since it was drawn: take the brush back to it
		// rather than rubbing it out and painting the whole thing again.
		addDrawableChild(btnEditArea = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.territory.edit"), (button) -> {
			if (getParent() instanceof AtlasScreen screen) {
				closeChild();
				screen.startEditingTerritory(baseLandmark);
			}
		}).dimensions(this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, confirmRowY() + 24, BUTTON_WIDTH * 2 + BUTTON_SPACING, 20).build());
		int settingsLeft = this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2;
		int settingsRight = settingsLeft + BUTTON_WIDTH + BUTTON_SPACING;
		// The label-controls row (rotation/shadow) is added only for the marks
		// that carry a drawn label; everything else leaves these null.
		rotationSlider = null;
		btnShadow = null;
		if (isSpawn()) {
			btnLayer = null;
			btnDate = null;
		} else {
			addDrawableChild(btnLayer = ButtonWidget.builder(layerText(), button -> {
				List<glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer> allLayers = glam.ardor.roleplayers_atlas.MarkerLayers.all();
				int index = 0;
				for (int i = 0; i < allLayers.size(); i++) {
					if (allLayers.get(i).id().equals(markerLayer)) index = i;
				}
				markerLayer = allLayers.get((index + 1) % allLayers.size()).id();
				button.setMessage(layerText());
			}).dimensions(settingsLeft, labelRowY(), BUTTON_WIDTH, 20).build());
			// Dating shares the layer row: every kind of mark can carry a date.
			addDrawableChild(btnDate = ButtonWidget.builder(onOff("gui.roleplayers_atlas.marker.dating", dateEnabled), button -> {
				dateEnabled = !dateEnabled;
				button.setMessage(onOff("gui.roleplayers_atlas.marker.dating", dateEnabled));
			}).dimensions(settingsRight, labelRowY(), BUTTON_WIDTH, 20).build());
		}
		if (isSpawn()) {
			// Only how it looks: the icon row, the ink row and how strongly it shows.
			btnZoneTitle = null;
			btnHideLabel = null;
			radiusSlider = null;
			btnDistance = null;
			addDrawableChild(opacitySlider = new OpacitySlider(this.width / 2 - BUTTON_WIDTH / 2, this.height / 2 + 46, BUTTON_WIDTH, 20));
		} else if (isSimple()) {
			// Inscription/route settings: just the ink color row and opacity —
			// plus, for a route, whether it tells you how long it is.
			btnZoneTitle = null;
			btnHideLabel = null;
			radiusSlider = null;
			if (isRoute()) {
				addDrawableChild(opacitySlider = new OpacitySlider(settingsLeft, this.height / 2 + 34, BUTTON_WIDTH, 20));
				addDrawableChild(btnDistance = ButtonWidget.builder(onOff("gui.roleplayers_atlas.marker.distanceToggle", showDistance), button -> {
					showDistance = !showDistance;
					button.setMessage(onOff("gui.roleplayers_atlas.marker.distanceToggle", showDistance));
				}).dimensions(settingsRight, this.height / 2 + 34, BUTTON_WIDTH, 20).build());
			} else {
				btnDistance = null;
				addDrawableChild(opacitySlider = new OpacitySlider(this.width / 2 - BUTTON_WIDTH / 2, this.height / 2 + 34, BUTTON_WIDTH, 20));
			}
			// Label row: a route only hides its backing ribbon; a pen inscription
			// can also be turned.
			if (isRoute()) {
				addDrawableChild(btnShadow = ButtonWidget.builder(shadowText(), button -> {
					labelNoShadow = !labelNoShadow;
					button.setMessage(shadowText());
				}).dimensions(this.width / 2 - BUTTON_WIDTH / 2, this.height / 2 + 58, BUTTON_WIDTH, 20).build());
			} else {
				addDrawableChild(rotationSlider = new RotationSlider(settingsLeft, this.height / 2 + 58, BUTTON_WIDTH, 20));
				addDrawableChild(btnShadow = ButtonWidget.builder(shadowText(), button -> {
					labelNoShadow = !labelNoShadow;
					button.setMessage(shadowText());
				}).dimensions(settingsRight, this.height / 2 + 58, BUTTON_WIDTH, 20).build());
			}
		} else {
			btnDistance = null;
			addDrawableChild(btnZoneTitle = ButtonWidget.builder(zoneTitleText(), button -> {
				zoneTitleEnabled = !zoneTitleEnabled;
				button.setMessage(zoneTitleText());
			}).dimensions(settingsLeft, this.height / 2 + 46, BUTTON_WIDTH, 20).build());
			addDrawableChild(radiusSlider = new RadiusSlider(settingsRight, this.height / 2 + 46, BUTTON_WIDTH, 20));
			addDrawableChild(opacitySlider = new OpacitySlider(settingsLeft, this.height / 2 + 69, BUTTON_WIDTH, 20));
			addDrawableChild(btnHideLabel = ButtonWidget.builder(hideLabelText(), button -> {
				hideLabel = !hideLabel;
				button.setMessage(hideLabelText());
			}).dimensions(settingsRight, this.height / 2 + 69, BUTTON_WIDTH, 20).build());
			// Territory names are written across the land, so they get the turn and
			// shadow controls; a plain point marker keeps the old two-row layout.
			if (isTerritory()) {
				addDrawableChild(rotationSlider = new RotationSlider(settingsLeft, this.height / 2 + 92, BUTTON_WIDTH, 20));
				addDrawableChild(btnShadow = ButtonWidget.builder(shadowText(), button -> {
					labelNoShadow = !labelNoShadow;
					button.setMessage(shadowText());
				}).dimensions(settingsRight, this.height / 2 + 92, BUTTON_WIDTH, 20).build());
			}
		}
		textField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, (this.width - 200) / 2, this.height / 2 - 65, 200, 20, Text.translatable("gui.roleplayers_atlas.marker.label"));
		textField.setEditable(true);
		textField.setFocusUnlocked(true);
		textField.setFocused(true);
		textField.setPlaceholder(Text.translatable("gui.roleplayers_atlas.marker.label"));
		textField.setText(baseLandmark.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString());

		// The note is a large multiline box below the layer button (see render).

		if (isTerritory() || isSimple()) {
			// No icon choice for territories (always the colored diamond), pen
			// inscriptions (text only) or routes (dashed path).
			textureScrollBox = null;
			textureRadioGroup = null;
			textureButtons.clear();
		} else {
			textureScrollBox = new ScrollBoxComponent(false, (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING));
			this.addChild(textureScrollBox);

			int typeCount = (int) MarkerTextures.getInstance().asMap().values().stream().filter(t -> t.keyId().getPath().startsWith("custom/")).count();
			int typesOnScreen = Math.min(typeCount, 7);
			int typeScrollWidth = typesOnScreen * (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
			textureScrollBox.getViewport().setSize(typeScrollWidth, TexturePreviewButton.FRAME_SIZE + TYPE_SPACING);
			textureScrollBox.setGuiCoords((this.width - typeScrollWidth) / 2, this.height / 2 - 35);

			textureRadioGroup = new ToggleButtonRadioGroup<>();
			textureRadioGroup.addListener(button -> {
				selectedTexture = button.getValue();
				for (IMarkerTypeSelectListener listener : markerListeners) {
					listener.onSelectMarkerType(button.getValue());
				}
			});
			int contentX = 0;
			for (MarkerTexture texture : MarkerTextures.getInstance().asMap().values()) {
				if (!texture.keyId().getPath().startsWith("custom/")) continue;
				if (selectedTexture == MarkerTexture.DEFAULT) selectedTexture = texture;
				TexturePreviewButton<MarkerTexture> markerGui = new MarkerPreviewButton(texture, ColorUtil.componentsFromRgb(selectedColor.getFireworkColor()));
				textureButtons.put(texture, markerGui);
				textureRadioGroup.addButton(markerGui);
				textureScrollBox.getViewport().addContent(markerGui).setRelativeX(contentX);
				contentX += TexturePreviewButton.FRAME_SIZE + TYPE_SPACING;
			}
		}

		// Color

		colorScrollBox = new ScrollBoxComponent(false, (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING));
		this.addChild(colorScrollBox);

		int colorsOnScreen = Math.min(DyeColor.values().length, 7);
		int colorScrollWidth = colorsOnScreen * (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
		colorScrollBox.getViewport().setSize(colorScrollWidth, TexturePreviewButton.FRAME_SIZE + TYPE_SPACING);
		// With the texture row hidden for territories, the color row moves up to fill the gap.
		colorScrollBox.setGuiCoords((this.width - colorScrollWidth) / 2, this.height / 2 + (isTerritory() || isSimple() ? -12 : 10));

		colorRadioGroup = new ToggleButtonRadioGroup<>();
		colorRadioGroup.addListener(button -> {
			selectedColor = button.getValue();
			if (textureRadioGroup != null) {
				for (TexturePreviewButton<MarkerTexture> preview : textureRadioGroup) {
					preview.reTint(ColorUtil.componentsFromRgb(selectedColor.getFireworkColor()));
				}
			}
		});
		int colorContentX = 0;
		for (DyeColor color : DyeColor.values()) {
			TexturePreviewButton<DyeColor> colorGui = new TexturePreviewButton<>(color, BookmarkButton.TEXTURE_LEFT, BookmarkButton.WIDTH, BookmarkButton.HEIGHT, BookmarkButton.HEIGHT, ColorUtil.componentsFromRgb(color.getFireworkColor()));
			colorButtons.put(color, colorGui);
			colorRadioGroup.addButton(colorGui);
			colorScrollBox.getViewport().addContent(colorGui).setRelativeX(colorContentX);
			colorContentX += TexturePreviewButton.FRAME_SIZE + TYPE_SPACING;
		}

		updateSelected();
	}

	@Override
	public void closeChild() {
		super.closeChild();
		if (textureScrollBox != null) {
			textureScrollBox.closeChild();
		}
		if (colorScrollBox != null) {
			colorScrollBox.closeChild();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isSpawn()) return super.mouseClicked(mouseX, mouseY, button);
		noteFocused = isOverNoteBox(mouseX, mouseY);
		if (noteFocused && textField != null) {
			textField.setFocused(false);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button) || textField.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (noteFocused) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !noteText.isEmpty()) {
				noteText = noteText.substring(0, noteText.length() - 1);
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				noteFocused = false;
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
				String clip = MinecraftClient.getInstance().keyboard.getClipboard();
				if (clip != null) noteText = (noteText + clip).substring(0, Math.min(512, noteText.length() + clip.length()));
				return true;
			}
			return true;
		}
		// Enter is the same as pressing Done. Typing a name and reaching for the
		// mouse to confirm it is a step nobody expects to have to take.
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			if (btnDone != null && btnDone.active) btnDone.onPress();
			return true;
		}
		// Escape backs out of the dialog. Handled here rather than left to the
		// screen behind, which would take the whole book down with it.
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			closeChild();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers) || textField.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (noteFocused) {
			if (chr >= ' ' && noteText.length() < 512) noteText += chr;
			return true;
		}
		return super.charTyped(chr, modifiers) || textField.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		// Manual dim instead of renderBackground: 1.21.6+ only allows one
		// background blur per frame, and the parent screen already used it.
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, isSpawn() ? Text.translatable("gui.roleplayers_atlas.spawn.name") : Text.translatable("gui.roleplayers_atlas.marker.label"), this.height / 2 - 80, 0xDDDDDD, true);
		// Inscriptions must not be empty — they are nothing but their text.
		btnDone.active = !isPenLabel() || !textField.getText().isBlank();
		btnCancel.render(context, mouseX, mouseY, partialTick);
		btnDone.render(context, mouseX, mouseY, partialTick);
		// Nothing to lengthen on a road that is still being drawn for the first time.
		btnExtend.visible = isRoute() && !isSpawn() && baseLandmark != null && !baseLandmark.id().getPath().equals("newroute");
		if (btnExtend.visible) btnExtend.render(context, mouseX, mouseY, partialTick);
		// Nothing to reshape on a zone that is still being painted for the first time.
		btnEditArea.visible = isTerritory() && baseLandmark != null && !baseLandmark.id().getPath().equals("newterritory");
		if (btnEditArea.visible) btnEditArea.render(context, mouseX, mouseY, partialTick);
		if (!isSpawn()) {
			textField.render(context, mouseX, mouseY, partialTick);
			renderNoteBox(context);
		}
		if (btnZoneTitle != null) btnZoneTitle.render(context, mouseX, mouseY, partialTick);
		if (btnHideLabel != null) btnHideLabel.render(context, mouseX, mouseY, partialTick);
		if (radiusSlider != null) radiusSlider.render(context, mouseX, mouseY, partialTick);
		if (btnDistance != null) btnDistance.render(context, mouseX, mouseY, partialTick);
		if (btnDate != null) btnDate.render(context, mouseX, mouseY, partialTick);
		if (btnLayer != null) btnLayer.render(context, mouseX, mouseY, partialTick);
		if (rotationSlider != null) rotationSlider.render(context, mouseX, mouseY, partialTick);
		if (btnShadow != null) btnShadow.render(context, mouseX, mouseY, partialTick);
		opacitySlider.render(context, mouseX, mouseY, partialTick);
		// Darker background for marker type selector
		if (textureScrollBox != null) {
			context.fillGradient(textureScrollBox.getGuiX() + 1, textureScrollBox.getGuiY() + 1,
				textureScrollBox.getGuiX() + textureScrollBox.getWidth(),
				textureScrollBox.getGuiY() + textureScrollBox.getHeight(),
				0x88101010, 0x99101010);
		}
		context.fillGradient(colorScrollBox.getGuiX() + 1, colorScrollBox.getGuiY() + 1,
			colorScrollBox.getGuiX() + colorScrollBox.getWidth(),
			colorScrollBox.getGuiY() + colorScrollBox.getHeight(),
			0x88101010, 0x99101010);
		super.render(context, mouseX, mouseY, partialTick);
	}

	public interface IMarkerTypeSelectListener {
		void onSelectMarkerType(MarkerTexture texture);
	}
}
