package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.AtlasConfig;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The settings screen (opened from Mod Menu): every option in one scrolling
 * list, grouped under headers, with a tooltip on each row.
 * <p>
 * Rows write straight into the config fields; the file is written once, when
 * the screen closes. Anything that changes what a tile should look like
 * triggers a redraw of the land already on the map at that same moment, or the
 * setting would appear to do nothing at all.
 */
public class AtlasConfigScreen extends Screen {
	private static final int ROW_WIDTH = 310;

	private final Screen parent;
	private final AtlasConfig config = RoleplayersAtlas.CONFIG;
	// Snapshots from the moment the screen opened, not from the last rebuild —
	// init() runs again on every resize and reset.
	private final AtlasConfig.FallbackHandling wasFallback;
	private final int[] wasElevations;
	// A grave's epitaph carries its date in the marker's own name, so a change
	// of reckoning has to go back and rewrite the ones already on the page.
	private final AtlasConfig.Reckoning wasReckoning;
	private final boolean wasMarkDate;
	private boolean resetArmed = false;

	public AtlasConfigScreen(Screen parent) {
		super(Text.translatable("gui.roleplayers_atlas.config"));
		this.parent = parent;
		this.wasFallback = config.fallbackFailHandling;
		this.wasElevations = new int[]{config.elevationLow, config.elevationMid, config.elevationHigh, config.elevationPeak};
		this.wasReckoning = config.reckoning;
		this.wasMarkDate = config.showMarkDate;
	}

	@Override
	protected void init() {
		OptionList list = new OptionList(this.client, this.width, this.height - 96, 40, 25);

		list.addHeader(Text.translatable("roleplayers_atlas.category.map"));
		list.addWidget(toggle("fullscreen", () -> config.fullscreen, v -> config.fullscreen = v));
		list.addWidget(toggle("keepZoom", () -> config.keepZoom, v -> config.keepZoom = v));
		list.addWidget(toggle("keepOffset", () -> config.keepOffset, v -> config.keepOffset = v));
		list.addWidget(enumButton("emptyHandling", AtlasConfig.EmptyHandling.values(), () -> config.emptyHandling,
			v -> config.emptyHandling = v, v -> "gui.roleplayers_atlas.config.emptyHandling." + v.name().toLowerCase()));
		list.addWidget(toggle("stabilizeHeldMap", () -> config.stabilizeHeldMap, v -> config.stabilizeHeldMap = v));
		list.addWidget(intSlider("handheldScale", config.handheldScale, 50, 200, v -> config.handheldScale = v, this::percent));
		list.addWidget(toggle("showHints", () -> config.showHints, v -> config.showHints = v));
		list.addWidget(intSlider("mapScale", config.mapScale, -2, 10, v -> config.mapScale = v, v -> Text.literal(Integer.toString(v))));
		list.addWidget(intSlider("maxTileChunks", config.maxTileChunks, 0, 6, v -> config.maxTileChunks = v, v -> Text.literal(Integer.toString(v))));
		list.addWidget(intSlider("maxTilePixels", config.maxTilePixels, 0, 3, v -> config.maxTilePixels = v, v -> Text.literal(Integer.toString(v))));
		list.addWidget(intSlider("chunkTickLimit", config.chunkTickLimit, 1, 1000, v -> config.chunkTickLimit = v, v -> Text.literal(Integer.toString(v))));

		list.addHeader(Text.translatable("roleplayers_atlas.category.marks"));
		list.addWidget(enumButton("quickMark", AtlasConfig.QuickMark.values(), () -> config.quickMark,
			v -> config.quickMark = v, v -> "gui.roleplayers_atlas.config.quickMark." + v.name().toLowerCase()));
		// Whatever icons the resource pack actually ships, read at the moment the
		// screen opens rather than listed here and left to rot.
		List<String> icons = glam.ardor.roleplayers_atlas.QuickMark.icons();
		if (!icons.isEmpty()) {
			list.addWidget(cycleButton("quickMarkIcon",
				() -> Text.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(config.quickMarkIcon.replace('_', ' '))),
				() -> {
					int at = icons.indexOf(config.quickMarkIcon);
					config.quickMarkIcon = icons.get((at + 1) % icons.size());
				}));
		}
		list.addWidget(intSlider("quickMarkRange", config.quickMarkRange, 16, 512, v -> config.quickMarkRange = v, this::blocks));
		list.addWidget(enumButton("markerSort", AtlasConfig.MarkerSort.values(), () -> config.markerSort,
			v -> config.markerSort = v, v -> "gui.roleplayers_atlas.sort." + v.name().toLowerCase()));
		list.addWidget(enumButton("reckoning", AtlasConfig.Reckoning.values(), () -> config.reckoning,
			v -> config.reckoning = v, v -> "gui.roleplayers_atlas.config.reckoning." + v.name().toLowerCase()));
		list.addWidget(toggle("showMarkDate", () -> config.showMarkDate, v -> config.showMarkDate = v));
		list.addWidget(toggle("showRealDate", () -> config.showRealDate, v -> config.showRealDate = v));
		list.addWidget(toggle("deathMarkers", () -> config.deathMarkers, v -> config.deathMarkers = v));
		list.addWidget(toggle("hideDeathsByDefault", () -> config.hideDeathsByDefault, v -> config.hideDeathsByDefault = v));
		list.addWidget(enumButton("graveStyle", AtlasConfig.GraveStyle.values(), () -> config.graveStyle,
			v -> config.graveStyle = v, v -> "gui.roleplayers_atlas.config.graveStyle." + v.name().toLowerCase()));
		list.addWidget(toggle("spawnMarker", () -> config.spawnMarker, v -> config.spawnMarker = v));
		list.addWidget(toggle("hearthFollowsBeds", () -> config.hearthFollowsBeds, v -> config.hearthFollowsBeds = v));
		list.addWidget(intSlider("guideArrowOpacity", config.guideArrowOpacity, 10, 100, v -> config.guideArrowOpacity = v, this::percent));
		list.addWidget(toggle("clearTrackingOnArrival", () -> config.clearTrackingOnArrival, v -> config.clearTrackingOnArrival = v));

