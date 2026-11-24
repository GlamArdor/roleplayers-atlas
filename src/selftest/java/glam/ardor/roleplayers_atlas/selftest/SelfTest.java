package glam.ardor.roleplayers_atlas.selftest;

import com.mojang.blaze3d.platform.InputConstants;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import glam.ardor.roleplayers_atlas.MapShare;
import glam.ardor.roleplayers_atlas.MapTeleport;
import glam.ardor.roleplayers_atlas.ParchmentExport;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.XaeroImport;
import glam.ardor.roleplayers_atlas.gui.AtlasConfigScreen;
import glam.ardor.roleplayers_atlas.gui.AtlasScreen;
import glam.ardor.roleplayers_atlas.gui.core.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Dev-only walk through the atlas: draws the book, opens the screen, places
 * every kind of mark through the real click handlers, exports and imports a
 * scroll, renders a parchment, teleports and imports Xaero waypoints. Every
 * step logs a verdict to run/selftest/report.txt and leaves an in-game
 * screenshot in run/screenshots/ for a human to look at.
 * <p>
 * Active only with -Droleplayers_atlas.selftest=true, and never shipped: it
 * lives in its own source set.
 */
public class SelfTest implements ClientModInitializer {
	private record Step(String name, int waitTicks, Runnable action) {
	}

