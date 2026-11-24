package glam.ardor.roleplayers_atlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import glam.ardor.roleplayers_atlas.BiomeOverrides;
import glam.ardor.roleplayers_atlas.TileTexture;
import glam.ardor.roleplayers_atlas.gui.core.ButtonComponent;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.reloader.BiomeTileProviders;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * Correcting how a biome is drawn.
 * <p>
 * The atlas guesses at biomes it has no picture for, and guessing is sometimes
 * wrong. Only the person looking at the map can tell – so this lists every
 * biome the world has, says plainly where each one's look came from, and lets
 * that be overruled.
 * <p>
 * Four views in one window: the list of biomes, the grid of looks to choose
 * from, what you have painted cell by cell, and what other people's scrolls
 * have had to say. Choosing is done by picture rather than by name, because
 * "windswept_gravelly_hills" tells a player nothing and the drawing tells them
 * everything.
 */
public class BiomeModal extends Component {
	private static final int ROW_H = 20;
	private static final int LIST_W = 310;
	private static final int LIST_ROWS = 9;
	private static final int LIST_H = ROW_H * LIST_ROWS;
	private static final int SP = 8;
	/** Two buttons to the page's width, so a long caption never has to crawl. */
	private static final int BTN_W = (LIST_W - SP) / 2;
	/** Three to the page, for the row under the grid of looks. */
	private static final int BTN3_W = (LIST_W - SP * 2) / 3;
	/**
	 * The gap above the list, matched to the one below it. The scroll box hangs
	 * its arrows in both, overlapping each end by the same few pixels – a page
	 * that breathes evenly reads better than one that gives the arrow all the
	 * room it asks for at the top and none at the bottom.
	 */
	private static final int ROW_GAP = 12;
	private static final int TOGGLE_W = 160;
	private static final int SEARCH_W = LIST_W - TOGGLE_W - SP;

	/** A tile sheet is 32x48 of 8px subtiles; these four make up a piece of open ground. */
	private static final int[][] FULL_TILE = {{0, 0, 2, 4}, {1, 0, 1, 4}, {0, 1, 2, 3}, {1, 1, 1, 3}};
	/** Row previews stay small; the grid draws them large enough to actually read. */
	private static final int TILE_SIZE = 16;
	private static final int GRID_TILE = 24;
	private static final int GRID_GAP = 6;

	private record Entry(Identifier biome, BiomeTileProviders.Source source) {
	}

	/** Which of the window's faces is showing. */
	private enum View {
		LIST, PICK, OWN, SHARED
	}

	private final List<Entry> entries = new ArrayList<>();
	private ScrollBoxComponent listBox;
	private ScrollBoxComponent pickBox;
	private ScrollBoxComponent sheetBox;
	private EditBox searchField;
	private Button btnGuessedOnly;
	private Button btnResetAll;
	private Button btnDone;
	private Button btnPickCancel;
	private Button btnPickClear;
	private Button btnPickApply;
	private Button btnOwn;
	private Button btnShared;
	private Button btnSheetBack;
	private Button btnSheetDropAll;
	/** Highlighted in the grid, waiting for Apply. */
	private @Nullable Identifier chosenLook = null;
	private View view = View.LIST;

	private boolean guessedOnly = false;
	/** Non-null while the grid of looks is up, naming the biome being corrected. */
	private @Nullable Identifier picking = null;
	/** Highlighted when the window was opened by clicking a piece of map. */
	private @Nullable Identifier focus = null;

	/** The chunks a chosen look will be painted onto, when the window came from a selection. */
	private @Nullable java.util.Set<net.minecraft.world.level.ChunkPos> patchCells = null;
	private @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> patchDim = null;

	public void setData(@Nullable Identifier focus) {
		this.focus = focus;
		this.picking = null;
		this.guessedOnly = false;
		this.patchCells = null;
		this.patchDim = null;
		this.view = View.LIST;
	}

