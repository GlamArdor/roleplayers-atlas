package glam.ardor.roleplayers_atlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasComponents;
import glam.ardor.roleplayers_atlas.MarkerLayers;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import glam.ardor.roleplayers_atlas.gui.core.ScrollBoxComponent;
import glam.ardor.roleplayers_atlas.gui.core.ToggleButtonRadioGroup;
import glam.ardor.roleplayers_atlas.util.ColorUtil;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.DyeColor;

/**
 * Creation/editing dialog for a player-defined marker layer: pick a name and
 * a color. Deleting a layer moves its landmarks back to the personal layer.
 */
public class LayerModal extends Component {
	private static final int BTN_W = 120;
	private static final int SP = 8;
	private static final int TYPE_SPACING = 1;

	private WorldSummary summary;
	private WorldAtlasData worldAtlasData;
	private MarkerLayers.MapLayer editing;

	private DyeColor selectedColor = DyeColor.WHITE;
	private EditBox nameField;
	private Button btnDone;
	private Button btnDelete;
	private Button btnDeleteAll;
	private Button btnCancel;
	private ScrollBoxComponent colorScrollBox;
	private ToggleButtonRadioGroup<TexturePreviewButton<DyeColor>> colorRadioGroup;
	private final Map<DyeColor, TexturePreviewButton<DyeColor>> colorButtons = new LinkedHashMap<>();
	private String pendingName = "";

