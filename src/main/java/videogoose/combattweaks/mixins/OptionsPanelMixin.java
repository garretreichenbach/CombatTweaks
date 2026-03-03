package videogoose.combattweaks.mixins;

import api.mod.StarMod;
import org.schema.game.client.view.gui.options.newoptions.OptionsPanelNew;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.settings.EngineSettingsType;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUITabbedContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import videogoose.combattweaks.data.ControlBindingData;
import videogoose.combattweaks.gui.controls.ControlBindingsScrollableList;

import java.util.ArrayList;
import java.util.Locale;

@Mixin(targets = "org.schema.game.client.view.gui.options.newoptions.OptionsPanelNew", remap = false)
public class OptionsPanelMixin {

	@Shadow
	private GUIContentPane generalTab;

	/**
	 * @author videogoose
	 * @reason Add a new "MOD CONTROLS" tab to the options menu, which contains a list of all control bindings added by mods. Each mod with control bindings gets its own sub-tab within this section.
	 */
	@Overwrite
	public void createSettingsPane() {
		OptionsPanelNew optionsPanel = (OptionsPanelNew) (Object) this;
		GUITabbedContent pane = new GUITabbedContent(optionsPanel.getState(), generalTab.getContent(0));
		pane.activationInterface = optionsPanel.activeInterface;
		pane.setPos(0, 2, 0);
		pane.onInit();
		generalTab.getContent(0).attach(pane);

		optionsPanel.addSettingsTab(pane, EngineSettingsType.GENERAL, Lng.str("GENERAL"));
		optionsPanel.addSettingsTab(pane, EngineSettingsType.GRAPHICS, Lng.str("GRAPHICS"));
		optionsPanel.addSettingsTab(pane, EngineSettingsType.GRAPHICS_ADVANCED, Lng.str("ADV. GRAPHICS"));
		optionsPanel.addSettingsTab(pane, EngineSettingsType.SOUND, Lng.str("SOUND"));
		GUIContentPane modControlsPane=  optionsPanel.addTab(Lng.str("MOD CONTROLS"));
		optionsPanel.addOkCancel(generalTab);

		GUITabbedContent tabbedContent = new GUITabbedContent(modControlsPane.getState(), modControlsPane.getContent(0));
		tabbedContent.activationInterface = optionsPanel.activeInterface;
		tabbedContent.onInit();
		tabbedContent.setPos(0, 2, 0);
		modControlsPane.getContent(0).attach(tabbedContent);

		for(StarMod mod : ControlBindingData.getBindings().keySet()) {
			ArrayList<ControlBindingData> modBindings = ControlBindingData.getBindings().get(mod);
			if(!modBindings.isEmpty()) {
				GUIContentPane modTab = tabbedContent.addTab(mod.getName().toUpperCase(Locale.ENGLISH));
				ControlBindingsScrollableList scrollableList = new ControlBindingsScrollableList(modTab.getState(), modTab.getContent(0), mod);
				scrollableList.onInit();
				modTab.getContent(0).attach(scrollableList);
			}
		}
	}
}