	/** Opened straight onto the grid, to paint the marked-out area. */
	public void setPatchData(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, java.util.Set<net.minecraft.world.level.ChunkPos> cells) {
		this.focus = null;
		this.guessedOnly = false;
		this.patchDim = dimension;
		this.patchCells = cells;
		this.picking = null;
		this.view = View.LIST;
	}

	/** The dimension whose painting this window is talking about. */
	private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() {
		if (patchDim != null) return patchDim;
		return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension();
	}

	private void collect() {
		entries.clear();
		Map<Identifier, BiomeTileProviders.Source> sources = BiomeTileProviders.getInstance().sources();
		String query = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
		sources.forEach((biome, source) -> {
			if (guessedOnly && (source == BiomeTileProviders.Source.EXACT || source == BiomeTileProviders.Source.TAGS)) return;
			if (!query.isEmpty() && !biome.toString().toLowerCase(Locale.ROOT).contains(query)) return;
			entries.add(new Entry(biome, source));
		});
		// The one that was clicked first, then anything guessed, then the rest –
		// what needs attention should not have to be hunted for.
		entries.sort(Comparator
			.comparingInt((Entry e) -> e.biome.equals(focus) ? 0 : switch (e.source) {
				case MANUAL -> 1;
				case SHARED -> 2;
				case NONE -> 3;
				case NAME -> 4;
				case TAGS -> 5;
				case EXACT -> 6;
			})
			.thenComparing(e -> e.biome.toString()));
	}