		list.addHeader(Text.translatable("roleplayers_atlas.category.titles"));
		list.addWidget(toggle("zoneTitles", () -> config.zoneTitles, v -> config.zoneTitles = v));
		list.addWidget(toggle("zoneTitleSound", () -> config.zoneTitleSound, v -> config.zoneTitleSound = v));
		list.addWidget(intSlider("zoneTitleSeconds", config.zoneTitleSeconds, 1, 30, v -> config.zoneTitleSeconds = v, this::seconds));
		list.addWidget(intSlider("zoneTitleCooldown", config.zoneTitleCooldown, 0, 600, v -> config.zoneTitleCooldown = v, this::seconds));

		list.addHeader(Text.translatable("roleplayers_atlas.category.terrain"));
		list.addWidget(intSlider("elevationLow", config.elevationLow, -64, 320, v -> config.elevationLow = v, this::blocks));
		list.addWidget(intSlider("elevationMid", config.elevationMid, -64, 320, v -> config.elevationMid = v, this::blocks));
		list.addWidget(intSlider("elevationHigh", config.elevationHigh, -64, 320, v -> config.elevationHigh = v, this::blocks));
		list.addWidget(intSlider("elevationPeak", config.elevationPeak, -64, 320, v -> config.elevationPeak = v, this::blocks));
		// CRASH is a resource pack author's tool — it stops the game dead on a
		// biome with no texture. Left out of the cycle so a player can't pick it
		// by accident; the config file still takes it.
		AtlasConfig.FallbackHandling[] fallbacks = Arrays.stream(AtlasConfig.FallbackHandling.values())
			.filter(v -> v != AtlasConfig.FallbackHandling.CRASH)
			.toArray(AtlasConfig.FallbackHandling[]::new);
		list.addWidget(enumButton("fallbackFailHandling", fallbacks,
			() -> config.fallbackFailHandling == AtlasConfig.FallbackHandling.CRASH ? AtlasConfig.FallbackHandling.MISSING : config.fallbackFailHandling,
			v -> config.fallbackFailHandling = v, v -> "gui.roleplayers_atlas.config.fallbackFailHandling." + v.name().toLowerCase()));

