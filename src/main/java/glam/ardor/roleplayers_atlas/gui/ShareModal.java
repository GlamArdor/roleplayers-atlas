package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.AtlasComponents;
import glam.ardor.roleplayers_atlas.MapShare;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.ButtonComponent;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.gui.core.ToggleButtonComponent;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Map-scroll exchange: pick exactly what to export (terrain, individual
 * markers/inscriptions/territories in three wheel-scrollable lists) into a
 * shareable .atlas file, or import such a file from the scrolls folder.
 */
public class ShareModal extends Component {
	private static final int COL_W = 110;
	private static final int COL_COUNT = 5;
	private static final int COL_SPACING = 8;
	private static final int ROW_H = 17;
	private static final int LIST_H = ROW_H * 6;
	private static final int BTN_W = 100;
	private static final int SP = 8;

	private static final class Entry {
		final Landmark landmark;
		final String label;
		final int type; // 0 markers, 1 inscriptions, 2 territories
		final String layerId;
		boolean included = true;

		Entry(Landmark landmark, String label, int type, String layerId) {
			this.landmark = landmark;
			this.label = label;
			this.type = type;
			this.layerId = layerId;
		}
	}

	/** Checkbox row inside a scroll list. */
	private class EntryRow extends ToggleButtonComponent {
		final Entry entry;

