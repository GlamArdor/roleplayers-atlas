package glam.ardor.roleplayers_atlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import glam.ardor.roleplayers_atlas.CityPaint;
import glam.ardor.roleplayers_atlas.TileTexture;
import glam.ardor.roleplayers_atlas.gui.core.ButtonComponent;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.reloader.TileTextures;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

/**
 * The box of pieces a town is drawn from.
 * <p>
 * The atlas draws villages the game tells it about. A town players built is
 * invisible to it – so this hands the cartographer the same pieces the atlas
 * uses for villages: roads, crossroads, wells, houses, farms, market stalls,
 * lamps. Take one and it stays in hand; every cell the brush touches on the map
 * is built on there and then.
 * <p>
 * Two further faces behind it, as in the biome window: the ledger of what you
 * have drawn, and the ledger of what came in on other people's scrolls.
 * Anything drawn can be lifted again – nothing about the land underneath is
 * touched.
 */
public class CityModal extends Component {
	private static final int ROW_H = 20;
	private static final int LIST_W = 310;
	private static final int LIST_H = ROW_H * 9;
	private static final int SP = 8;
	private static final int BTN_W = (LIST_W - SP) / 2;
	/** Row previews stay small; the grid draws them large enough to actually read. */
	private static final int TILE_SIZE = 16;
	private static final int GRID_TILE = 24;
	private static final int GRID_GAP = 6;
	/** The gap above the list, matched to the one below it. */
	private static final int ROW_GAP = 12;

	/** Which face of the window is showing. */
	private enum View {
		PICK, OWN, SHARED
	}

	private View view = View.PICK;

	private ScrollBoxComponent pickBox;
	private ScrollBoxComponent sheetBox;
	private EditBox searchField;
	private Button btnCancel;
	private Button btnOwn;
	private Button btnShared;
	private Button btnSheetBack;
	private Button btnSheetDropAll;

	private @Nullable ResourceKey<Level> dim = null;

	/** Every piece the atlas has a drawing for, filtered by the search box. */
	private final List<Identifier> pieces = new ArrayList<>();

	public void setData(ResourceKey<Level> dimension) {
		this.dim = dimension;
		this.view = View.PICK;
	}

	/** What the brush is holding right now, so the grid can show which one it is. */
	private @Nullable Identifier held() {
		return getParent() instanceof AtlasScreen screen ? screen.heldCityPiece : null;
	}

	private int listLeft() {
		return (this.width - LIST_W) / 2;
	}

	private int listTop() {
		return this.height / 2 - LIST_H / 2 + 6;
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		// The gap above the list matches the one below it, as in the biome window.
		searchField = new EditBox(Minecraft.getInstance().font, listLeft(), listTop() - ROW_GAP - 12, LIST_W, 12, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.search"));
		searchField.setEditable(true);
		searchField.setCanLoseFocus(true);
		searchField.setBordered(false);
		searchField.setHint(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.search"));
		searchField.setResponder(q -> rebuildPieces());

		pickBox = new ScrollBoxComponent(true, GRID_TILE + GRID_GAP);
		sheetBox = new ScrollBoxComponent(true, ROW_H);
		addChild(pickBox);

		int bottomY = listTop() + LIST_H + 12;
		int secondY = bottomY + 24;

		addRenderableWidget(btnCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.done"), button -> closeChild())
			.bounds(listLeft(), bottomY, LIST_W, 20).build());

		addRenderableWidget(btnOwn = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.own"), button -> openSheets(View.OWN))
			.tooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.own.tooltip")))
			.bounds(listLeft(), secondY, BTN_W, 20).build());
		addRenderableWidget(btnShared = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.shared"), button -> openSheets(View.SHARED))
			.tooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.shared.tooltip")))
			.bounds(listLeft() + BTN_W + SP, secondY, BTN_W, 20).build());

		addRenderableWidget(btnSheetDropAll = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.sheetDropAll"), button -> {
			if (view == View.SHARED) CityPaint.dropAllImported();
			else if (dim != null) CityPaint.clearAll(dim);
			closeSheets();
		}).bounds(listLeft(), bottomY, BTN_W, 20).build());
		addRenderableWidget(btnSheetBack = Button.builder(net.minecraft.network.chat.Component.translatable("gui.back"), button -> closeSheets())
			.bounds(listLeft() + BTN_W + SP, bottomY, BTN_W, 20).build());

		rebuildPieces();
	}

