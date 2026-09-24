package glam.ardor.roleplayers_atlas.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.CityPaint;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.TileTexture;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.gui.core.ButtonComponent;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import glam.ardor.roleplayers_atlas.reloader.TileTextures;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.world.World;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.util.ArrayList;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.util.Comparator;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import java.util.Map;

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
	private TextFieldWidget searchField;
	private ButtonWidget btnCancel;
	private ButtonWidget btnOwn;
	private ButtonWidget btnShared;
	private ButtonWidget btnSheetBack;
	private ButtonWidget btnSheetDropAll;

	private @Nullable RegistryKey<World> dim = null;

	/** Every piece the atlas has a drawing for, filtered by the search box. */
	private final List<Identifier> pieces = new ArrayList<>();

	public void setData(RegistryKey<World> dimension) {
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
		searchField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, listLeft(), listTop() - ROW_GAP - 12, LIST_W, 12, Text.translatable("gui.roleplayers_atlas.city.search"));
		searchField.setEditable(true);
		searchField.setFocusUnlocked(true);
		searchField.setDrawsBackground(false);
		searchField.setPlaceholder(Text.translatable("gui.roleplayers_atlas.city.search"));
		searchField.setChangedListener(q -> rebuildPieces());

		pickBox = new ScrollBoxComponent(true, GRID_TILE + GRID_GAP);
		sheetBox = new ScrollBoxComponent(true, ROW_H);
		addChild(pickBox);

		int bottomY = listTop() + LIST_H + 12;
		int secondY = bottomY + 24;

		addDrawableChild(btnCancel = ButtonWidget.builder(Text.translatable("gui.done"), button -> closeChild())
			.dimensions(listLeft(), bottomY, LIST_W, 20).build());

		addDrawableChild(btnOwn = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.city.own"), button -> openSheets(View.OWN))
			.tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.city.own.tooltip")))
			.dimensions(listLeft(), secondY, BTN_W, 20).build());
		addDrawableChild(btnShared = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.city.shared"), button -> openSheets(View.SHARED))
			.tooltip(Tooltip.of(Text.translatable("gui.roleplayers_atlas.city.shared.tooltip")))
			.dimensions(listLeft() + BTN_W + SP, secondY, BTN_W, 20).build());

		addDrawableChild(btnSheetDropAll = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.city.sheetDropAll"), button -> {
			if (view == View.SHARED) CityPaint.dropAllImported();
			else if (dim != null) CityPaint.clearAll(dim);
			closeSheets();
		}).dimensions(listLeft(), bottomY, BTN_W, 20).build());
		addDrawableChild(btnSheetBack = ButtonWidget.builder(Text.translatable("gui.back"), button -> closeSheets())
			.dimensions(listLeft() + BTN_W + SP, bottomY, BTN_W, 20).build());

		rebuildPieces();
	}

	/**
	 * The pieces on offer. Village parts first because that is what a town is
	 * made of; everything else the atlas can draw follows, since a keep drawn as
	 * a fortress corridor is a fair thing to want.
	 */
	private void rebuildPieces() {
		pieces.clear();
		String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
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
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
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
	static Text pieceName(Identifier piece) {
		String path = piece.getPath();
		int slash = path.lastIndexOf('/');
		String leaf = slash < 0 ? path : path.substring(slash + 1);
		String key = "tile.roleplayers_atlas.piece." + leaf;
		Text translated = Text.translatable(key);
		if (!translated.getString().equals(key)) return translated;
		return Text.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(leaf.replaceAll("[_-]", " ")));
	}

	/** Where it comes from – a plains village, a nether fortress – so two wells can be told apart. */
	static Text familyName(Identifier piece) {
		String family = pieceFamily(piece);
		String key = "tile.roleplayers_atlas.family." + family.replace('/', '.');
		Text translated = Text.translatable(key);
		if (!translated.getString().equals(key)) return translated;
		return Text.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(family.replaceAll("[/_-]", " ")));
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
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			TileTexture texture = textureOf(piece);
			if (texture != null) BiomeModal.drawTile(context, texture, x, y, GRID_TILE);
			if (piece.equals(held())) context.drawStrokedRectangle(x - 1, y - 1, GRID_TILE + 2, GRID_TILE + 2, 0xFF8FB4D8);
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) {
				context.drawStrokedRectangle(x - 1, y - 1, GRID_TILE + 2, GRID_TILE + 2, 0xFFFFE8B0);
			}
			context.disableScissor();
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubled) {
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

		abstract void drawContent(DrawContext context, int x, int y);

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
			int x = getGuiX();
			int y = getGuiY();
			int top = listTop();
			int bottom = top + LIST_H;
			if (y + getHeight() <= top || y >= bottom) return;
			context.enableScissor(x, Math.max(y, top), x + getWidth(), Math.min(y + getHeight(), bottom));
			if (!isClipped && isMouseOver(mouseX, mouseY) && mouseY >= top && mouseY < bottom) context.fill(x, y, x + getWidth(), y + getHeight(), 0x22FFFFFF);
			drawContent(context, x, y);
			Text drop = Text.translatable("gui.roleplayers_atlas.biomes.sheetDrop");
			context.drawText(textRenderer, drop, x + getWidth() - textRenderer.getWidth(drop) - 3, y + 6, 0xFFC8A05A, true);
			context.disableScissor();
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubled) {
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
		void drawContent(DrawContext context, int x, int y) {
			TileTexture texture = textureOf(piece);
			if (texture != null) BiomeModal.drawTile(context, texture, x + 2, y + 2, TILE_SIZE);
			int textX = x + TILE_SIZE + 6;
			Text amount = Text.translatable("gui.roleplayers_atlas.city.ownCount", familyName(piece), count);
			int amountW = textRenderer.getWidth(amount);
			context.drawText(textRenderer, textRenderer.trimToWidth(pieceName(piece).getString(), getWidth() - 56 - TILE_SIZE - amountW), textX, y + 2, 0xFFF0E4C8, true);
			context.drawText(textRenderer, amount, x + getWidth() - 50 - amountW, y + 2, 0xFF9A8C70, true);
			// The name it goes by underneath, dim, the same as in the biome window.
			context.drawText(textRenderer, textRenderer.trimToWidth(piece.toString(), getWidth() - 56 - TILE_SIZE), textX, y + 11, 0xFF7A6E58, true);
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
		void drawContent(DrawContext context, int x, int y) {
			context.drawText(textRenderer, textRenderer.trimToWidth(author, getWidth() - 60), x + 4, y + 2, 0xFFF0E4C8, true);
			context.drawText(textRenderer, Text.translatable("gui.roleplayers_atlas.city.sharedCount", CityPaint.importedCount(author)), x + 4, y + 11, 0xFF9A8C70, true);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, switch (view) {
			case OWN -> Text.translatable("gui.roleplayers_atlas.city.ownTitle");
			case SHARED -> Text.translatable("gui.roleplayers_atlas.city.sharedTitle");
			case PICK -> Text.translatable("gui.roleplayers_atlas.city.title");
		}, listTop() - ROW_GAP - 12 - 12, 0xDDDDDD, true);

		boolean picking = view == View.PICK;
		searchField.setVisible(picking);
		btnCancel.visible = picking;
		btnOwn.visible = picking;
		btnOwn.active = dim != null && CityPaint.ownCount(dim) > 0;
		btnOwn.setMessage(Text.translatable("gui.roleplayers_atlas.city.own", dim == null ? 0 : CityPaint.ownCount(dim)));
		btnShared.visible = picking;
		btnShared.active = CityPaint.hasImported();
		btnShared.setMessage(Text.translatable("gui.roleplayers_atlas.city.shared", CityPaint.importedAuthors().size()));
		btnSheetBack.visible = !picking;
		btnSheetDropAll.visible = !picking;

		context.fillGradient(listLeft(), listTop(), listLeft() + LIST_W, listTop() + LIST_H, 0x66101010, 0x77101010);
		if (picking) {
			searchField.render(context, mouseX, mouseY, partialTick);
			btnCancel.render(context, mouseX, mouseY, partialTick);
			btnOwn.render(context, mouseX, mouseY, partialTick);
			btnShared.render(context, mouseX, mouseY, partialTick);
			if (pieces.isEmpty()) {
				drawCentered(context, Text.translatable("gui.roleplayers_atlas.city.none"), listTop() + LIST_H / 2 - 4, 0xAAAAAA, true);
			}
		} else {
			btnSheetBack.render(context, mouseX, mouseY, partialTick);
			btnSheetDropAll.render(context, mouseX, mouseY, partialTick);
		}
		super.render(context, mouseX, mouseY, partialTick);

		if (picking) {
			for (Component child : pickBox.getViewport().getChildren()) {
				for (Component row : child.getChildren()) {
					if (row instanceof PieceButton piece && !piece.isClipped && piece.isMouseOver(mouseX, mouseY) && mouseY >= listTop() && mouseY < listTop() + LIST_H) {
						context.drawTooltip(textRenderer, List.of(
							pieceName(piece.piece),
							familyName(piece.piece).copy().formatted(net.minecraft.util.Formatting.GRAY),
							Text.literal(piece.piece.toString()).formatted(net.minecraft.util.Formatting.DARK_GRAY)
						), mouseX, mouseY);
					}
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
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
	public boolean keyPressed(KeyInput input) {
		int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			if (view == View.PICK) closeChild();
			else closeSheets();
			return true;
		}
		return super.keyPressed(input) || searchField.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		char chr = (char) input.codepoint();
		int modifiers = input.modifiers();
		return super.charTyped(input) || searchField.charTyped(input);
	}

}