		EntryRow(Entry entry, int width) {
			super(true);
			this.entry = entry;
			setSelected(entry.included);
			setSize(width, ROW_H);
			addListener(button -> entry.included = isSelected());
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			// The viewport only culls fully-outside children — partially visible
			// rows must clip themselves to the box.
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			int bx = x + 2;
			int by = y + 4;
			context.fill(bx, by, bx + 9, by + 9, 0xFF3E2B18);
			context.fill(bx + 1, by + 1, bx + 8, by + 8, 0xFFE8DCC2);
			if (isSelected()) context.fill(bx + 2, by + 2, bx + 7, by + 7, 0xFF7A4A1E);
			context.drawText(textRenderer, textRenderer.trimToWidth(entry.label, getWidth() - 17), bx + 13, y + 5, 0xFFF0E4C8, true);
			context.disableScissor();
			super.render(context, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}

	/** Layer row: its checkbox bulk-toggles every landmark of that layer. */
	private class LayerRow extends ToggleButtonComponent {
		final glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer;

		LayerRow(glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer, int width) {
			super(true);
			this.layer = layer;
			setSize(width, ROW_H);
			addListener(button -> {
				boolean target = isSelected();
				for (Entry entry : entries) {
					if (entry.layerId.equals(layer.id())) entry.included = target;
				}
				syncRows();
			});
		}

		private boolean allIncluded() {
			boolean any = false;
			for (Entry entry : entries) {
				if (!entry.layerId.equals(layer.id())) continue;
				any = true;
				if (!entry.included) return false;
			}
			return any;
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			setSelected(allIncluded());
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			int bx = x + 2;
			int by = y + 4;
			context.fill(bx, by, bx + 9, by + 9, 0xFF3E2B18);
			context.fill(bx + 1, by + 1, bx + 8, by + 8, 0xFFE8DCC2);
			if (isSelected()) context.fill(bx + 2, by + 2, bx + 7, by + 7, 0xFF7A4A1E);
			// Layer color swatch, then the name.
			context.fill(bx + 13, by, bx + 22, by + 9, 0xFF3E2B18);
			context.fill(bx + 14, by + 1, bx + 21, by + 8, 0xFF000000 | (layer.color() & 0xFFFFFF));
			context.drawText(textRenderer, textRenderer.trimToWidth(layer.name(), getWidth() - 30), bx + 26, y + 5, 0xFFF0E4C8, true);
			context.disableScissor();
			super.render(context, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}

	/** One of Xaero's waypoint files, listed like a scroll and read the same way. */
	private class XaeroRow extends ButtonComponent {
		final glam.ardor.roleplayers_atlas.XaeroImport.Source source;

		XaeroRow(glam.ardor.roleplayers_atlas.XaeroImport.Source source, int width) {
			this.source = source;
			setSize(width, ROW_H);
			addListener(button -> selectXaero(source));
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (selectedXaero != null && selectedXaero.file().equals(source.file())) context.fill(x, y, x + getWidth(), y + getHeight(), 0x33FFD98A);
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			String count = " (" + source.count() + ")";
			int room = getWidth() - 6 - textRenderer.getWidth(count);
			String label = textRenderer.trimToWidth(source.label(), room);
			// The world being played on right now is the one nearly everybody wants.
			context.drawText(textRenderer, label + count, x + 3, y + 5, source.current() ? 0xFFFFE9A8 : 0xFFF0E4C8, true);
			context.disableScissor();
			super.render(context, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}

	/** Checkbox row for one of Xaero's waypoint sets. */
	private class SetRow extends ToggleButtonComponent {
		final String set;

		SetRow(String set, int count, int width) {
			super(true);
			this.set = set;
			setSelected(true);
			setSize(width, ROW_H);
			this.count = count;
			addListener(button -> {
				if (isSelected()) chosenSets.add(set);
				else chosenSets.remove(set);
			});
		}

		private final int count;

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			int bx = x + 2;
			int by = y + 4;
			context.fill(bx, by, bx + 9, by + 9, 0xFF3E2B18);
			context.fill(bx + 1, by + 1, bx + 8, by + 8, 0xFFE8DCC2);
			if (isSelected()) context.fill(bx + 2, by + 2, bx + 7, by + 7, 0xFF7A4A1E);
			String count = " (" + this.count + ")";
			int room = getWidth() - 17 - textRenderer.getWidth(count);
			context.drawText(textRenderer, textRenderer.trimToWidth(set, room) + count, bx + 13, y + 5, 0xFFF0E4C8, true);
			context.disableScissor();
			super.render(context, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}

	/** Clickable file row inside the import list. Selecting one reads it, but applies nothing. */
	private class FileRow extends ButtonComponent {
		final String name;
		final Path file;

		FileRow(Path file, int width) {
			this.file = file;
			this.name = file.getFileName().toString();
			setSize(width, ROW_H);
			addListener(button -> select(file));
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (file.equals(selectedFile)) context.fill(x, y, x + getWidth(), y + getHeight(), 0x33FFD98A);
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			context.drawText(textRenderer, textRenderer.trimToWidth(name, getWidth() - 6), x + 3, y + 5, 0xFFF0E4C8, true);
			context.disableScissor();
			super.render(context, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(mouseX, mouseY, button);
		}
	}

	private WorldSummary summary;
	private DynamicRegistryManager manager;
	private RegistryKey<World> dim;

	private final List<Entry> entries = new ArrayList<>();
	private boolean includeTerrain = true;
	private boolean includeAuthor = true;
	private boolean includeCorrections = true;
	private boolean importMode = false;

	private TextFieldWidget nameField;
	private ButtonWidget btnTerrain;
	private ButtonWidget btnCorrections;
	private ButtonWidget btnAuthor;
	private ButtonWidget btnAll;
	private ButtonWidget btnNone;
	private ButtonWidget btnExport;
	private ButtonWidget btnImport;
	private ButtonWidget btnFolder;
	private ButtonWidget btnCancel;
	private ButtonWidget btnTakeIn;
	private final ScrollBoxComponent[] columns = new ScrollBoxComponent[COL_COUNT];
	private ScrollBoxComponent fileBox;

	/** The scroll being looked at, and what reading it turned up. Nothing is applied until Take in. */
	private Path selectedFile = null;
	private MapShare.Preview preview = null;

	/** Import mode reads either our own scrolls or Xaero's waypoints. */
	private boolean fromXaero = false;
	private ScrollBoxComponent setBox;
	private glam.ardor.roleplayers_atlas.XaeroImport.Source selectedXaero = null;
	private List<glam.ardor.roleplayers_atlas.XaeroImport.Waypoint> xaeroWaypoints = new ArrayList<>();
	private final java.util.Set<String> chosenSets = new java.util.HashSet<>();
	private boolean setsAsLayers = true;
	private ButtonWidget btnSource;
	private ButtonWidget btnSetsAsLayers;

	private void select(Path file) {
		selectedFile = file;
		preview = MapShare.peek(file, dim);
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	/** Reads one of Xaero's files and lists its sets to pick from. */
	private void selectXaero(glam.ardor.roleplayers_atlas.XaeroImport.Source source) {
		selectedXaero = source;
		xaeroWaypoints = glam.ardor.roleplayers_atlas.XaeroImport.read(source.file());
		chosenSets.clear();
		chosenSets.addAll(glam.ardor.roleplayers_atlas.XaeroImport.sets(xaeroWaypoints).keySet());
		rebuildSetBox();
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
	}

	private void rebuildSetBox() {
		if (setBox == null) return;
		setBox.getViewport().removeAllContent();
		int y = 0;
		for (java.util.Map.Entry<String, Integer> entry : glam.ardor.roleplayers_atlas.XaeroImport.sets(xaeroWaypoints).entrySet()) {
			setBox.getViewport().addContent(new SetRow(entry.getKey(), entry.getValue(), PANE_W)).setRelativeY(y);
			y += ROW_H;
		}
		setBox.setScrollPos(0);
	}

	/** The waypoints the chosen sets hold, which is what Take in would write. */
	private List<glam.ardor.roleplayers_atlas.XaeroImport.Waypoint> chosenWaypoints() {
		return xaeroWaypoints.stream()
			.filter(w -> chosenSets.contains(glam.ardor.roleplayers_atlas.XaeroImport.setName(w.set())))
			.toList();
	}

	private Text sourceText() {
		return Text.translatable("gui.roleplayers_atlas.share.source",
			Text.translatable(fromXaero ? "gui.roleplayers_atlas.share.source.xaero" : "gui.roleplayers_atlas.share.source.scrolls"));
	}

	private Text setsAsLayersText() {
		return Text.translatable("gui.roleplayers_atlas.share.setsAsLayers",
			Text.translatable(setsAsLayers ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	void setData(WorldSummary summary, DynamicRegistryManager manager, RegistryKey<World> dim, WorldAtlasData data) {
		this.summary = summary;
		this.manager = manager;
		this.dim = dim;
		importMode = false;
		includeTerrain = true;
		includeCorrections = true;
		entries.clear();
		data.getEditableLandmarks().keySet().forEach(landmark -> {
			boolean pen = Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL));
			boolean route = landmark.contains(AtlasComponents.ROUTE);
			boolean territory = !landmark.contains(LandmarkComponentTypes.POS) && landmark.contains(LandmarkComponentTypes.CHUNKS);
			String name = landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
			if (name.isBlank()) name = Text.translatable(route ? "gui.roleplayers_atlas.unnamedRoute" : territory ? "gui.roleplayers_atlas.unnamedZone" : pen ? "gui.roleplayers_atlas.unnamedLabel" : "gui.roleplayers_atlas.unnamedMarker").getString();
			String layer = glam.ardor.roleplayers_atlas.RoleplayersAtlas.layerOf(landmark);
			if (!glam.ardor.roleplayers_atlas.MarkerLayers.DEFAULT_ID.equals(layer)) {
				glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layerDef = glam.ardor.roleplayers_atlas.MarkerLayers.get(layer);
				if (layerDef != null) name += " (" + layerDef.name() + ")";
			}
			entries.add(new Entry(landmark, name, route ? 3 : territory ? 2 : pen ? 1 : 0, layer));
		});
		entries.sort(java.util.Comparator.comparing(e -> e.label, String.CASE_INSENSITIVE_ORDER));
	}

	private Text terrainText() {
		return Text.translatable("gui.roleplayers_atlas.share.terrain", Text.translatable(includeTerrain ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	private Text authorText() {
		return Text.translatable("gui.roleplayers_atlas.share.author", Text.translatable(includeAuthor ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	private Text correctionsText() {
		return Text.translatable("gui.roleplayers_atlas.share.corrections", Text.translatable(includeCorrections ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}


	private int listTop() {
		return this.height / 2 + 12;
	}

	/** Import mode is two panes side by side: the scrolls on the left, what one holds on the right. */
	private static final int PANE_W = 150;

	private int paneLeft() {
		return this.width / 2 - PANE_W - SP / 2;
	}

	private int previewLeft() {
		return this.width / 2 + SP / 2;
	}

	private int columnX(int column) {
		int total = COL_W * COL_COUNT + COL_SPACING * (COL_COUNT - 1);
		return this.width / 2 - total / 2 + column * (COL_W + COL_SPACING);
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		nameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, (this.width - 200) / 2, this.height / 2 - 62, 200, 18, Text.translatable("gui.roleplayers_atlas.share.name"));
		nameField.setEditable(true);
		nameField.setFocusUnlocked(true);
		nameField.setPlaceholder(Text.translatable("gui.roleplayers_atlas.share.name"));
		nameField.setMaxLength(48);
		nameField.setFocused(true);

		// Three things the scroll can carry besides the marks themselves: the land
		// as it was explored, how it was read, and whose reading it is.
		addDrawableChild(btnTerrain = ButtonWidget.builder(terrainText(), button -> {
			includeTerrain = !includeTerrain;
			button.setMessage(terrainText());
		}).dimensions(this.width / 2 - 152, this.height / 2 - 42, 96, 20).build());
		addDrawableChild(btnCorrections = ButtonWidget.builder(correctionsText(), button -> {
			includeCorrections = !includeCorrections;
			button.setMessage(correctionsText());
		}).tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.corrections.tooltip")))
			.dimensions(this.width / 2 - 48, this.height / 2 - 42, 96, 20).build());
		addDrawableChild(btnAuthor = ButtonWidget.builder(authorText(), button -> {
			includeAuthor = !includeAuthor;
			button.setMessage(authorText());
		}).dimensions(this.width / 2 + 56, this.height / 2 - 42, 96, 20).build());

		addDrawableChild(btnAll = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.all"), button -> {
			entries.forEach(e -> e.included = true);
			syncRows();
		}).dimensions(this.width / 2 - 98, this.height / 2 - 18, 96, 14).build());
		addDrawableChild(btnNone = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.none"), button -> {
			entries.forEach(e -> e.included = false);
			syncRows();
		}).dimensions(this.width / 2 + 2, this.height / 2 - 18, 96, 14).build());

		// The scroll boxes must join the tree before coordinates are assigned,
		// or their content resolves relative positions against (0,0).
		for (int type = 0; type < COL_COUNT; type++) {
			ScrollBoxComponent box = columns[type] = new ScrollBoxComponent(true, ROW_H);
			addChild(box);
			box.getViewport().setSize(COL_W, LIST_H);
			box.setGuiCoords(columnX(type), listTop());
			int y = 0;
			if (type == COL_COUNT - 1) {
				for (glam.ardor.roleplayers_atlas.MarkerLayers.MapLayer layer : glam.ardor.roleplayers_atlas.MarkerLayers.all()) {
					box.getViewport().addContent(new LayerRow(layer, COL_W)).setRelativeY(y);
					y += ROW_H;
				}
			} else {
				for (Entry entry : entries) {
					if (entry.type != type) continue;
					box.getViewport().addContent(new EntryRow(entry, COL_W)).setRelativeY(y);
					y += ROW_H;
				}
			}
		}

		fileBox = new ScrollBoxComponent(true, ROW_H);
		setBox = new ScrollBoxComponent(true, ROW_H);
		setBox.getViewport().setSize(PANE_W, LIST_H);

		// Import mode's two headers: where the marks are coming from, and — for
		// Xaero's — whether its sets become layers of ours.
		addDrawableChild(btnSource = ButtonWidget.builder(sourceText(), button -> {
			fromXaero = !fromXaero;
			button.setMessage(sourceText());
			switchMode(true);
		}).tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.source.tooltip")))
			.dimensions(paneLeft(), this.height / 2 - 18, PANE_W, 14).build());
		addDrawableChild(btnSetsAsLayers = ButtonWidget.builder(setsAsLayersText(), button -> {
			setsAsLayers = !setsAsLayers;
			button.setMessage(setsAsLayersText());
		}).tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.setsAsLayers.tooltip")))
			.dimensions(previewLeft(), this.height / 2 - 18, PANE_W, 14).build());

		int bottomY = this.height / 2 + LIST_H + 24;
		addDrawableChild(btnExport = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.export"), button -> doExport())
			.tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.export.tooltip")))
			.dimensions(0, bottomY, BTN_W, 20).build());
		addDrawableChild(btnImport = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.import"), button -> switchMode(!importMode))
			.tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.import.tooltip")))
			.dimensions(0, bottomY, BTN_W, 20).build());
		// Handing a scroll over means putting someone else's file into this
		// folder, so the modal opens it rather than describing where it is.
		addDrawableChild(btnFolder = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.folder"), button -> {
			Util.getOperatingSystem().open(MapShare.scrollsDir());
		}).tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.folder.tooltip")))
			.dimensions(0, bottomY, BTN_W, 20).build());
		// Nothing is written until this is pressed — the row above only reads.
		addDrawableChild(btnTakeIn = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.share.takeIn"), button -> {
			if (fromXaero) doXaeroImport();
			else if (selectedFile != null) doImport(selectedFile);
		}).tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.share.takeIn.tooltip")))
			.dimensions(0, bottomY, BTN_W, 20).build());
		addDrawableChild(btnCancel = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> closeChild())
			.dimensions(0, bottomY, BTN_W, 20).build());
		layoutBottomRow();
	}

	/**
	 * Spreads the bottom row across whichever buttons are on show. Export steps
	 * out in import mode, and leaving its slot standing empty pushed the rest of
	 * the row off to one side.
	 */
	private void layoutBottomRow() {
		List<ButtonWidget> shown = new ArrayList<>();
		if (!importMode) shown.add(btnExport);
		if (importMode) shown.add(btnTakeIn);
		shown.add(btnImport);
		shown.add(btnFolder);
		shown.add(btnCancel);
		int total = shown.size() * BTN_W + (shown.size() - 1) * SP;
		int x = this.width / 2 - total / 2;
		for (ButtonWidget button : shown) {
			button.setX(x);
			x += BTN_W + SP;
		}
	}

	private void syncRows() {
		for (ScrollBoxComponent box : columns) {
			if (box == null) continue;
			for (Component child : box.getViewport().getChildren()) {
				for (Component row : child.getChildren()) {
					if (row instanceof EntryRow entryRow) entryRow.setSelected(entryRow.entry.included);
				}
			}
		}
	}

	private void switchMode(boolean toImport) {
		importMode = toImport;
		nameField.setFocused(!toImport);
		selectedFile = null;
		preview = null;
		layoutBottomRow();
		selectedXaero = null;
		xaeroWaypoints = new ArrayList<>();
		chosenSets.clear();
		if (toImport) {
			for (ScrollBoxComponent box : columns) removeChild(box);
			addChild(fileBox);
			fileBox.getViewport().removeAllContent();
			fileBox.getViewport().setSize(PANE_W, LIST_H);
			fileBox.setGuiCoords(paneLeft(), listTop());
			int y = 0;
			if (fromXaero) {
				for (glam.ardor.roleplayers_atlas.XaeroImport.Source source : glam.ardor.roleplayers_atlas.XaeroImport.sources(dim)) {
					fileBox.getViewport().addContent(new XaeroRow(source, PANE_W)).setRelativeY(y);
					y += ROW_H;
				}
				// The right pane holds the sets to pick from rather than a reading.
				// Joined to the tree before it is placed or filled: content added to
				// a parentless box resolves its position against (0,0).
				addChild(setBox);
				setBox.getViewport().setSize(PANE_W, LIST_H);
				setBox.setGuiCoords(previewLeft(), listTop());
				rebuildSetBox();
			} else {
				removeChild(setBox);
				for (Path file : MapShare.listScrolls()) {
					fileBox.getViewport().addContent(new FileRow(file, PANE_W)).setRelativeY(y);
					y += ROW_H;
				}
			}
			fileBox.setScrollPos(0);
		} else {
			removeChild(fileBox);
			removeChild(setBox);
			for (int type = 0; type < COL_COUNT; type++) {
				addChild(columns[type]);
				columns[type].setGuiCoords(columnX(type), listTop());
			}
		}
	}

	private void doExport() {
		List<Landmark> selected = entries.stream().filter(e -> e.included).map(e -> e.landmark).toList();
		var player = MinecraftClient.getInstance().player;
		try {
			Path file = MapShare.export(dim, summary, manager, selected, includeTerrain, includeCorrections, includeAuthor, nameField.getText());
			if (player != null) {
				// An .atlas file has nothing to open it, so the link reveals the folder.
				player.sendMessage(Text.translatable("gui.roleplayers_atlas.share.exported", glam.ardor.roleplayers_atlas.ParchmentExport.folderLink(file)), false);
				glam.ardor.roleplayers_atlas.AtlasSounds.exportDone();
			}
			closeChild();
		} catch (Exception e) {
			glam.ardor.roleplayers_atlas.RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Export failed", e);
			if (player != null) player.sendMessage(Text.translatable("gui.roleplayers_atlas.share.exportFailed"), false);
		}
	}

	/** Writes the chosen Xaero waypoints in as marks of our own. */
	private void doXaeroImport() {
		var player = MinecraftClient.getInstance().player;
		List<glam.ardor.roleplayers_atlas.XaeroImport.Waypoint> chosen = chosenWaypoints();
		int written = glam.ardor.roleplayers_atlas.XaeroImport.importWaypoints(chosen, dim, setsAsLayers);
		if (player != null) {
			player.sendMessage(Text.translatable("gui.roleplayers_atlas.share.xaeroImported", written), false);
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F));
		}
		if (getParent() instanceof AtlasScreen screen) {
			screen.rebuildLayerTabs();
			screen.updateBookmarkerList();
		}
		closeChild();
	}

	private void doImport(Path file) {
		var player = MinecraftClient.getInstance().player;
		MapShare.ImportResult result = MapShare.importFile(file, dim, summary, manager);
		if (player != null) {
			if (result.error() != null) {
				player.sendMessage(Text.translatable("gui.roleplayers_atlas.share." + result.error()), false);
				return;
			}
			player.sendMessage(Text.translatable("gui.roleplayers_atlas.share.imported", result.landmarks(), result.regions(), result.corrections()), false);
			MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1F));
		}
		if (getParent() instanceof AtlasScreen screen) screen.updateBookmarkerList();
		closeChild();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, Text.translatable(importMode ? "gui.roleplayers_atlas.share.importTitle" : "gui.roleplayers_atlas.share.title"), this.height / 2 - 84, 0xDDDDDD, true);

		btnExport.visible = !importMode;
		btnTerrain.visible = !importMode;
		btnCorrections.visible = !importMode;
		btnAuthor.visible = !importMode;
		btnAll.visible = !importMode;
		btnNone.visible = !importMode;
		nameField.setVisible(!importMode);
		// Nothing to correct with is not worth offering as a choice.
		btnCorrections.active = !glam.ardor.roleplayers_atlas.BiomeOverrides.all().isEmpty()
			|| !glam.ardor.roleplayers_atlas.BiomeOverrides.ownPatches(dim).isEmpty()
			|| glam.ardor.roleplayers_atlas.CityPaint.ownCount(dim) > 0;
		btnExport.active = includeTerrain || (includeCorrections && btnCorrections.active) || entries.stream().anyMatch(e -> e.included);
		btnTakeIn.visible = importMode;
		btnTakeIn.active = fromXaero
			? selectedXaero != null && !chosenWaypoints().isEmpty()
			: selectedFile != null && preview != null && preview.sameDimension();
		// Switching source is only offered where Xaero has left something behind.
		btnSource.visible = importMode && glam.ardor.roleplayers_atlas.XaeroImport.present();
		btnSetsAsLayers.visible = importMode && fromXaero;
		if (btnSource.visible) btnSource.render(context, mouseX, mouseY, partialTick);
		if (btnSetsAsLayers.visible) btnSetsAsLayers.render(context, mouseX, mouseY, partialTick);
		btnImport.setMessage(Text.translatable(importMode ? "gui.roleplayers_atlas.share.back" : "gui.roleplayers_atlas.share.import"));
		btnImport.setTooltip(Tooltip.of(Text.translatable(importMode ? "gui.roleplayers_atlas.share.back.tooltip" : "gui.roleplayers_atlas.share.import.tooltip")));

		if (!importMode) {
			nameField.render(context, mouseX, mouseY, partialTick);
			btnTerrain.render(context, mouseX, mouseY, partialTick);
			btnCorrections.render(context, mouseX, mouseY, partialTick);
			btnAuthor.render(context, mouseX, mouseY, partialTick);
			btnAll.render(context, mouseX, mouseY, partialTick);
			btnNone.render(context, mouseX, mouseY, partialTick);
			btnExport.render(context, mouseX, mouseY, partialTick);
			// Darker backgrounds + headers over the lists.
			String[] headers = {"share.markers", "share.labels", "share.zones", "share.routes", "share.layersCol"};
			for (int i = 0; i < COL_COUNT; i++) {
				int x = columnX(i);
				context.fillGradient(x, listTop(), x + COL_W, listTop() + LIST_H, 0x66101010, 0x77101010);
				Text header = Text.translatable("gui.roleplayers_atlas." + headers[i]);
				context.drawText(textRenderer, header, x + (COL_W - textRenderer.getWidth(header)) / 2, listTop() - 11, 0xFFDDDDDD, true);
			}
		} else {
			context.fillGradient(paneLeft(), listTop(), paneLeft() + PANE_W, listTop() + LIST_H, 0x66101010, 0x77101010);
			context.fillGradient(previewLeft(), listTop(), previewLeft() + PANE_W, listTop() + LIST_H, 0x66101010, 0x77101010);
			if (fileBox.getViewport().getChildren().stream().allMatch(child -> child.getChildren().isEmpty())) {
				int y = listTop() + LIST_H / 2 - 4;
				Text none = Text.translatable(fromXaero ? "gui.roleplayers_atlas.share.noXaero" : "gui.roleplayers_atlas.share.noFiles");
				context.drawText(textRenderer, none, paneLeft() + (PANE_W - textRenderer.getWidth(none)) / 2, y, 0xFFAAAAAA, true);
			}
			if (fromXaero) {
				if (selectedXaero == null) {
					context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.share.pickXaero"), previewLeft() + 5, listTop() + 5, 0xFF9A8C70, true);
				} else {
					// What Take in would write, counted rather than promised.
					Text total = Text.translatable("gui.roleplayers_atlas.share.xaeroChosen", chosenWaypoints().size());
					context.drawText(textRenderer, total, previewLeft() + (PANE_W - textRenderer.getWidth(total)) / 2, listTop() + LIST_H + 4, 0xFFF0E4C8, true);
				}
			} else {
				renderPreview(context);
			}
		}
		if (btnTakeIn.visible) btnTakeIn.render(context, mouseX, mouseY, partialTick);
		btnImport.render(context, mouseX, mouseY, partialTick);
		btnFolder.render(context, mouseX, mouseY, partialTick);
		btnCancel.render(context, mouseX, mouseY, partialTick);
		super.render(context, mouseX, mouseY, partialTick);
	}

	/**
	 * What the chosen scroll holds, before a word of it is written down. Lines
	 * that would say nothing are left out rather than printed as zeroes: a scroll
	 * of nothing but roads should read as a scroll of roads.
	 */
	private void renderPreview(DrawContext context) {
		int x = previewLeft() + 5;
		int y = listTop() + 5;
		if (preview == null) {
			context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.share.pickFile"), x, y, 0xFF9A8C70, true);
			return;
		}
		context.drawText(textRenderer, textRenderer.trimToWidth(selectedFile.getFileName().toString(), PANE_W - 10), x, y, 0xFFF0E4C8, true);
		y += 13;
		if (!preview.sameDimension()) {
			// The one thing that stops an import outright, said before it is tried.
			for (net.minecraft.text.OrderedText line : textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.share.previewOtherDim", preview.dimension()).formatted(net.minecraft.util.Formatting.RED), PANE_W - 10)) {
				context.drawText(textRenderer, line, x, y, 0xFFFF8080, true);
				y += 10;
			}
			return;
		}
		if (!preview.authors().isEmpty()) {
			for (net.minecraft.text.OrderedText line : textRenderer.wrapLines(Text.translatable("gui.roleplayers_atlas.share.previewAuthors", String.join(", ", preview.authors())), PANE_W - 10)) {
				context.drawText(textRenderer, line, x, y, 0xFFC8A05A, true);
				y += 10;
			}
			y += 3;
		}
		int[][] counts = {
			{preview.markers(), 0}, {preview.labels(), 1}, {preview.routes(), 2}, {preview.territories(), 3},
			{preview.regions(), 4}, {preview.biomeCorrections(), 5}, {preview.patches(), 6}, {preview.towns(), 7}
		};
		String[] keys = {"markers", "labels", "routes", "zones", "regions", "biomes", "patches", "towns"};
		boolean any = false;
		for (int[] pair : counts) {
			if (pair[0] == 0) continue;
			any = true;
			context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.share.preview." + keys[pair[1]], pair[0]), x, y, 0xFFF0E4C8, true);
			y += 10;
		}
		if (!any) context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.share.previewEmpty"), x, y, 0xFF9A8C70, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Nothing in this component tree hands out focus, and a text field that
		// never gets it silently swallows every keystroke — so it claims and
		// releases focus on click itself.
		if (!importMode && nameField.isVisible()) {
			boolean overName = nameField.isMouseOver(mouseX, mouseY);
			nameField.setFocused(overName);
			if (overName) return nameField.mouseClicked(mouseX, mouseY, button);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Escape steps back out of the dialog — first out of the file list, then
		// out of the window. Left to the screen behind it, it would shut the book.
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			if (importMode) switchMode(false);
			else closeChild();
			return true;
		}
		if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && !importMode) {
			if (btnExport.active) doExport();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers) || nameField.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return super.charTyped(chr, modifiers) || nameField.charTyped(chr, modifiers);
	}

	@Override
	public void closeChild() {
		super.closeChild();
		// Only boxes still attached: a component with no parent takes closeChild to
		// mean "close the screen", and the lists not currently on show have none —
		// which shut the whole atlas every time this window was dismissed.
		for (ScrollBoxComponent box : columns) {
			if (box != null && box.getParent() != null) box.closeChild();
		}
		if (fileBox != null && fileBox.getParent() != null) fileBox.closeChild();
		if (setBox != null && setBox.getParent() != null) setBox.closeChild();
	}
}