	/**
	 * The pieces on offer. Village parts first because that is what a town is
	 * made of; everything else the atlas can draw follows, since a keep drawn as
	 * a fortress corridor is a fair thing to want.
	 */
	private void rebuildPieces() {
		pieces.clear();
		String query = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
		for (Identifier id : TileTextures.getInstance().getTextures().keySet()) {
			if (!id.getPath().startsWith("structure/")) continue;
			// Searched by what is written on screen as well as by the raw path –
			// nobody looking for a well is going to type "well_covered".
			if (!query.isEmpty()
				&& !id.getPath().toLowerCase(Locale.ROOT).contains(query)
				&& !pieceName(id).getString().toLowerCase(Locale.ROOT).contains(query)
				&& !familyName(id).getString().toLowerCase(Locale.ROOT).contains(query)) continue;
			pieces.add(id);
		}
		pieces.sort(Comparator
			.comparingInt((Identifier id) -> id.getPath().startsWith("structure/village/") ? 0 : 1)
			.thenComparing(Identifier::toString));

		int perRow = LIST_W / (GRID_TILE + GRID_GAP);
		pickBox.getViewport().removeAllContent();
		pickBox.getViewport().setSize(perRow * (GRID_TILE + GRID_GAP), LIST_H);
		pickBox.setGuiCoords((this.width - perRow * (GRID_TILE + GRID_GAP)) / 2, listTop());
		int i = 0;
		for (Identifier piece : pieces) {
			pickBox.getViewport().addContent(new PieceButton(piece)).setRelativeCoords(2 + (i % perRow) * (GRID_TILE + GRID_GAP), 2 + (i / perRow) * (GRID_TILE + GRID_GAP));
			i++;
		}
		pickBox.setScrollPos(0);
	}

	private void openSheets(View which) {
		view = which;
		removeChild(pickBox);
		addChild(sheetBox);
		rebuildSheets();
	}

	private void closeSheets() {
		view = View.PICK;
		removeChild(sheetBox);
		addChild(pickBox);
		rebuildPieces();
	}

	private void rebuildSheets() {
		sheetBox.getViewport().removeAllContent();
		sheetBox.getViewport().setSize(LIST_W, LIST_H);
		sheetBox.setGuiCoords(listLeft(), listTop());
		int y = 0;
		if (view == View.SHARED) {
			for (String author : CityPaint.importedAuthors()) {
				sheetBox.getViewport().addContent(new SharedRow(author)).setRelativeY(y);
				y += ROW_H;
			}
		} else if (dim != null) {
			for (Map.Entry<Identifier, Integer> entry : CityPaint.ownLooks(dim).entrySet()) {
				sheetBox.getViewport().addContent(new OwnRow(entry.getKey(), entry.getValue())).setRelativeY(y);
				y += ROW_H;
			}
		}
		sheetBox.setScrollPos(0);
	}