		addDrawableChild(list);

		// Two clicks: undoing every setting at once is easy to hit by accident.
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.roleplayers_atlas.config.reset"), button -> {
			if (!resetArmed) {
				resetArmed = true;
				button.setMessage(Text.translatable("gui.roleplayers_atlas.config.resetConfirm"));
				return;
			}
			config.resetToDefaults();
			resetArmed = false;
			clearAndInit();
		}).dimensions(this.width / 2 - 154, this.height - 30, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(this.width / 2 + 4, this.height - 30, 150, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		config.saveFields();
		boolean elevationsChanged = wasElevations[0] != config.elevationLow
			|| wasElevations[1] != config.elevationMid
			|| wasElevations[2] != config.elevationHigh
			|| wasElevations[3] != config.elevationPeak;
		if (config.fallbackFailHandling != wasFallback || elevationsChanged) WorldAtlasData.retileAll();
		if (config.reckoning != wasReckoning || config.showMarkDate != wasMarkDate) WorldAtlasData.redateGraves();
		this.client.setScreen(parent);
	}

	private Text blocks(int value) {
		return Text.translatable("config.roleplayers_atlas.blocks", value);
	}

	private Text percent(int value) {
		return Text.translatable("config.roleplayers_atlas.percent", value);
	}

	private Text seconds(int value) {
		return Text.translatable("config.roleplayers_atlas.seconds", value);
	}

	private static Text label(String key, Text value) {
		return Text.translatable("config.roleplayers_atlas." + key).copy().append(": ").append(value);
	}

	private ClickableWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycleButton(key,
			() -> getter.get() ? ScreenTexts.ON : ScreenTexts.OFF,
			() -> setter.accept(!getter.get()));
	}

	private <E extends Enum<E>> ClickableWidget enumButton(String key, E[] values, Supplier<E> getter, Consumer<E> setter, Function<E, String> nameKey) {
		return cycleButton(key,
			() -> Text.translatable(nameKey.apply(getter.get())),
			() -> setter.accept(values[(indexOf(values, getter.get()) + 1) % values.length]));
	}

	private static <E> int indexOf(E[] values, E value) {
		for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
		return -1;
	}

	private ClickableWidget cycleButton(String key, Supplier<Text> value, Runnable onClick) {
		ButtonWidget button = ButtonWidget.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).dimensions(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.of(Text.translatable("config.roleplayers_atlas." + key + ".tooltip")));
		return button;
	}

	private ClickableWidget intSlider(String key, int current, int min, int max, Consumer<Integer> setter, IntFunction<Text> display) {
		return new OptionSlider(key, current, min, max, setter, display);
	}

	/** Slider over an integer range that writes straight back into the config field. */
	private static class OptionSlider extends SliderWidget {
		private final String key;
		private final int min;
		private final int max;
		private final Consumer<Integer> setter;
		private final IntFunction<Text> display;

		OptionSlider(String key, int current, int min, int max, Consumer<Integer> setter, IntFunction<Text> display) {
			super(0, 0, ROW_WIDTH, 20, Text.empty(), MathHelper.clamp((current - min) / (double) (max - min), 0.0, 1.0));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.of(Text.translatable("config.roleplayers_atlas." + key + ".tooltip")));
			updateMessage();
		}

		private int currentValue() {
			return MathHelper.clamp((int) Math.round(min + (max - min) * this.value), min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(key, display.apply(currentValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}
	}

	private static class OptionList extends ElementListWidget<OptionList.Entry> {
		OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Text text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		static class WidgetEntry extends Entry {
			private final ClickableWidget widget;

			WidgetEntry(ClickableWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.render(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends Entry {
			private final Text text;

			HeaderEntry(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				MinecraftClient client = MinecraftClient.getInstance();
				context.drawCenteredTextWithShadow(client.textRenderer, text,
					x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}
	}
}
