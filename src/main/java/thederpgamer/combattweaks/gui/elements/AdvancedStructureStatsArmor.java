package thederpgamer.combattweaks.gui.elements;

import org.schema.common.util.StringTools;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.tools.StatLabelResult;
import org.schema.game.client.view.gui.advancedstats.AdvancedStructureStatsGUISGroup;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableDirtyInterface;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

public class AdvancedStructureStatsArmor extends AdvancedStructureStatsGUISGroup {

	private ManagedUsableSegmentController<?> lastController;
	private ArmorHPCollection lastCollection;

	public AdvancedStructureStatsArmor(AdvancedGUIElement element) {
		super(element);
	}

	@Override
	public String getId() {
		return "AS_ARMOR";
	}

	@Override
	public String getTitle() {
		return Lng.str("Armor");
	}

	@Override
	public void build(GUIContentPane contentPane, GUIDockableDirtyInterface dockableInterface) {
		contentPane.setTextBoxHeightLast(30);
		int y = 0;
		addStatLabel(contentPane.getContent(0), 0, y++, new StatLabelResult() {

			@Override
			public String getName() {
				return Lng.str("Armor HP:");
			}

			@Override
			public FontLibrary.FontSize getFontSize() {
				return FontLibrary.FontSize.MEDIUM;
			}

			@Override
			public String getValue() {
				updateCollection();
				if(getMan().getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					if(lastCollection != null) {
						boolean canBleedThrough = lastCollection.canBleedThrough();
						double bleedThroughThreshold = lastCollection.getBleedThroughThreshold();
						double percent = lastCollection.getHPPercent();
						if(percent >= 100.0f) {
							return "100% - Armor Integrity Stable";
						} else {
							if(canBleedThrough) {
								return StringTools.formatPointZero(percent * 100.0f) + "% - WARNING: Armor Integrity Below " + StringTools.formatPointZero(bleedThroughThreshold * 100.0f) + "%!";
							} else {
								return StringTools.formatPointZero(percent * 100.0f) + "% - Armor Integrity Stable";
							}
						}
					}
				}
				return "0";
			}

			@Override
			public int getStatDistance() {
				return 150;
			}
		});
		addStatLabel(contentPane.getContent(0), 0, y++, new StatLabelResult() {

			@Override
			public String getName() {
				return Lng.str("Armor HP (Current):");
			}

			@Override
			public FontLibrary.FontSize getFontSize() {
				return FontLibrary.FontSize.MEDIUM;
			}

			@Override
			public String getValue() {
				updateCollection();
				if(lastCollection != null) {
					return StringTools.formatSeperated(lastCollection.getCurrentHP());
				}
				return "0";
			}

			@Override
			public int getStatDistance() {
				return 150;
			}
		});
		addStatLabel(contentPane.getContent(0), 0, y++, new StatLabelResult() {

			@Override
			public String getName() {
				return Lng.str("Armor HP (Max):");
			}

			@Override
			public FontLibrary.FontSize getFontSize() {
				return FontLibrary.FontSize.MEDIUM;
			}

			@Override
			public String getValue() {
				updateCollection();
				if(lastCollection != null) {
					return StringTools.formatSeperated(lastCollection.getMaxHP());
				}
				return "0";
			}

			@Override
			public int getStatDistance() {
				return 150;
			}
		});
	}

	private void updateCollection() {
		if(lastCollection == null) {
			lastCollection = ArmorHPCollection.getCollection(getMan().getSegmentController());
			lastController = (ManagedUsableSegmentController<?>) getMan().getSegmentController();
		} else if(lastController != getMan().getSegmentController()) {
			lastCollection = ArmorHPCollection.getCollection(getMan().getSegmentController());
			lastController = (ManagedUsableSegmentController<?>) getMan().getSegmentController();
		}
	}
}
