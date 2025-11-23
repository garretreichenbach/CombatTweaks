package thederpgamer.combattweaks.gui.controls;

import api.mod.ModSkeleton;
import api.mod.StarLoader;
import api.mod.StarMod;
import org.schema.game.common.controller.elements.power.reactor.chamber.ReactorChamberUnit;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUIElementList;
import org.schema.schine.graphicsengine.forms.gui.newgui.*;
import org.schema.schine.input.InputState;
import org.schema.schine.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;
import thederpgamer.combattweaks.data.ControlBindingData;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

public class ControlBindingsScrollableList extends ScrollableTableList<ControlBindingData> implements GUIActiveInterface {

	private final StarMod mod;

	public ControlBindingsScrollableList(InputState state, GUIElement element, StarMod mod) {
		super(state, 100, 100, element);
		this.mod = mod;
	}

	@Override
	protected Collection<ControlBindingData> getElementList() {
		return ControlBindingData.getModBindings(mod);
	}

	@Override
	public void initColumns() {
		addColumn(Lng.str("Name"), 3.0f, new Comparator<ControlBindingData>() {
			@Override
			public int compare(ControlBindingData o1, ControlBindingData o2) {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});
		addColumn(Lng.str("Description"), 7.0f, new Comparator<ControlBindingData>() {
			@Override
			public int compare(ControlBindingData o1, ControlBindingData o2) {
				return o1.getDescription().compareToIgnoreCase(o2.getDescription());
			}
		});
		addColumn(Lng.str("Setting"), 3.0f, new Comparator<ControlBindingData>() {
			@Override
			public int compare(ControlBindingData o1, ControlBindingData o2) {
				// Compare the binding values
				if(o1.getBinding() == o2.getBinding()) {
					return 0;
				} else if(o1.getBinding() <= 0) {
					return 1; // o1 is not bound
				} else if(o2.getBinding() <= 0) {
					return -1; // o2 is not bound
				}
				return Integer.compare(o1.getBinding(), o2.getBinding());
			}
		});
		addTextFilter(new GUIListFilterText<ControlBindingData>() {
			@Override
			public boolean isOk(String input, ControlBindingData listElement) {
				return listElement.getName().toLowerCase(Locale.ENGLISH).contains(input.trim().toLowerCase(Locale.ENGLISH));
			}
		}, Lng.str("SEARCH BY NAME"), ControllerElement.FilterRowStyle.FULL);
	}

	@Override
	public void updateListEntries(GUIElementList guiElementList, Set<ControlBindingData> set) {
		guiElementList.deleteObservers();
		guiElementList.addObserver(this);
		int count = 0;
		for(ModSkeleton mod : StarLoader.starMods) {
			ScrollableTableList<ControlBindingData>.Seperator seperator = getSeperator(mod.getName(), count++);
			for(final ControlBindingData binding : set) {
				if(binding.getMod().getSkeleton().equals(mod)) {
					GUIClippedRow nameText = getSimpleRow(binding.getName(), this);
					GUIClippedRow descriptionText = getSimpleRow(binding.getDescription(), this);
					GUIHorizontalButton settingButton = new GUIHorizontalButton(getState(), GUIHorizontalArea.HButtonType.BUTTON_RED_MEDIUM, new Object() {
						@Override
						public String toString() {
							if(binding.getBinding() <= 0) {
								return "NOT BOUND";
							} else {
								return Keyboard.getKeyName(binding.getBinding());
							}
						}
					}, new GUICallback() {
						@Override
						public void callback(GUIElement callingGuiElement, MouseEvent event) {
							if(event.pressedLeftMouse()) {
								System.err.println("PRESSED MOUSE TO ACTIVATE");
								(new ControlBindingDialog(getState(), binding)).activate();
							}
						}

						@Override
						public boolean isOccluded() {
							return !isActive();
						}
					}, this, new GUIActivationCallback() {
						@Override
						public boolean isVisible(InputState state) {
							return true;
						}

						@Override
						public boolean isActive(InputState state) {
							return ControlBindingsScrollableList.this.isActive();
						}
					}) {
						@Override
						public void draw() {
							setWidth(columns.get(2).bg.getWidth() - 25);
							HButtonType.getType(HButtonType.TEXT_FILED_LIGHT, isInside(), isActive(), false);
							setMouseUpdateEnabled(isActive());
							super.draw();
						}
					};
					settingButton.onInit();
					settingButton.setFont(FontLibrary.getBlenderProMedium19());
					settingButton.getPos().y = 3;
					ControlBindingSettingsRow row = new ControlBindingSettingsRow(getState(), binding, nameText, descriptionText, settingButton);
					row.seperator = seperator;
					row.onInit();
					guiElementList.addWithoutUpdate(row);
				}
			}
		}
		guiElementList.updateDim();
	}

	public class ControlBindingSettingsRow extends ScrollableTableList<ControlBindingData>.Row {

		public ControlBindingSettingsRow(InputState state, ControlBindingData binding, GUIElement... elements) {
			super(state, binding, elements);
			highlightSelect = true;
		}
	}
}