	void setData(WorldSummary summary, WorldAtlasData worldAtlasData, MarkerLayers.MapLayer editing) {
		this.summary = summary;
		this.worldAtlasData = worldAtlasData;
		this.editing = editing;
		this.pendingName = editing == null ? "" : editing.name();
		this.selectedColor = DyeColor.WHITE;
		if (editing != null) {
			for (DyeColor color : DyeColor.values()) {
				if (color.getTextureDiffuseColor() == editing.color()) selectedColor = color;
			}
		}
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		nameField = new EditBox(Minecraft.getInstance().font, (this.width - 200) / 2, this.height / 2 - 38, 200, 18, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.layerModal.name"));
		nameField.setEditable(true);
		nameField.setCanLoseFocus(true);
		nameField.setFocused(true);
		nameField.setHint(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.layerModal.name"));
		nameField.setValue(pendingName);

		colorScrollBox = new ScrollBoxComponent(false, (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING));
		this.addChild(colorScrollBox);
		int colorsOnScreen = Math.min(DyeColor.values().length, 7);
		int colorScrollWidth = colorsOnScreen * (TexturePreviewButton.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
		colorScrollBox.getViewport().setSize(colorScrollWidth, TexturePreviewButton.FRAME_SIZE + TYPE_SPACING);
		colorScrollBox.setGuiCoords((this.width - colorScrollWidth) / 2, this.height / 2 - 10);

		colorRadioGroup = new ToggleButtonRadioGroup<>();
		colorRadioGroup.addListener(button -> selectedColor = button.getValue());
		colorButtons.clear();
		int colorContentX = 0;
		for (DyeColor color : DyeColor.values()) {
			TexturePreviewButton<DyeColor> colorGui = new TexturePreviewButton<>(color, BookmarkButton.TEXTURE_LEFT, BookmarkButton.WIDTH, BookmarkButton.HEIGHT, BookmarkButton.HEIGHT, ColorUtil.componentsFromRgb(color.getFireworkColor()));
			colorButtons.put(color, colorGui);
			colorRadioGroup.addButton(colorGui);
			colorScrollBox.getViewport().addContent(colorGui).setRelativeX(colorContentX);
			colorContentX += TexturePreviewButton.FRAME_SIZE + TYPE_SPACING;
		}
		colorRadioGroup.setSelectedButton(colorButtons.get(selectedColor));

		int bottomY = this.height / 2 + 34;
		addRenderableWidget(btnDone = Button.builder(net.minecraft.network.chat.Component.translatable("gui.done"), button -> doSave())
			.bounds(this.width / 2 - BTN_W - SP / 2, bottomY, BTN_W, 20).build());
		addRenderableWidget(btnCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.cancel"), button -> closeChild())
			.bounds(this.width / 2 + SP / 2, bottomY, BTN_W, 20).build());
		int deleteY = bottomY + 24;
		addRenderableWidget(btnDelete = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.layerModal.delete"), button -> doDelete(false))
			.bounds(this.width / 2 - BTN_W - SP / 2, deleteY, BTN_W, 20).build());
		addRenderableWidget(btnDeleteAll = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.layerModal.deleteAll"), button -> doDelete(true))
			.bounds(this.width / 2 + SP / 2, deleteY, BTN_W, 20).build());
	}

	private void doSave() {
		String name = nameField.getValue().trim();
		if (name.isBlank()) return;
		String id = editing != null ? editing.id() : "layer_" + Integer.toHexString((name + System.nanoTime()).hashCode());
		MarkerLayers.put(new MarkerLayers.MapLayer(id, name, selectedColor.getTextureDiffuseColor()));
		closeChild();
	}

	private void doDelete(boolean withMarkers) {
		if (editing == null) {
			closeChild();
			return;
		}
		if (summary != null && summary.landmarks() != null && worldAtlasData != null) {
			List<Landmark> affected = new ArrayList<>();
			for (Landmark landmark : worldAtlasData.getEditableLandmarks().keySet()) {
				if (editing.id().equals(RoleplayersAtlas.layerOf(landmark))) affected.add(landmark);
			}
			for (Landmark landmark : affected) {
				summary.landmarks().remove(landmark.owner(), landmark.id());
				if (withMarkers) {
					// Deleted together with the layer.
					RoleplayersAtlas.trackedMarkers.remove(RoleplayersAtlas.trackKey(landmark));
				} else {
					// Landmarks of the deleted layer fall back to the personal layer.
					summary.landmarks().put(WorldAtlasData.copyLandmarkWith(landmark, landmark.id(), copy -> copy.set(AtlasComponents.LAYER, MarkerLayers.DEFAULT_ID)));
				}
			}
			if (withMarkers) glam.ardor.roleplayers_atlas.TrackedMarkersStore.save();
		}
		MarkerLayers.remove(editing.id());
		closeChild();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, net.minecraft.network.chat.Component.translatable(editing == null ? "gui.roleplayers_atlas.layerModal.titleNew" : "gui.roleplayers_atlas.layerModal.titleEdit"), this.height / 2 - 58, 0xDDDDDD, true);
		btnDone.active = !nameField.getValue().isBlank();
		btnDelete.visible = editing != null;
		btnDeleteAll.visible = editing != null;
		nameField.extractRenderState(context, mouseX, mouseY, partialTick);
		btnDone.extractRenderState(context, mouseX, mouseY, partialTick);
		if (btnDelete.visible) btnDelete.extractRenderState(context, mouseX, mouseY, partialTick);
		if (btnDeleteAll.visible) btnDeleteAll.extractRenderState(context, mouseX, mouseY, partialTick);
		btnCancel.extractRenderState(context, mouseX, mouseY, partialTick);
		context.fillGradient(colorScrollBox.getGuiX() + 1, colorScrollBox.getGuiY() + 1,
			colorScrollBox.getGuiX() + colorScrollBox.getWidth(),
			colorScrollBox.getGuiY() + colorScrollBox.getHeight(),
			0x88101010, 0x99101010);
		super.extractRenderState(context, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x(), mouseY = click.y();
		int button = click.button();
		return super.mouseClicked(click, doubled) || nameField.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int keyCode = input.key(), scanCode = input.keycode(), modifiers = input.modifiers();
		if (keyCode == InputConstants.KEY_ESCAPE) {
			closeChild();
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			if (btnDone.active) btnDone.onPress(input);
			return true;
		}
		return super.keyPressed(input) || nameField.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		char chr = (char) input.codepoint();
		return super.charTyped(input) || nameField.charTyped(input);
	}

	@Override
	public void closeChild() {
		super.closeChild();
		if (colorScrollBox != null) colorScrollBox.closeChild();
	}
}