	private int listLeft() {
		return (this.width - LIST_W) / 2;
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		searchField = new EditBox(Minecraft.getInstance().font, listLeft(), listTop() - ROW_GAP - 12, SEARCH_W, 12, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.search"));
		searchField.setEditable(true);
		searchField.setCanLoseFocus(true);
		searchField.setBordered(false);
		searchField.setHint(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.search"));
		searchField.setResponder(q -> rebuildList());

		addRenderableWidget(btnGuessedOnly = Button.builder(guessedOnlyText(), button -> {
			guessedOnly = !guessedOnly;
			button.setMessage(guessedOnlyText());
			rebuildList();
		}).bounds(listLeft() + LIST_W - TOGGLE_W, listTop() - ROW_GAP - 16, TOGGLE_W, 16).build());

		listBox = new ScrollBoxComponent(true, ROW_H);
		addChild(listBox);
		listBox.getViewport().setSize(LIST_W, LIST_H);
		listBox.setGuiCoords(listLeft(), listTop());

		pickBox = new ScrollBoxComponent(true, GRID_TILE + GRID_GAP);
		sheetBox = new ScrollBoxComponent(true, ROW_H);

		int bottomY = listTop() + LIST_H + 12;
		int secondY = bottomY + 24;
		addRenderableWidget(btnResetAll = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.resetAll"), button -> {
			BiomeOverrides.resetAll();
			rebuildList();
		}).bounds(listLeft(), bottomY, BTN_W, 20).build());
		addRenderableWidget(btnDone = Button.builder(net.minecraft.network.chat.Component.translatable("gui.done"), button -> closeChild())
			.bounds(listLeft() + BTN_W + SP, bottomY, BTN_W, 20).build());

		// Grid row: cancel, unpaint, apply.
		addRenderableWidget(btnPickCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.cancel"), button -> stopPicking())
			.bounds(listLeft(), bottomY, BTN3_W, 20).build());
		// Painting an area can also mean unpainting it, which is a choice of its own.
		addRenderableWidget(btnPickClear = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.clearPatch"), button -> {
			if (patchCells != null && patchDim != null) BiomeOverrides.setPatches(patchDim, patchCells, null);
			closeChild();
		}).bounds(listLeft() + BTN3_W + SP, bottomY, BTN3_W, 20).build());
		addRenderableWidget(btnPickApply = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.applyPatch"), button -> {
			if (chosenLook == null) return;
			if (patchCells != null && patchDim != null) {
				BiomeOverrides.setPatches(patchDim, patchCells, chosenLook);
				closeChild();
			} else if (picking != null) {
				BiomeOverrides.set(picking, chosenLook);
				stopPicking();
			}
		}).bounds(listLeft() + (BTN3_W + SP) * 2, bottomY, BTN3_W, 20).build());

		// The two ledgers of painting, side by side: yours and everyone else's.
		addRenderableWidget(btnOwn = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.own"), button -> openSheets(View.OWN))
			.tooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.own.tooltip")))
			.bounds(listLeft(), secondY, BTN_W, 20).build());
		addRenderableWidget(btnShared = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.shared"), button -> openSheets(View.SHARED))
			.tooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.shared.tooltip")))
			.bounds(listLeft() + BTN_W + SP, secondY, BTN_W, 20).build());

		addRenderableWidget(btnSheetBack = Button.builder(net.minecraft.network.chat.Component.translatable("gui.back"), button -> closeSheets())
			.bounds(listLeft() + BTN_W + SP, bottomY, BTN_W, 20).build());
		addRenderableWidget(btnSheetDropAll = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.sheetDropAll"), button -> {
			if (view == View.SHARED) {
				BiomeOverrides.dropAllImported();
			} else if (dimension() != null) {
				BiomeOverrides.clearPatches(dimension());
			}
			closeSheets();
		}).bounds(listLeft(), bottomY, BTN_W, 20).build());

		rebuildList();
		// Selected an area first: the list has nothing to say, go straight to
		// choosing what to paint it as.
		if (patchCells != null) startPicking(null);
	}

	private net.minecraft.network.chat.Component guessedOnlyText() {
		return net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.guessedOnly", net.minecraft.network.chat.Component.translatable(guessedOnly ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	private int listTop() {
		return this.height / 2 - LIST_H / 2 + 6;
	}

	private void rebuildList() {
		collect();
		listBox.getViewport().removeAllContent();
		int y = 0;
		for (Entry entry : entries) {
			listBox.getViewport().addContent(new BiomeRow(entry)).setRelativeY(y);
			y += ROW_H;
		}
		listBox.setScrollPos(0);
	}

	/** Shows the grid of looks. A null biome means an area is being painted instead. */
	private void startPicking(@Nullable Identifier biome) {
		picking = biome;
		view = View.PICK;
		chosenLook = null;
		removeChild(listBox);
		addChild(pickBox);
		List<Identifier> looks = new ArrayList<>(BiomeTileProviders.getInstance().availableLooks());
		looks.sort(Comparator.comparing(Identifier::toString));
		// A gap wide enough for the highlight ring to sit in without touching.
		int perRow = LIST_W / (GRID_TILE + GRID_GAP);
		pickBox.getViewport().removeAllContent();
		pickBox.getViewport().setSize(perRow * (GRID_TILE + GRID_GAP), LIST_H);
		pickBox.setGuiCoords((this.width - perRow * (GRID_TILE + GRID_GAP)) / 2, listTop());
		int i = 0;
		for (Identifier look : looks) {
			LookButton button = new LookButton(look);
			pickBox.getViewport().addContent(button).setRelativeCoords(2 + (i % perRow) * (GRID_TILE + GRID_GAP), 2 + (i / perRow) * (GRID_TILE + GRID_GAP));
			i++;
		}
		pickBox.setScrollPos(0);
	}

	private void stopPicking() {
		// An area was marked out on the map; there is no list behind this.
		if (patchCells != null) {
			closeChild();
			return;
		}
		picking = null;
		view = View.LIST;
		removeChild(pickBox);
		addChild(listBox);
		listBox.setGuiCoords(listLeft(), listTop());
		rebuildList();
	}

	/** Shows one of the two ledgers of painting. */
	private void openSheets(View which) {
		view = which;
		removeChild(listBox);
		addChild(sheetBox);
		rebuildSheets();
	}

	private void closeSheets() {
		view = View.LIST;
		removeChild(sheetBox);
		addChild(listBox);
		listBox.setGuiCoords(listLeft(), listTop());
		rebuildList();
	}

	private void rebuildSheets() {
		sheetBox.getViewport().removeAllContent();
		sheetBox.getViewport().setSize(LIST_W, LIST_H);
		sheetBox.setGuiCoords(listLeft(), listTop());
		int y = 0;
		if (view == View.SHARED) {
			for (String author : BiomeOverrides.importedAuthors()) {
				sheetBox.getViewport().addContent(new SharedRow(author)).setRelativeY(y);
				y += ROW_H;
			}
		} else if (dimension() != null) {
			for (Map.Entry<Identifier, Integer> entry : BiomeOverrides.ownPatchLooks(dimension()).entrySet()) {
				sheetBox.getViewport().addContent(new OwnRow(entry.getKey(), entry.getValue())).setRelativeY(y);
				y += ROW_H;
			}
		}
		sheetBox.setScrollPos(0);
	}

	/** Rebuilt after something was torn out, dropping back to the list once it is bare. */
	private void afterTearOut() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
		if (sheetCount() > 0) rebuildSheets();
		else closeSheets();
	}

	private int sheetCount() {
		if (view == View.SHARED) return BiomeOverrides.importedAuthors().size();
		return dimension() == null ? 0 : BiomeOverrides.ownPatchLooks(dimension()).size();
	}

	/** One cartographer's sheet: whose it is, how much of it there is, and the way to be rid of it. */
	private class SharedRow extends SheetRow {
		private final String author;

		SharedRow(String author) {
			this.author = author;
			addListener(button -> {
				BiomeOverrides.dropImported(author);
				afterTearOut();
			});
		}

		@Override
		void drawContent(GuiGraphicsExtractor context, int x, int y) {
			BiomeOverrides.Sheet sheet = BiomeOverrides.importedSheet(author);
			context.text(font, font.plainSubstrByWidth(author, getWidth() - 60), x + 4, y + 2, 0xFFF0E4C8, true);
			if (sheet != null) {
				context.text(font, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.sharedCounts", sheet.biomeCount(), sheet.patchCount()), x + 4, y + 11, 0xFF9A8C70, true);
			}
		}
	}

	/** One look you painted with, and how much ground it covers. */
	private class OwnRow extends SheetRow {
		private final Identifier look;
		private final int count;

		OwnRow(Identifier look, int count) {
			this.look = look;
			this.count = count;
			addListener(button -> {
				if (dimension() != null) BiomeOverrides.clearPatchesOf(dimension(), look);
				afterTearOut();
			});
		}

		@Override
		void drawContent(GuiGraphicsExtractor context, int x, int y) {
			TileTexture texture = tileOf(look);
			if (texture != null) drawTile(context, texture, x + 2, y + 2);
			int textX = x + TILE_SIZE + 6;
			net.minecraft.network.chat.Component amount = net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.ownCount", count);
			int amountW = font.width(amount);
			context.text(font, font.plainSubstrByWidth(friendlyName(look).getString(), getWidth() - 56 - TILE_SIZE - amountW), textX, y + 2, 0xFFF0E4C8, true);
			context.text(font, amount, x + getWidth() - 50 - amountW, y + 2, 0xFF9A8C70, true);
			// The name it goes by underneath, dim: a translation is what you read,
			// but the id is what you type into a config or say to an admin.
			context.text(font, font.plainSubstrByWidth(look.toString(), getWidth() - 56 - TILE_SIZE), textX, y + 11, 0xFF7A6E58, true);
		}
	}

	/** Shared shape for both ledgers: read on the left, one word to tear it out on the right. */
	private abstract class SheetRow extends ButtonComponent {
		SheetRow() {
			setSize(LIST_W, ROW_H);
		}

		abstract void drawContent(GuiGraphicsExtractor context, int x, int y);

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			drawContent(context, x, y);
			net.minecraft.network.chat.Component drop = net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.sheetDrop");
			context.text(font, drop, x + getWidth() - font.width(drop) - 3, y + 6, 0xFFC8A05A, true);
			context.disableScissor();
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
			double mouseX = click.x(), mouseY = click.y();
			int button = click.button();
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			if (!isMouseOver(mouseX, mouseY)) return false;
			// Only the word on the right tears the sheet out – the rest of the row
			// is there to be read, and a whole sheet is too much to lose by a slip.
			if (mouseX < getGuiX() + getWidth() - 46) return false;
			return super.mouseClicked(click, doubled);
		}
	}

	/** The colour of the atlas' own paper, so a preview reads as it will on the map. */
	private static final int PAPER = 0xFFE6D3A6;
	private static final int PAPER_EDGE = 0xFF6B5334;

	/**
	 * The open-ground face of a tile, which is what a biome looks like at a
	 * glance. Tile art is brown lines on nothing at all, so it needs the paper
	 * put behind it or there is nothing to see.
	 */
	static void drawTile(GuiGraphicsExtractor context, TileTexture texture, int x, int y) {
		drawTile(context, texture, x, y, TILE_SIZE);
	}

	/** The same at any size – the grid draws them large enough to actually read. */
	static void drawTile(GuiGraphicsExtractor context, TileTexture texture, int x, int y, int size) {
		int half = size / 2;
		context.fill(x, y, x + size, y + size, PAPER);
		for (int[] part : FULL_TILE) {
			context.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x + part[0] * half, y + part[1] * half, part[2] * 8, part[3] * 8, half, half, 8, 8, 32, 48);
		}
		context.outline(x, y, size, size, PAPER_EDGE);
	}

	/**
	 * A biome's name as a person would say it.
	 * <p>
	 * Vanilla ships a translation for every one of its own; a datapack biome
	 * usually ships none, so its path is tidied into words instead – better than
	 * showing a raw key, and honest about there being nothing better to show.
	 */
	static net.minecraft.network.chat.Component friendlyName(Identifier biome) {
		String key = "biome." + biome.getNamespace() + "." + biome.getPath().replace('/', '.');
		net.minecraft.network.chat.Component translated = net.minecraft.network.chat.Component.translatable(key);
		if (!translated.getString().equals(key)) return translated;
		return net.minecraft.network.chat.Component.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(biome.getPath().replaceAll("[/_-]", " ")));
	}

	private static @Nullable TileTexture tileOf(@Nullable Identifier look) {
		if (look == null) return null;
		return BiomeTileProviders.getInstance().getTileProvider(look).getTexture(net.minecraft.world.level.ChunkPos.ZERO, null);
	}

	/** One biome: what it is, what it's drawn as, where that came from. */
	private class BiomeRow extends ButtonComponent {
		private final Entry entry;

		BiomeRow(Entry entry) {
			this.entry = entry;
			setSize(LIST_W, ROW_H);
			addListener(button -> startPicking(entry.biome));
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			boolean hovered = !isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom;
			if (entry.biome.equals(focus)) context.fill(x, y, x + getWidth(), y + getHeight(), 0x33FFD98A);
			if (hovered) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);

			TileTexture texture = tileOf(BiomeTileProviders.getInstance().drawnAs(entry.biome));
			if (texture != null) drawTile(context, texture, x + 2, y + 2);

			int textX = x + TILE_SIZE + 6;
			// Top line: what it is called, then where its look came from, right
			// aligned. Bottom line: the id, dimmer, for anyone who needs it.
			net.minecraft.network.chat.Component source = net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.source." + entry.source.name().toLowerCase(Locale.ROOT));
			int sourceW = font.width(source);
			int nameRoom = x + getWidth() - sourceW - 6 - textX;
			context.text(font, font.plainSubstrByWidth(friendlyName(entry.biome).getString(), nameRoom), textX, y + 2, 0xFFF0E4C8, true);
			context.text(font, source, x + getWidth() - sourceW - 3, y + 2, sourceInk(entry.source), true);

			// Only your own word can be taken back here; someone else's sheet is
			// torn out whole, from the ledger that lists them.
			boolean corrected = BiomeOverrides.all().containsKey(entry.biome);
			net.minecraft.network.chat.Component reset = net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.reset");
			int idRoom = getWidth() - (TILE_SIZE + 6) - (corrected ? font.width(reset) + 8 : 4);
			context.text(font, font.plainSubstrByWidth(entry.biome.toString(), idRoom), textX, y + 11, 0xFF9A8C70, true);
			if (corrected) {
				context.text(font, reset, x + getWidth() - font.width(reset) - 3, y + 11, 0xFFC8A05A, true);
			}
			context.disableScissor();
		}

		/** Guesswork is the thing worth noticing, so it is the thing that is coloured. */
		private int sourceInk(BiomeTileProviders.Source source) {
			return switch (source) {
				case EXACT -> 0xFF8FA88F;
				case TAGS -> 0xFFA9A98F;
				case NAME -> 0xFFD8A45A;
				case NONE -> 0xFFD87A5A;
				case MANUAL -> 0xFF8FB4D8;
				case SHARED -> 0xFFB08FD8;
			};
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
			double mouseX = click.x(), mouseY = click.y();
			int button = click.button();
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			if (!isMouseOver(mouseX, mouseY)) return false;
			// The reset word on the bottom right takes the click back to the guess.
			if (BiomeOverrides.all().containsKey(entry.biome) && mouseX >= getGuiX() + getWidth() - 46 && mouseY >= getGuiY() + ROW_H / 2) {
				BiomeOverrides.set(entry.biome, null);
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
				rebuildList();
				return true;
			}
			return super.mouseClicked(click, doubled);
		}
	}

	/** One look to choose from, shown as itself. */
	private class LookButton extends ButtonComponent {
		private final Identifier look;

		LookButton(Identifier look) {
			this.look = look;
			setSize(GRID_TILE, GRID_TILE);
			// Clicking marks the choice; Apply commits it. Both kinds of
			// correction change the whole map, so both deserve the second step.
			addListener(button -> chosenLook = look);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			TileTexture texture = tileOf(look);
			if (texture != null) drawTile(context, texture, x, y, GRID_TILE);
			boolean chosen = chosenLook != null ? look.equals(chosenLook) : (picking != null && look.equals(BiomeTileProviders.getInstance().drawnAs(picking)));
			if (chosen) context.outline(x - 1, y - 1, GRID_TILE + 2, GRID_TILE + 2, 0xFF8FB4D8);
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) {
				context.outline(x - 1, y - 1, GRID_TILE + 2, GRID_TILE + 2, 0xFFFFE8B0);
			}
			context.disableScissor();
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
			double mouseX = click.x(), mouseY = click.y();
			int button = click.button();
			if (mouseY < listTop() || mouseY >= listTop() + LIST_H) return false;
			return super.mouseClicked(click, doubled);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, switch (view) {
			case SHARED -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.sharedTitle");
			case OWN -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.ownTitle");
			case LIST -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.title");
			case PICK -> picking != null
				? net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.pickTitle", friendlyName(picking))
				: net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.patchTitle", patchCells == null ? 0 : patchCells.size());
		}, listTop() - ROW_GAP - 16 - 12, 0xDDDDDD, true);

		boolean listing = view == View.LIST;
		boolean sheets = view == View.OWN || view == View.SHARED;
		searchField.setVisible(listing);
		btnGuessedOnly.visible = listing;
		btnResetAll.visible = listing;
		btnDone.visible = listing;
		btnPickCancel.visible = view == View.PICK;
		btnPickClear.visible = view == View.PICK && patchCells != null;
		btnPickApply.visible = view == View.PICK;
		btnPickApply.active = chosenLook != null;
		btnSheetBack.visible = sheets;
		btnSheetDropAll.visible = sheets;
		// Both ledgers are always on show, greyed when there is nothing in them:
		// a button that only exists once something has happened is a feature
		// nobody knows to expect.
		int painted = dimension() == null ? 0 : BiomeOverrides.ownPatches(dimension()).size();
		btnOwn.visible = listing;
		btnOwn.active = painted > 0;
		btnOwn.setMessage(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.own", painted));
		btnShared.visible = listing;
		btnShared.active = BiomeOverrides.hasImported();
		btnShared.setMessage(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.shared", BiomeOverrides.importedAuthors().size()));

		context.fillGradient(listLeft(), listTop(), listLeft() + LIST_W, listTop() + LIST_H, 0x66101010, 0x77101010);
		if (sheets) {
			btnSheetBack.extractRenderState(context, mouseX, mouseY, partialTick);
			btnSheetDropAll.extractRenderState(context, mouseX, mouseY, partialTick);
		} else if (listing) {
			searchField.extractRenderState(context, mouseX, mouseY, partialTick);
			btnGuessedOnly.extractRenderState(context, mouseX, mouseY, partialTick);
			btnResetAll.extractRenderState(context, mouseX, mouseY, partialTick);
			btnDone.extractRenderState(context, mouseX, mouseY, partialTick);
			btnOwn.extractRenderState(context, mouseX, mouseY, partialTick);
			btnShared.extractRenderState(context, mouseX, mouseY, partialTick);
			if (entries.isEmpty()) {
				drawCentered(context, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.biomes.none"), listTop() + LIST_H / 2 - 4, 0xAAAAAA, true);
			}
		} else {
			btnPickCancel.extractRenderState(context, mouseX, mouseY, partialTick);
			if (patchCells != null) btnPickClear.extractRenderState(context, mouseX, mouseY, partialTick);
			btnPickApply.extractRenderState(context, mouseX, mouseY, partialTick);
		}
		super.extractRenderState(context, mouseX, mouseY, partialTick);

		// The name of the look under the cursor, once everything else is down.
		if (view == View.PICK) {
			for (Component child : pickBox.getViewport().getChildren()) {
				for (Component row : child.getChildren()) {
					if (row instanceof LookButton look && !look.isClipped && look.isMouseOver(mouseX, mouseY) && mouseY >= listTop() && mouseY < listTop() + LIST_H) {
						context.setComponentTooltipForNextFrame(font, java.util.List.of(friendlyName(look.look), net.minecraft.network.chat.Component.literal(look.look.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
					}
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x(), mouseY = click.y();
		int button = click.button();
		if (view == View.LIST && searchField.isVisible()) {
			boolean overSearch = searchField.isMouseOver(mouseX, mouseY);
			searchField.setFocused(overSearch);
			if (overSearch) return searchField.mouseClicked(click, doubled);
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int keyCode = input.key(), scanCode = input.keycode(), modifiers = input.modifiers();
		// Escape steps back one view at a time, and out of the window from the
		// list. Left to the screen behind it, it would shut the book instead.
		if (keyCode == InputConstants.KEY_ESCAPE) {
			switch (view) {
				case PICK -> stopPicking();
				case OWN, SHARED -> closeSheets();
				case LIST -> closeChild();
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			if (view == View.PICK && btnPickApply.active) btnPickApply.onPress(input);
			else if (view == View.LIST) closeChild();
			return true;
		}
		return super.keyPressed(input) || searchField.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		char chr = (char) input.codepoint();
		return super.charTyped(input) || searchField.charTyped(input);
	}
}
