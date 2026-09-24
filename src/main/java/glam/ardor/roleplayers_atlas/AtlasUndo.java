package glam.ardor.roleplayers_atlas;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Taking back the last thing drawn.
 * <p>
 * Every tool in the atlas draws straight onto the map, and until now every one
 * of them was a one-way road: a stroke of the town brush across the wrong
 * valley, a zone rubbed out to nothing, a mark erased by a slipped click. None
 * of it could be got back. This keeps the last twenty things done and how to
 * undo each, which costs nothing while nothing goes wrong and everything when
 * it does.
 * <p>
 * What is stored is the <em>inverse</em> action, not a copy of the map: putting
 * a landmark back, painting a set of cells the colour they were, restoring a
 * correction to what it said before. Running one is done with recording turned
 * off, so undoing doesn't pile another step onto the stack.
 * <p>
 * The stack belongs to one world and is emptied when it changes – a step that
 * says "put this landmark back" means nothing in a world that never had it.
 */
public final class AtlasUndo {
	/** How far back the atlas remembers. Twenty is a long drag's worth of strokes. */
	private static final int LIMIT = 20;

	public record Step(Text description, Runnable undo) {
	}

	private static final Deque<Step> undoSteps = new ArrayDeque<>();
	private static final Deque<Step> redoSteps = new ArrayDeque<>();

	/**
	 * Which pile a step goes on as it happens.
	 * <p>
	 * The trick that makes redo free: every way of changing the map files its own
	 * inverse. So taking a step back is itself a change, and if its inverse is
	 * filed on the other pile, going forward again needs no separate machinery.
	 */
	private enum Recording {
		NORMAL, UNDOING, REDOING, OFF
	}

	private static Recording mode = Recording.NORMAL;

	private AtlasUndo() {
	}

	/**
	 * Files away how to take back what was just done.
	 *
	 * @param description what it was, in words, to be shown when it is taken back
	 * @param undo        what to do to reverse it – itself a change, which files
	 *                    its own inverse in turn
	 */
	public static void push(Text description, Runnable undo) {
		Step step = new Step(description, undo);
		switch (mode) {
			case OFF -> {
				return;
			}
			// Doing something new forks away from whatever was undone: there is no
			// longer a forward to go to.
			case NORMAL -> {
				undoSteps.addLast(step);
				redoSteps.clear();
			}
			case UNDOING -> redoSteps.addLast(step);
			case REDOING -> undoSteps.addLast(step);
		}
		trim(undoSteps);
		trim(redoSteps);
	}

	private static void trim(Deque<Step> steps) {
		while (steps.size() > LIMIT) steps.removeFirst();
	}

	public static boolean canUndo() {
		return !undoSteps.isEmpty();
	}

	public static boolean canRedo() {
		return !redoSteps.isEmpty();
	}

	/** Takes back the last thing done and says what it was, or null if there was nothing. */
	public static @Nullable Text undo() {
		return run(undoSteps, Recording.UNDOING);
	}

	/** Does again the last thing taken back, or null if there was nothing. */
	public static @Nullable Text redo() {
		return run(redoSteps, Recording.REDOING);
	}

	private static @Nullable Text run(Deque<Step> from, Recording as) {
		Step step = from.pollLast();
		if (step == null) return null;
		Recording was = mode;
		mode = as;
		try {
			step.undo().run();
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to reverse {}", step.description().getString(), e);
			return null;
		} finally {
			mode = was;
		}
		return step.description();
	}

	/**
	 * Files a change whose two directions are both known outright.
	 * <p>
	 * Most of the atlas gets redo for free, because each way of changing the map
	 * files its own inverse and so a step back is itself a step. Some things
	 * aren't like that – taking a scroll in touches marks, layers, corrections
	 * and towns at once, and the way forward is simply to read the scroll again
	 * rather than to reverse a reversal. For those, both directions are handed
	 * over and the two are made to swap places each time one is used.
	 */
	public static void pushReversible(Text description, Runnable back, Runnable forth) {
		push(description, () -> {
			withoutRecording(back);
			pushReversible(description, forth, back);
		});
	}

	/**
	 * Runs something without any of it being remembered, so a caller can file one
	 * step of its own covering the whole of it.
	 */
	public static void withoutRecording(Runnable action) {
		withRecordingOff(() -> {
			action.run();
			return null;
		});
	}

	/** The same, for something that has an answer to give back. */
	public static <T> T withRecordingOff(java.util.function.Supplier<T> action) {
		Recording was = mode;
		mode = Recording.OFF;
		try {
			return action.get();
		} finally {
			mode = was;
		}
	}

	public static void clear() {
		undoSteps.clear();
		redoSteps.clear();
	}
}