	private void afterTearOut() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
		if (sheetCount() > 0) rebuildSheets();
		else closeSheets();
	}

	private int sheetCount() {
		if (view == View.SHARED) return CityPaint.importedAuthors().size();
		return dim == null ? 0 : CityPaint.ownLooks(dim).size();
	}

	/**
	 * A piece's name as a person would say it.
	 * <p>
	 * Translated by its own last word, because the same well and the same
	 * crossroad turn up in five kinds of village and there is no sense naming
	 * each of them five times. Anything without a translation falls back to its
	 * file name tidied into words – honest about there being nothing better.
	 */
	static net.minecraft.network.chat.Component pieceName(Identifier piece) {
		String path = piece.getPath();
		int slash = path.lastIndexOf('/');
		String leaf = slash < 0 ? path : path.substring(slash + 1);
		String key = "tile.roleplayers_atlas.piece." + leaf;
		net.minecraft.network.chat.Component translated = net.minecraft.network.chat.Component.translatable(key);
		if (!translated.getString().equals(key)) return translated;
		return net.minecraft.network.chat.Component.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(leaf.replaceAll("[_-]", " ")));
	}

	/** Where it comes from – a plains village, a nether fortress – so two wells can be told apart. */
	static net.minecraft.network.chat.Component familyName(Identifier piece) {
		String family = pieceFamily(piece);
		String key = "tile.roleplayers_atlas.family." + family.replace('/', '.');
		net.minecraft.network.chat.Component translated = net.minecraft.network.chat.Component.translatable(key);
		if (!translated.getString().equals(key)) return translated;
		return net.minecraft.network.chat.Component.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(family.replaceAll("[/_-]", " ")));
	}

	static String pieceFamily(Identifier piece) {
		String path = piece.getPath().substring("structure/".length());
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(0, slash);
	}

	private static @Nullable TileTexture textureOf(@Nullable Identifier piece) {
		return piece == null ? null : TileTextures.getInstance().getTextures().get(piece);
	}

	/** One piece to choose from, shown as itself. Taking it puts it in hand at once. */
	private class PieceButton extends ButtonComponent {
		private final Identifier piece;

		PieceButton(Identifier piece) {
			this.piece = piece;
			setSize(GRID_TILE, GRID_TILE);
			// Careful with whose closeChild this is: called bare it would take the
			// button out of its own grid, and the button's parent is the viewport,
			// not the screen. Both have to be reached through the window itself.
			addListener(button -> {
				Component owner = CityModal.this.getParent();
				CityModal.this.closeChild();
				if (owner instanceof AtlasScreen screen) screen.holdCityPiece(piece);
			});
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			TileTexture texture = textureOf(piece);
			if (texture != null) BiomeModal.drawTile(context, texture, x, y, GRID_TILE);
			if (piece.equals(held())) context.outline(x - 1, y - 1, GRID_TILE + 2, GRID_TILE + 2, 0xFF8FB4D8);
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

	/** Shared shape for both ledgers: read on the left, one word to rub it out on the right. */
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
			if (mouseX < getGuiX() + getWidth() - 46) return false;
			return super.mouseClicked(click, doubled);
		}
	}

	private class OwnRow extends SheetRow {
		private final Identifier piece;
		private final int count;

		OwnRow(Identifier piece, int count) {
			this.piece = piece;
			this.count = count;
			addListener(button -> {
				if (dim != null) CityPaint.clearOf(dim, piece);
				afterTearOut();
			});
		}

		@Override
		void drawContent(GuiGraphicsExtractor context, int x, int y) {
			TileTexture texture = textureOf(piece);
			if (texture != null) BiomeModal.drawTile(context, texture, x + 2, y + 2, TILE_SIZE);
			int textX = x + TILE_SIZE + 6;
			net.minecraft.network.chat.Component amount = net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.ownCount", familyName(piece), count);
			int amountW = font.width(amount);
			context.text(font, font.plainSubstrByWidth(pieceName(piece).getString(), getWidth() - 56 - TILE_SIZE - amountW), textX, y + 2, 0xFFF0E4C8, true);
			context.text(font, amount, x + getWidth() - 50 - amountW, y + 2, 0xFF9A8C70, true);
			// The name it goes by underneath, dim, the same as in the biome window.
			context.text(font, font.plainSubstrByWidth(piece.toString(), getWidth() - 56 - TILE_SIZE), textX, y + 11, 0xFF7A6E58, true);
		}
	}

	private class SharedRow extends SheetRow {
		private final String author;

		SharedRow(String author) {
			this.author = author;
			addListener(button -> {
				CityPaint.dropImported(author);
				afterTearOut();
			});
		}

		@Override
		void drawContent(GuiGraphicsExtractor context, int x, int y) {
			context.text(font, font.plainSubstrByWidth(author, getWidth() - 60), x + 4, y + 2, 0xFFF0E4C8, true);
			context.text(font, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.sharedCount", CityPaint.importedCount(author)), x + 4, y + 11, 0xFF9A8C70, true);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, switch (view) {
			case OWN -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.ownTitle");
			case SHARED -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.sharedTitle");
			case PICK -> net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.title");
		}, listTop() - ROW_GAP - 12 - 12, 0xDDDDDD, true);

		boolean picking = view == View.PICK;
		searchField.setVisible(picking);
		btnCancel.visible = picking;
		btnOwn.visible = picking;
		btnOwn.active = dim != null && CityPaint.ownCount(dim) > 0;
		btnOwn.setMessage(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.own", dim == null ? 0 : CityPaint.ownCount(dim)));
		btnShared.visible = picking;
		btnShared.active = CityPaint.hasImported();
		btnShared.setMessage(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.shared", CityPaint.importedAuthors().size()));
		btnSheetBack.visible = !picking;
		btnSheetDropAll.visible = !picking;

		context.fillGradient(listLeft(), listTop(), listLeft() + LIST_W, listTop() + LIST_H, 0x66101010, 0x77101010);
		if (picking) {
			searchField.extractRenderState(context, mouseX, mouseY, partialTick);
			btnCancel.extractRenderState(context, mouseX, mouseY, partialTick);
			btnOwn.extractRenderState(context, mouseX, mouseY, partialTick);
			btnShared.extractRenderState(context, mouseX, mouseY, partialTick);
			if (pieces.isEmpty()) {
				drawCentered(context, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.city.none"), listTop() + LIST_H / 2 - 4, 0xAAAAAA, true);
			}
		} else {
			btnSheetBack.extractRenderState(context, mouseX, mouseY, partialTick);
			btnSheetDropAll.extractRenderState(context, mouseX, mouseY, partialTick);
		}
		super.extractRenderState(context, mouseX, mouseY, partialTick);

		if (picking) {
			for (Component child : pickBox.getViewport().getChildren()) {
				for (Component row : child.getChildren()) {
					if (row instanceof PieceButton piece && !piece.isClipped && piece.isMouseOver(mouseX, mouseY) && mouseY >= listTop() && mouseY < listTop() + LIST_H) {
						context.setComponentTooltipForNextFrame(font, List.of(
							pieceName(piece.piece),
							familyName(piece.piece).copy().withStyle(net.minecraft.ChatFormatting.GRAY),
							net.minecraft.network.chat.Component.literal(piece.piece.toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
						), mouseX, mouseY);
					}
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x(), mouseY = click.y();
		int button = click.button();
		if (view == View.PICK && searchField.isVisible()) {
			boolean overSearch = searchField.isMouseOver(mouseX, mouseY);
			searchField.setFocused(overSearch);
			if (overSearch) return searchField.mouseClicked(click, doubled);
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int keyCode = input.key(), scanCode = input.keycode(), modifiers = input.modifiers();
		if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			if (view == View.PICK) closeChild();
			else closeSheets();
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