	private final List<Step> steps = new ArrayList<>();
	private final List<String> report = new ArrayList<>();
	private int index = -1;
	private int wait = 0;
	private int joinedTicks = 0;
	private int idleTicks = 0;
	private boolean openedWorld = false;
	private boolean finished = false;
	private int landmarksBefore;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("roleplayers_atlas.selftest")) return;
		buildSteps();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		if (finished) return;
		if (client.level == null || client.player == null) {
			if (++idleTicks % 100 == 0) {
				Screen waiting = client.gui.screen();
				RoleplayersAtlas.LOGGER.info("[selftest] waiting for a world, screen: " + waiting);
				// Other mods' first-launch notices sit in front of quick play; wave them off.
				if (waiting != null && !waiting.getClass().getName().startsWith("net.minecraft.")) waiting.onClose();
				// Quick play is lost once such a notice was in the way; open the world ourselves.
				else if (waiting instanceof net.minecraft.client.gui.screens.TitleScreen && !openedWorld) {
					openedWorld = true;
					client.createWorldOpenFlows().openWorld("New World", () -> client.gui.setScreen(null));
				}
			}
			return;
		}
		if (++joinedTicks < 200) return; // let chunks and Surveyor settle
		if (wait-- > 0) return;
		Screen foreign = client.gui.screen();
		if (foreign != null && !foreign.getClass().getName().startsWith("net.minecraft.") && !foreign.getClass().getName().startsWith("glam.ardor.")) {
			// Another mod's welcome screen popped up in the world; close it and look again next tick.
			log("  closing " + foreign.getClass().getName());
			foreign.onClose();
			if (client.gui.screen() == foreign) client.gui.setScreen(null);
			return;
		}
		index++;
		if (index >= steps.size()) {
			finish(client);
			return;
		}
		Step step = steps.get(index);
		try {
			step.action.run();
			log("STEP " + step.name + " ran");
		} catch (Throwable t) {
			log("STEP " + step.name + " THREW " + t);
			for (StackTraceElement e : t.getStackTrace()) {
				if (report.size() > 4000) break;
				log("    at " + e);
			}
		}
		wait = step.waitTicks;
	}

	// ---------------------------------------------------------------- steps

	private void buildSteps() {
		step("setup", 40, () -> {
			// No cheats in this world, so the tp command is not in the tree yet.
			check("teleport refused without cheats", !MapTeleport.allowed());
			IntegratedServer server = mc().getSingleplayerServer();
			server.execute(() -> server.getPlayerList().op(new net.minecraft.server.players.NameAndId(server.getPlayerList().getPlayers().getFirst().getGameProfile())));
			command("time set noon");
			command("weather clear");
			command("gamerule advance_time false");
			landmarksBefore = ownLandmarks().size();
			log("  landmarks at start: " + landmarksBefore);
		});
		step("hud", 20, () -> shot("01_hud"));

		// Field mode: first press draws the book into both hands.
		step("look down", 10, () -> command("tp @s ~ ~ ~ ~ 40"));
		step("press M (draw book)", 60, () -> pressKey(InputConstants.KEY_M));
		step("book in hands", 10, () -> {
			check("hold mode active", AtlasHoldMode.isActive());
			check("book fully open", AtlasHoldMode.openProgress() >= 1.0F, "openProgress=" + AtlasHoldMode.openProgress());
			shot("02_book_both_hands");
		});
		step("give torch", 60, () -> command("item replace entity @s weapon.mainhand with minecraft:torch"));
		step("book in small hand", 10, () -> {
			check("small-hand book", AtlasHoldMode.bookInSmallHand());
			shot("03_book_small_hand");
		});
		step("clear hand", 60, () -> command("item replace entity @s weapon.mainhand with minecraft:air"));
		step("quick mark (N)", 20, () -> pressKey(InputConstants.KEY_N));
		step("quick mark placed", 10, () -> check("quick mark added", ownLandmarks().size() > landmarksBefore, "count=" + ownLandmarks().size()));

		// Second press puts the book away and opens the screen.
		step("press M (open screen)", 40, () -> pressKey(InputConstants.KEY_M));
		step("atlas screen", 10, () -> {
			check("AtlasScreen open", screen() instanceof AtlasScreen, String.valueOf(screen()));
			check("hold mode off", !AtlasHoldMode.isActive());
			shot("04_atlas_screen");
		});
		step("zoom in", 30, () -> {
			key(InputConstants.KEY_EQUALS);
			key(InputConstants.KEY_EQUALS);
		});
		step("zoomed in", 5, () -> shot("05_zoom_in"));
		step("zoom out", 30, () -> {
			for (int i = 0; i < 4; i++) key(InputConstants.KEY_MINUS);
		});
		step("zoomed out", 5, () -> shot("06_zoom_out"));
		step("zoom back", 30, () -> {
			key(InputConstants.KEY_EQUALS);
			key(InputConstants.KEY_EQUALS);
		});

		// Marker through the bookmark and a click on the map.
		step("marker bookmark", 10, () -> clickComponent(atlas().addMarkerBookmark));
		step("click map for marker", 20, () -> clickMap(-40, -20));
		step("marker modal", 10, () -> {
			EditBox field = field(atlas().markerModal, "textField");
			check("marker modal has text field", field != null);
			field.setValue("Тестовая метка");
			shot("07_marker_modal");
		});
		step("marker done", 20, () -> clickWidget(field(atlas().markerModal, "btnDone")));
		step("marker saved", 10, () -> {
			check("named marker exists", findByName("Тестовая метка") != null);
			shot("08_marker_on_map");
		});

		// Territory: paint a few chunks, finish through the bookmark.
		step("territory bookmark", 10, () -> clickComponent(atlas().addTerritoryBookmark));
		step("paint territory", 10, () -> {
			double[] c = mapPoint(30, 20);
			dragOnMap(c[0], c[1], c[0] + 40, c[1] + 25);
		});
		step("territory painted", 10, () -> shot("09_territory_painting"));
		step("finish territory", 20, () -> clickComponent(atlas().addTerritoryBookmark));
		step("territory modal", 10, () -> {
			EditBox field = field(atlas().markerModal, "textField");
			if (field != null) field.setValue("Тестовые земли");
			shot("10_territory_modal");
		});
		step("territory done", 20, () -> clickWidget(field(atlas().markerModal, "btnDone")));
		step("territory saved", 10, () -> {
			check("territory exists", findByName("Тестовые земли") != null);
			shot("11_territory_on_map");
		});

		// Pen inscription.
		step("label bookmark", 10, () -> clickComponent(atlas().addLabelBookmark));
		step("click map for label", 20, () -> clickMap(-60, 40));
		step("label modal", 10, () -> {
			EditBox field = field(atlas().markerModal, "textField");
			if (field != null) field.setValue("Пером написано");
			shot("12_label_modal");
		});
		step("label done", 20, () -> clickWidget(field(atlas().markerModal, "btnDone")));
		step("label saved", 10, () -> {
			check("inscription exists", findByName("Пером написано") != null);
			shot("13_label_on_map");
		});

		// Route through three clicks.
		step("route bookmark", 10, () -> clickComponent(atlas().addRouteBookmark));
		step("route points", 10, () -> {
			clickMap(-80, -50);
			clickMap(-20, -60);
			clickMap(20, -30);
		});
		step("finish route", 20, () -> clickComponent(atlas().addRouteBookmark));
		step("route modal", 10, () -> {
			EditBox field = field(atlas().markerModal, "textField");
			if (field != null) field.setValue("Тестовая дорога");
			shot("14_route_modal");
		});
		step("route done", 20, () -> clickWidget(field(atlas().markerModal, "btnDone")));
		step("route saved", 10, () -> {
			check("route exists", findByName("Тестовая дорога") != null);
			shot("15_route_on_map");
		});

		// Share modal opens and renders.
		// The share bookmark may sit below the fold of the side list; press it directly.
		step("share bookmark", 20, () -> atlas().shareMapBookmark.onClick());
		step("share modal", 10, () -> shot("16_share_modal"));
		step("close share", 20, () -> key(InputConstants.KEY_ESCAPE));

		// Scroll round trip through the API the modal uses.
		step("scroll export/import", 20, () -> {
			var level = mc().level;
			WorldSummary summary = SurveyorClient.tryGetSummary(level.dimension());
			List<Landmark> mine = new ArrayList<>(ownLandmarks().values());
			Path file;
			try {
				file = MapShare.export(level.dimension(), summary, level.registryAccess(), mine, true, true, true, "selftest");
			} catch (IOException e) {
				check("scroll written", false, e.toString());
				return;
			}
			check("scroll written", file != null && Files.exists(file), String.valueOf(file));
			var preview = MapShare.peek(file, level.dimension());
			check("scroll readable", preview != null);
			var result = MapShare.importFile(file, level.dimension(), summary, level.registryAccess());
			log("  import result: " + result);
		});

		// Parchment of the whole explored map.
		step("parchment", 20, () -> {
			WorldAtlasData data = WorldAtlasData.getOrCreate(mc().level.dimension());
			var image = ParchmentExport.composeFullMap(data, true, true, true, true);
			check("parchment composed", image != null);
			try {
				Path saved = ParchmentExport.save(image, "selftest");
				check("parchment saved", Files.exists(saved), saved.toString());
				log("  parchment " + image.getWidth() + "x" + image.getHeight() + " -> " + saved);
			} catch (IOException e) {
				check("parchment saved", false, e.toString());
			}
		});

		// Teleport rights come from the command tree the server sent.
		step("teleport", 60, () -> {
			check("teleport allowed after op", MapTeleport.allowed());
			var p = mc().player.blockPosition();
			MapTeleport.to(SurveyorClient.tryGetSummary(mc().level.dimension()), mc().level.dimension(), p.getX() + 40, null, p.getZ() + 40);
			startX = p.getX();
		});
		step("teleported", 10, () -> {
			int dx = mc().player.blockPosition().getX() - startX;
			check("player moved by teleport", Math.abs(dx - 40) <= 1, "dx=" + dx);
			shot("17_after_teleport");
		});

		// Close the screen and look at the HUD side: zone title of a named mark nearby.
		step("close atlas", 20, () -> {
			if (screen() != null) key(InputConstants.KEY_ESCAPE);
		});
		step("closed", 5, () -> check("screen closed", screen() == null, String.valueOf(screen())));
		step("walk to marker", 80, () -> {
			Landmark mark = findByName("Тестовая метка");
			if (mark == null) return;
			var pos = mark.get(LandmarkComponentTypes.POS);
			command("tp @s " + pos.getX() + " " + (mc().player.getBlockY() + 2) + " " + pos.getZ());
		});
		step("zone title", 10, () -> shot("18_zone_title"));

		// Field mode again, now with a tracked mark: guide arrow on the page.
		step("track marker", 10, () -> {
			Landmark mark = findByName("Тестовая дорога");
			if (mark != null) RoleplayersAtlas.trackedMarkers.add(RoleplayersAtlas.trackKey(mark));
			command("spreadplayers ~-30 ~-30 0 1 false @s");
		});
		step("look down again", 20, () -> command("tp @s ~ ~ ~ ~ 40"));
		step("draw book again", 80, () -> pressKey(InputConstants.KEY_M));
		step("book with guide", 10, () -> shot("19_book_with_guide_arrow"));
		step("escape closes book", 40, () -> {
			// Esc in field mode goes through pauseGame, which the mixin turns into "put the book away".
			mc().pauseGame(false);
		});
		step("book closed", 10, () -> {
			check("book put away by Esc", !AtlasHoldMode.isActive() || AtlasHoldMode.isClosing());
			check("no pause menu", screen() == null, String.valueOf(screen()));
			if (screen() != null) mc().gui.setScreen(null);
		});

		// Settings screen.
		step("settings", 20, () -> mc().gui.setScreen(new AtlasConfigScreen(null)));
		step("settings shown", 10, () -> {
			check("settings screen open", screen() instanceof AtlasConfigScreen, String.valueOf(screen()));
			shot("20_settings");
			mc().gui.setScreen(null);
		});

		// Xaero import from a fabricated waypoint file.
		step("xaero import", 10, () -> {
			try {
				Path dir = mc().gameDirectory.toPath().resolve("XaeroWaypoints").resolve("Singleplayer_New World").resolve("dim%0");
				Files.createDirectories(dir);
				Path file = dir.resolve("mw$default_1.txt");
				Files.writeString(file, "waypoint:Xaero test:X:100:70:100:12:false:0:gui.xaero_default:false:0:0:false\n"
					+ "waypoint:Xaero death:D:120:70:120:4:false:1:gui.xaero_default:false:0:0:false\n", StandardCharsets.UTF_8);
				var sources = XaeroImport.sources(mc().level.dimension());
				log("  xaero sources: " + sources.size());
				check("xaero file found", !sources.isEmpty());
				var points = XaeroImport.read(file);
				check("xaero waypoints read", points.size() == 2, "read=" + points.size());
				int imported = XaeroImport.importWaypoints(points, mc().level.dimension(), true);
				check("xaero waypoints imported", imported == 2, "imported=" + imported);
			} catch (IOException e) {
				check("xaero file written", false, e.toString());
			}
		});
		step("reopen atlas", 40, () -> RoleplayersAtlas.openAtlasScreen());
		step("final map", 10, () -> {
			shot("21_final_map");
			log("  landmarks at end: " + ownLandmarks().size());
		});
		step("close", 10, () -> mc().gui.setScreen(null));
	}

	private int startX;

	// ---------------------------------------------------------------- helpers

	private void step(String name, int wait, Runnable action) {
		steps.add(new Step(name, wait, action));
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static Screen screen() {
		return mc().gui.screen();
	}

	private static AtlasScreen atlas() {
		if (!(screen() instanceof AtlasScreen a)) throw new IllegalStateException("atlas screen is not open: " + screen());
		return a;
	}

	private void check(String what, boolean ok) {
		check(what, ok, "");
	}

	private void check(String what, boolean ok, String detail) {
		log((ok ? "  PASS " : "  FAIL ") + what + (detail.isEmpty() ? "" : " (" + detail + ")"));
	}

	private void log(String line) {
		report.add(line);
		RoleplayersAtlas.LOGGER.info("[selftest] " + line);
	}

	private static void command(String command) {
		IntegratedServer server = mc().getSingleplayerServer();
		var source = server.createCommandSourceStack().withEntity(server.getPlayerList().getPlayers().getFirst()).withPosition(server.getPlayerList().getPlayers().getFirst().position());
		server.execute(() -> server.getCommands().performPrefixedCommand(source, command));
	}

	private static void pressKey(int key) {
		KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(key));
	}

	private static void key(int key) {
		screen().keyPressed(new KeyEvent(key, 0, 0));
	}

	private static void click(double x, double y, int button) {
		Screen s = screen();
		MouseButtonEvent e = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
		s.mouseClicked(e, false);
		s.mouseReleased(e);
	}

	private static void clickComponent(Component c) {
		click(c.getGuiX() + c.getWidth() / 2.0, c.getGuiY() + c.getHeight() / 2.0, InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void clickWidget(AbstractWidget w) {
		if (w == null) throw new IllegalStateException("widget missing");
		click(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** Point on the map, relative to the screen centre. */
	private static double[] mapPoint(double dx, double dy) {
		Screen s = screen();
		return new double[]{s.width / 2.0 + dx, s.height / 2.0 + dy};
	}

	private static void clickMap(double dx, double dy) {
		double[] p = mapPoint(dx, dy);
		click(p[0], p[1], InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void dragOnMap(double x1, double y1, double x2, double y2) {
		Screen s = screen();
		MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
		s.mouseClicked(new MouseButtonEvent(x1, y1, left), false);
		int steps = 12;
		for (int i = 1; i <= steps; i++) {
			double x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
			s.mouseDragged(new MouseButtonEvent(x, y, left), (x2 - x1) / steps, (y2 - y1) / steps);
		}
		s.mouseReleased(new MouseButtonEvent(x2, y2, left));
	}

	@SuppressWarnings("unchecked")
	private static <T> T field(Object owner, String name) {
		Class<?> c = owner.getClass();
		while (c != null) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return (T) f.get(owner);
			} catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	private static Map<Identifier, Landmark> ownLandmarks() {
		WorldSummary summary = SurveyorClient.tryGetSummary(mc().level.dimension());
		if (summary == null || summary.landmarks() == null) return Map.of();
		UUID me = SurveyorClient.getClientUuid();
		return summary.landmarks().asMap(me, SurveyorClient.getExploration());
	}

	private static Landmark findByName(String name) {
		for (Landmark l : ownLandmarks().values()) {
			var n = l.get(LandmarkComponentTypes.NAME);
			if (n != null && n.getString().equals(name)) return l;
		}
		return null;
	}

	private static void shot(String name) {
		Minecraft client = mc();
		Screenshot.grab(client.gameDirectory, "selftest_" + name + ".png", client.gameRenderer.mainRenderTarget(), 1, msg -> {
		});
	}

	private void finish(Minecraft client) {
		finished = true;
		long fails = report.stream().filter(l -> l.contains(" FAIL ") || l.contains("THREW")).count();
		log("DONE: " + fails + " problem(s)");
		try {
			Path out = client.gameDirectory.toPath().resolve("selftest");
			Files.createDirectories(out);
			try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("report.txt"), StandardCharsets.UTF_8))) {
				report.forEach(w::println);
			}
		} catch (IOException e) {
			RoleplayersAtlas.LOGGER.error("[selftest] could not write report", e);
		}
		if (!Boolean.getBoolean("roleplayers_atlas.selftest.keepOpen")) client.stop();
	}
}
