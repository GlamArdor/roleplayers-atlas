package glam.ardor.roleplayers_atlas.gui.core;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * A GuiComponent that can act like a button.
 */
@SuppressWarnings("rawtypes")
public class ButtonComponent extends Component {
	protected final List<IButtonListener> listeners = new ArrayList<>();

	protected SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double x = click.x(), y = click.y();
		int mouseButton = click.button();
		if (!isClipped && mouseButton == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(x, y)) {
			onClick();
			return true;
		}

		return super.mouseClicked(click, doubled);
	}

	/**
	 * Called when the user left-clicks on this component.
	 */
	@SuppressWarnings("unchecked")
	public void onClick() {
		if (clickSound != null) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(clickSound, 1.0F));
		}

		for (IButtonListener listener : listeners) {
			listener.onClick(this);
		}
	}

	public void addListener(IButtonListener listener) {
		listeners.add(listener);
	}
}
