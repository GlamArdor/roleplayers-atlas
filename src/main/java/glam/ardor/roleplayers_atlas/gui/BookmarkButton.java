package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.gui.core.ToggleButtonComponent;
import glam.ardor.roleplayers_atlas.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

public class BookmarkButton extends ToggleButtonComponent {
	public static final Identifier TEXTURE_LEFT = RoleplayersAtlas.id("textures/gui/bookmark_left.png");
	public static final Identifier TEXTURE_RIGHT = RoleplayersAtlas.id("textures/gui/bookmark_right.png");
	public static final Identifier TEXTURE_TOP = RoleplayersAtlas.id("textures/gui/bookmark_top.png");
	public static final Identifier TEXTURE_BOTTOM = RoleplayersAtlas.id("textures/gui/bookmark_bottom.png");
	public static final int WIDTH = 24;
	public static final int HEIGHT = 18;

	protected Component title;
	protected Identifier iconTexture;
	protected final float[] backgroundTint;
	protected final float[] iconTint;
	protected final int iconWidth;
	protected final int iconHeight;
	protected final boolean backwards;
	protected final boolean vertical;
	protected final Identifier backgroundTexture;

	public BookmarkButton(Identifier backgroundTexture, Component title, Identifier iconTexture, @Nullable Integer backgroundTint, @Nullable Integer iconTint, int iconWidth, int iconHeight, boolean backwards, boolean vertical) {
		super(false);
		this.backgroundTexture = backgroundTexture;
		this.title = title;
		this.iconTexture = iconTexture;
		this.backgroundTint = backgroundTint == null ? null : ColorUtil.componentsFromRgb(backgroundTint);
		this.iconWidth = iconWidth;
		this.iconHeight = iconHeight;
		this.iconTint = iconTint == null ? null : ColorUtil.componentsFromRgb(iconTint);
		this.backwards = backwards;
		this.vertical = vertical;
		setTitle(title);
		setSize(vertical ? HEIGHT : WIDTH, vertical ? WIDTH : HEIGHT);
	}

	public BookmarkButton(Component title, Identifier iconTexture, @Nullable Integer backgroundTint, @Nullable Integer iconTint, int iconWidth, int iconHeight, boolean backwards, boolean vertical) {
		this(vertical ? (backwards ? TEXTURE_TOP : TEXTURE_BOTTOM) : (backwards ? TEXTURE_LEFT : TEXTURE_RIGHT), title, iconTexture, backgroundTint, iconTint, iconWidth, iconHeight, backwards, vertical);
	}

	public void setIconTexture(Identifier iconTexture) {
		this.iconTexture = iconTexture;
	}

	public Component getTitle() {
		return title;
	}

	public void setTitle(Component title) {
		this.title = title;
	}

	public void drawIcon(GuiGraphics context, int x, int y) {
		int tint = iconTint != null ? ARGB.color(255, (int) (iconTint[0] * 255), (int) (iconTint[1] * 255), (int) (iconTint[2] * 255)) : 0xFFFFFFFF;
		context.blit(RenderPipelines.GUI_TEXTURED, iconTexture, x, y, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight, tint);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
		boolean mouseOver = isMouseOver(mouseX, mouseY);
		boolean isExtended = mouseOver || isSelected();

		int backgroundArgb = backgroundTint != null ? ARGB.color(255, (int) (backgroundTint[0] * 255), (int) (backgroundTint[1] * 255), (int) (backgroundTint[2] * 255)) : 0xFFFFFFFF;
		context.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture, getGuiX(), getGuiY(), !vertical || isExtended ? 0 : HEIGHT, vertical || isExtended ? 0 : HEIGHT, vertical ? HEIGHT : WIDTH, vertical ? WIDTH : HEIGHT, vertical ? HEIGHT : WIDTH, vertical ? WIDTH : HEIGHT, vertical ? HEIGHT * 2 : WIDTH, vertical ? WIDTH : HEIGHT * 2, backgroundArgb);

		if (iconTexture != null) {
			int iconX = getGuiX() + (!vertical ? (10 - iconWidth / 2 + (isExtended ? (backwards ? 3 : 1) : (backwards ? 4 : 0))) : (9 - iconHeight / 2));
			int iconY = getGuiY() + (vertical ? (10 - iconWidth / 2 + (isExtended ? (backwards ? 3 : 1) : (backwards ? 4 : 0))) : (9 - iconHeight / 2));
			drawIcon(context, iconX, iconY);
		}

		renderTooltip(context, mouseX, mouseY, partialTick, mouseOver);
	}

	/** How the tool is used, shown greyed under its name. */
	private Component hint = null;
	/** The title and the wrapped hint, worked out on first use and kept. */
	private java.util.List<Component> tooltipLines = null;

	public void setHint(Component text) {
		this.hint = text;
		this.tooltipLines = null;
	}

	public void renderTooltip(GuiGraphics context, int mouseX, int mouseY, float partialTick, boolean mouseOver) {
		if (!mouseOver || title.getString().isEmpty()) return;
		if (hint == null) {
			context.setTooltipForNextFrame(font, title, mouseX, mouseY);
			return;
		}
		// Wrapped here rather than when the hint is set: buttons are built before
		// the screen has a font to measure with.
		if (tooltipLines == null) {
			java.util.List<Component> lines = new java.util.ArrayList<>();
			lines.add(title);
			for (net.minecraft.network.chat.FormattedText line : font.getSplitter().splitLines(hint.getString(), 190, net.minecraft.network.chat.Style.EMPTY)) {
				lines.add(Component.literal(line.getString()).withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			tooltipLines = lines;
		}
		context.setComponentTooltipForNextFrame(font, tooltipLines, mouseX, mouseY);
	}
}
