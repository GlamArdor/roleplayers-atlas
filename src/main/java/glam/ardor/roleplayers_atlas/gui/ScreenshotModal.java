package glam.ardor.roleplayers_atlas.gui;

import net.minecraft.client.input.KeyInput;
import glam.ardor.roleplayers_atlas.ParchmentExport;
import net.minecraft.client.input.KeyInput;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import net.minecraft.client.input.KeyInput;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.input.KeyInput;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import net.minecraft.client.input.KeyInput;
import java.nio.file.Path;

/**
 * Map snapshot dialog: save the current map view or the whole explored map,
 * with per-category checkboxes for markers, zones, routes and inscriptions.
 */
public class ScreenshotModal extends Component {
	private static boolean includeMarkers = true;
	private static boolean includeZones = true;
	private static boolean includeRoutes = true;
	private static boolean includeLabels = true;
	// Scroll furniture – off by default, since a plain map image is still the
	// common case and the border changes the picture's size.
	private static boolean stampAuthor = false;
	private static boolean stampTime = false;
	private static boolean stampFrame = false;

	private WorldAtlasData data;

	private ButtonWidget btnMarkers;
	private ButtonWidget btnZones;
	private ButtonWidget btnRoutes;
	private ButtonWidget btnLabels;
	private ButtonWidget btnAuthor;
	private ButtonWidget btnTime;
	private ButtonWidget btnFrame;
	private ButtonWidget btnView;
	private ButtonWidget btnFull;
	private ButtonWidget btnCancel;

	void setData(WorldAtlasData data) {
		this.data = data;
	}

	private static Text toggleText(String key, boolean value) {
		return Text.translatable(key, Text.translatable(value ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		int left = this.width / 2 - 102;
		int right = this.width / 2 + 2;
		addDrawableChild(btnMarkers = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.markers", includeMarkers), button -> {
			includeMarkers = !includeMarkers;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.markers", includeMarkers));
		}).dimensions(left, this.height / 2 - 34, 100, 20).build());
		addDrawableChild(btnZones = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.zones", includeZones), button -> {
			includeZones = !includeZones;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.zones", includeZones));
		}).dimensions(right, this.height / 2 - 34, 100, 20).build());
		addDrawableChild(btnRoutes = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.routes", includeRoutes), button -> {
			includeRoutes = !includeRoutes;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.routes", includeRoutes));
		}).dimensions(left, this.height / 2 - 10, 100, 20).build());
		addDrawableChild(btnLabels = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.labels", includeLabels), button -> {
			includeLabels = !includeLabels;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.labels", includeLabels));
		}).dimensions(right, this.height / 2 - 10, 100, 20).build());

		addDrawableChild(btnAuthor = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.author", stampAuthor), button -> {
			stampAuthor = !stampAuthor;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.author", stampAuthor));
		}).dimensions(left, this.height / 2 + 14, 100, 20).build());
		addDrawableChild(btnTime = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.time", stampTime), button -> {
			stampTime = !stampTime;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.time", stampTime));
		}).dimensions(right, this.height / 2 + 14, 100, 20).build());
		addDrawableChild(btnFrame = ButtonWidget.builder(toggleText("gui.roleplayers_atlas.screenshot.frame", stampFrame), button -> {
			stampFrame = !stampFrame;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.frame", stampFrame));
		}).dimensions(this.width / 2 - 102, this.height / 2 + 38, 204, 20).build());

		addDrawableChild(btnView = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.screenshot.view"), button -> {
			ParchmentExport.setDecoration(stampAuthor, stampTime, stampFrame);
			if (getParent() instanceof AtlasScreen screen) screen.scheduleViewCapture(includeMarkers, includeZones, includeRoutes, includeLabels);
			closeChild();
		}).dimensions(this.width / 2 - 102, this.height / 2 + 66, 204, 20).build());
		addDrawableChild(btnFull = ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.screenshot.full"), button -> {
			// Stitched from real in-game renders – pixel identical to the map.
			ParchmentExport.setDecoration(stampAuthor, stampTime, stampFrame);
			if (getParent() instanceof AtlasScreen screen) screen.scheduleFullCapture(includeMarkers, includeZones, includeRoutes, includeLabels);
			closeChild();
		}).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.roleplayers_atlas.screenshot.fullWarning"))).dimensions(this.width / 2 - 102, this.height / 2 + 90, 204, 20).build());
		addDrawableChild(btnCancel = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> closeChild())
			.dimensions(this.width / 2 - 102, this.height / 2 + 118, 204, 20).build());
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			closeChild();
			return true;
		}
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			btnView.onPress(input);
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, Text.translatable("gui.roleplayers_atlas.screenshot"), this.height / 2 - 56, 0xDDDDDD, true);
		btnMarkers.render(context, mouseX, mouseY, partialTick);
		btnZones.render(context, mouseX, mouseY, partialTick);
		btnRoutes.render(context, mouseX, mouseY, partialTick);
		btnLabels.render(context, mouseX, mouseY, partialTick);
		btnAuthor.render(context, mouseX, mouseY, partialTick);
		btnTime.render(context, mouseX, mouseY, partialTick);
		btnFrame.render(context, mouseX, mouseY, partialTick);
		btnView.render(context, mouseX, mouseY, partialTick);
		btnFull.render(context, mouseX, mouseY, partialTick);
		btnCancel.render(context, mouseX, mouseY, partialTick);
		super.render(context, mouseX, mouseY, partialTick);
	}
}
