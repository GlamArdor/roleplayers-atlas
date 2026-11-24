package glam.ardor.roleplayers_atlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import glam.ardor.roleplayers_atlas.ParchmentExport;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;

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

	private Button btnMarkers;
	private Button btnZones;
	private Button btnRoutes;
	private Button btnLabels;
	private Button btnAuthor;
	private Button btnTime;
	private Button btnFrame;
	private Button btnView;
	private Button btnFull;
	private Button btnCancel;

	void setData(WorldAtlasData data) {
		this.data = data;
	}

	private static net.minecraft.network.chat.Component toggleText(String key, boolean value) {
		return net.minecraft.network.chat.Component.translatable(key, net.minecraft.network.chat.Component.translatable(value ? "gui.roleplayers_atlas.marker.zoneTitle.on" : "gui.roleplayers_atlas.marker.zoneTitle.off"));
	}

	@Override
	public void init() {
		removeAllChildren();
		super.init();

		int left = this.width / 2 - 102;
		int right = this.width / 2 + 2;
		addRenderableWidget(btnMarkers = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.markers", includeMarkers), button -> {
			includeMarkers = !includeMarkers;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.markers", includeMarkers));
		}).bounds(left, this.height / 2 - 34, 100, 20).build());
		addRenderableWidget(btnZones = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.zones", includeZones), button -> {
			includeZones = !includeZones;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.zones", includeZones));
		}).bounds(right, this.height / 2 - 34, 100, 20).build());
		addRenderableWidget(btnRoutes = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.routes", includeRoutes), button -> {
			includeRoutes = !includeRoutes;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.routes", includeRoutes));
		}).bounds(left, this.height / 2 - 10, 100, 20).build());
		addRenderableWidget(btnLabels = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.labels", includeLabels), button -> {
			includeLabels = !includeLabels;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.labels", includeLabels));
		}).bounds(right, this.height / 2 - 10, 100, 20).build());

		addRenderableWidget(btnAuthor = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.author", stampAuthor), button -> {
			stampAuthor = !stampAuthor;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.author", stampAuthor));
		}).bounds(left, this.height / 2 + 14, 100, 20).build());
		addRenderableWidget(btnTime = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.time", stampTime), button -> {
			stampTime = !stampTime;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.time", stampTime));
		}).bounds(right, this.height / 2 + 14, 100, 20).build());
		addRenderableWidget(btnFrame = Button.builder(toggleText("gui.roleplayers_atlas.screenshot.frame", stampFrame), button -> {
			stampFrame = !stampFrame;
			button.setMessage(toggleText("gui.roleplayers_atlas.screenshot.frame", stampFrame));
		}).bounds(this.width / 2 - 102, this.height / 2 + 38, 204, 20).build());

		addRenderableWidget(btnView = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.screenshot.view"), button -> {
			ParchmentExport.setDecoration(stampAuthor, stampTime, stampFrame);
			if (getParent() instanceof AtlasScreen screen) screen.scheduleViewCapture(includeMarkers, includeZones, includeRoutes, includeLabels);
			closeChild();
		}).bounds(this.width / 2 - 102, this.height / 2 + 66, 204, 20).build());
		addRenderableWidget(btnFull = Button.builder(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.screenshot.full"), button -> {
			// Stitched from real in-game renders – pixel identical to the map.
			ParchmentExport.setDecoration(stampAuthor, stampTime, stampFrame);
			if (getParent() instanceof AtlasScreen screen) screen.scheduleFullCapture(includeMarkers, includeZones, includeRoutes, includeLabels);
			closeChild();
		}).tooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.screenshot.fullWarning"))).bounds(this.width / 2 - 102, this.height / 2 + 90, 204, 20).build());
		addRenderableWidget(btnCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.cancel"), button -> closeChild())
			.bounds(this.width / 2 - 102, this.height / 2 + 118, 204, 20).build());
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int keyCode = input.key(), scanCode = input.keycode(), modifiers = input.modifiers();
		if (keyCode == InputConstants.KEY_ESCAPE) {
			closeChild();
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			btnView.onPress(input);
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
		context.fill(0, 0, this.width, this.height, 0x66000000);
		drawCentered(context, net.minecraft.network.chat.Component.translatable("gui.roleplayers_atlas.screenshot"), this.height / 2 - 56, 0xDDDDDD, true);
		btnMarkers.extractRenderState(context, mouseX, mouseY, partialTick);
		btnZones.extractRenderState(context, mouseX, mouseY, partialTick);
		btnRoutes.extractRenderState(context, mouseX, mouseY, partialTick);
		btnLabels.extractRenderState(context, mouseX, mouseY, partialTick);
		btnAuthor.extractRenderState(context, mouseX, mouseY, partialTick);
		btnTime.extractRenderState(context, mouseX, mouseY, partialTick);
		btnFrame.extractRenderState(context, mouseX, mouseY, partialTick);
		btnView.extractRenderState(context, mouseX, mouseY, partialTick);
		btnFull.extractRenderState(context, mouseX, mouseY, partialTick);
		btnCancel.extractRenderState(context, mouseX, mouseY, partialTick);
		super.extractRenderState(context, mouseX, mouseY, partialTick);
	}
}
