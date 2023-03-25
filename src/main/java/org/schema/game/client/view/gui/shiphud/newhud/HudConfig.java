package org.schema.game.client.view.gui.shiphud.newhud;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.schema.common.XMLTools;
import org.schema.common.config.ConfigParserException;
import org.schema.common.config.ConfigurationElement;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.view.gui.weapon.WeaponBottomBar;
import org.schema.game.common.controller.elements.combination.Combinable;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager.OffensiveEffects;
import org.schema.game.common.data.blockeffects.BlockEffectTypes;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.input.InputState;
import org.schema.schine.resource.FileExt;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import thederpgamer.combattweaks.gui.hud.ShipArmorHPBar;
import thederpgamer.combattweaks.gui.hud.TargetShipArmorHPBar;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector4f;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

public abstract class HudConfig extends GUIAncor {
	public static final String hudConfigPath = "." + File.separator + "data" + File.separator + "config" + File.separator + "gui" + File.separator + "HudConfig.xml";
	public static ObjectOpenHashSet<Class<?>> initializedClient = new ObjectOpenHashSet<>();

	public HudConfig(InputState state) {
		super(state);
	}

	public static void load() throws SAXException, IOException, ParserConfigurationException, IllegalArgumentException, IllegalAccessException, ConfigParserException {
		load(hudConfigPath);
	}

	public static void load(String pathOfFactionConfig) throws SAXException, IOException, ParserConfigurationException, IllegalArgumentException, IllegalAccessException, ConfigParserException {
		List<HudConfig> configs = new ObjectArrayList<HudConfig>(10);
		configs.add(new TopBarNew(null, null));
		configs.add(new BottomBarBuild(null, null));
		configs.add(new WeaponBottomBar(null, 0));
		configs.add(new HealthBar(null));
		configs.add(new PowerBar(null));
		configs.add(new PowerBatteryBar(null));
		configs.add(new SpeedBarFarRight(null));
		configs.add(new SpeedBarRight(null));
		configs.add(new ShieldBarLeftOld(null));
		configs.add(new ShieldBarRightLocal(null));
		configs.add(new ShipHPBar(null));
		configs.add(new ShipArmorHPBar(null));
		configs.add(new PositiveEffectBar(null));
		configs.add(new NegativeEffectBar(null));
		configs.add(new PopupMessageNew(null, null, null, null));
		configs.add(new ColorPalette(null));
		configs.add(new BuffDebuff(null));
		configs.add(new TargetPanel(null));
		configs.add(new TargetPowerBar(null));
		configs.add(new TargetPlayerHealthBar(null));
		configs.add(new TargetShieldBar(null));
		configs.add(new TargetShipHPBar(null));
		configs.add(new TargetShipArmorHPBar(null));
		configs.add(new IndicatorIndices(null));
		configs.add(new PlayerHealthBar(null));
		configs.add(new ReactorPowerBar(null));
		configs.add(new PowerStabilizationBar(null));
		configs.add(new PowerConsumptionBar(null));
		assert ((new FileExt(pathOfFactionConfig)).exists());
		Document doc = XMLTools.loadXML(new FileExt(pathOfFactionConfig));
		for(HudConfig config : configs) config.parse(doc);
	}

	public abstract Vector4i getConfigColor();

	public abstract GUIPosition getConfigPosition();

	public abstract Vector2f getConfigOffset();

	public void updateOrientation() {
		orientate(getConfigPosition().value);
		getPos().x += getConfigOffset().x;
		getPos().y += getConfigOffset().y;
	}

	protected abstract String getTag();

	public void parse(Document config) throws IllegalArgumentException, IllegalAccessException, ConfigParserException {
		if(!initializedClient.contains(this.getClass())) {
			org.w3c.dom.Element root = config.getDocumentElement();
			NodeList childNodesTop = root.getChildNodes();
			Field[] fields = getClass().getDeclaredFields();
			//			for(Field f : fields){
			//				System.err.println("READING FIELD OF "+getClass()+": "+f.getName()+"; "+(f.getAnnotations() != null ? Arrays.toString(f.getAnnotations()) : ""));
			//			}
			boolean foundTop = false;
			ObjectOpenHashSet<Field> loaded = new ObjectOpenHashSet<Field>();
			for(int j = 0; j < childNodesTop.getLength(); j++) {
				Node itemTop = childNodesTop.item(j);
				if(itemTop.getNodeType() == Node.ELEMENT_NODE && itemTop.getNodeName().toLowerCase(Locale.ENGLISH).equals(getTag().toLowerCase(Locale.ENGLISH))) {
					NodeList childNodesIn = itemTop.getChildNodes();
					foundTop = true;
					for(int k = 0; k < childNodesIn.getLength(); k++) {
						Node itemIn = childNodesIn.item(k);
						if(itemIn.getNodeType() == Node.ELEMENT_NODE) {
							if(itemIn.getNodeName().toLowerCase(Locale.ENGLISH).equals("basicvalues")) {
								NodeList childNodes = itemIn.getChildNodes();
								for(int i = 0; i < childNodes.getLength(); i++) {
									Node item = childNodes.item(i);
									if(item.getNodeType() == Node.ELEMENT_NODE) {
										boolean searching = false;
										boolean found = false;
										for(Field f : fields) {
											f.setAccessible(true);
											ConfigurationElement annotation = f.getAnnotation(ConfigurationElement.class);
											//											System.err
											//											.println("1CHECKING: "+f.getName()+" with "+item.getNodeName()+"::: ");
											if(annotation != null) {
												searching = true;
												//												System.err
												//														.println("2CHECKING: "+annotation.name()+" with "+item.getNodeName()+"::: "+annotation.name().toLowerCase(Locale.ENGLISH).equals(item.getNodeName().toLowerCase(Locale.ENGLISH)));
												if(annotation.name().toLowerCase(Locale.ENGLISH).equals(item.getNodeName().toLowerCase(Locale.ENGLISH))) {
													try {
														if(f.getType() == Boolean.TYPE) {
															f.setBoolean(this, Boolean.parseBoolean(item.getTextContent()));
															found = true;
														} else if(f.getType() == Integer.TYPE) {
															f.setInt(this, Integer.parseInt(item.getTextContent()));
															found = true;
														} else if(f.getType() == Short.TYPE) {
															f.setShort(this, Short.parseShort(item.getTextContent()));
															found = true;
														} else if(f.getType() == Byte.TYPE) {
															f.setByte(this, Byte.parseByte(item.getTextContent()));
															found = true;
														} else if(f.getType() == Float.TYPE) {
															f.setFloat(this, Float.parseFloat(item.getTextContent()));
															found = true;
														} else if(f.getType() == Double.TYPE) {
															f.setDouble(this, Double.parseDouble(item.getTextContent()));
															found = true;
														} else if(f.getType() == Long.TYPE) {
															f.setLong(this, Long.parseLong(item.getTextContent()));
															found = true;
														} else {
															if(f.getType().equals(Vector2f.class)) {
																String[] split = item.getTextContent().split(",");
																if(split == null || split.length != 2) {
																	throw new ConfigParserException("Must be 2 int values seperated by comma: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "; ");
																}
																try {
																	Vector2f val = new Vector2f(Float.parseFloat(split[0].trim()), Float.parseFloat(split[1].trim()));
																	f.set(this, val);
																	found = true;
																} catch(NumberFormatException e) {
																	e.printStackTrace();
																	throw new ConfigParserException("Values must be numbers: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
															} else if(f.getType().equals(EffectIconIndex.class)) {
																String[] split = item.getTextContent().split(",");
																if(split == null || split.length != 4) {
																	throw new ConfigParserException("Must be 4 values seperated by comma: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
																try {
																	EffectIconIndex val = new EffectIconIndex(Integer.parseInt(split[1].trim()), BlockEffectTypes.valueOf(split[0].trim()), Boolean.parseBoolean(split[2].trim()), split[3].trim());
																	f.set(this, val);
																	found = true;
																} catch(NumberFormatException e) {
																	e.printStackTrace();
																	throw new ConfigParserException("Values must be numbers: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
															} else if(f.getType().equals(HitIconIndex.class)) {
																String[] split = item.getTextContent().split(",");
																if(split == null || split.length != 4) {
																	throw new ConfigParserException("Must be 4 values seperated by comma: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
																try {
																	HitIconIndex val = new HitIconIndex(Integer.parseInt(split[1].trim()), OffensiveEffects.valueOf(split[0].trim()), Boolean.parseBoolean(split[2].trim()), split[3].trim());
																	f.set(this, val);
																	found = true;
																} catch(NumberFormatException e) {
																	e.printStackTrace();
																	throw new ConfigParserException("Values must be numbers: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
															} else if(f.getType().equals(Vector4i.class)) {
																String[] split = item.getTextContent().split(",");
																if(split == null || split.length != 4) {
																	throw new ConfigParserException("Must be 4 int values seperated by comma: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
																try {
																	Vector4i val = new Vector4i(Integer.parseInt(split[0].trim()), Integer.parseInt(split[1].trim()), Integer.parseInt(split[2].trim()), Integer.parseInt(split[3].trim()));
																	f.set(this, val);
																	found = true;
																} catch(NumberFormatException e) {
																	e.printStackTrace();
																	throw new ConfigParserException("Values must be numbers: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
															} else if(f.getType().equals(Vector4f.class)) {
																String[] split = item.getTextContent().split(",");
																if(split == null || split.length != 4) {
																	throw new ConfigParserException("Must be 4 int values seperated by comma: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
																try {
																	Vector4f val = new Vector4f(Float.parseFloat(split[0].trim()) / 255f, Float.parseFloat(split[1].trim()) / 255f, Float.parseFloat(split[2].trim()) / 255f, Float.parseFloat(split[3].trim()) / 255f);
																	f.set(this, val);
																	found = true;
																} catch(NumberFormatException e) {
																	e.printStackTrace();
																	throw new ConfigParserException("Values must be numbers: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
															} else if(f.getType().equals(GUIPosition.class)) {
																if(item.getTextContent().isEmpty()) {
																	throw new ConfigParserException("May not be empty: " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + "");
																}
																String[] split = item.getTextContent().split("\\|");
																int v = 0;
																for(int g = 0; g < split.length; g++) {
																	String s = split[g].trim();
																	if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_BOTTOM")) {
																		v |= ORIENTATION_BOTTOM;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_HORIZONTAL_MIDDLE")) {
																		v |= ORIENTATION_HORIZONTAL_MIDDLE;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_VERTICAL_MIDDLE")) {
																		v |= ORIENTATION_VERTICAL_MIDDLE;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_LEFT")) {
																		v |= ORIENTATION_LEFT;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_NONE")) {
																		v |= ORIENTATION_NONE;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_RIGHT")) {
																		v |= ORIENTATION_RIGHT;
																	} else if(s.toUpperCase(Locale.ENGLISH).equals("ORIENTATION_TOP")) {
																		v |= ORIENTATION_TOP;
																	} else {
																		throw new ConfigParserException("Invalid value: '" + s + "'; " + itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": " + f.getName() + "; " + f.getType() + ": \nuse the following " + "ORIENTATION_BOTTOM, ORIENTATION_HORIZONTAL_MIDDLE, ORIENTATION_VERTICAL_MIDDLE, ORIENTATION_LEFT, ORIENTATION_NONE, ORIENTATION_RIGHT, ORIENTATION_TOP seperated by |");
																	}
																}
																GUIPosition p = new GUIPosition();
																p.value = v;
																f.set(this, p);
																found = true;
															} else if(f.getType().equals(String.class)) {
																found = true;
															} else {
																throw new ConfigParserException("Cannot parse field: " + f.getName() + "; " + f.getType());
															}
														}
													} catch(NumberFormatException e) {
														throw new ConfigParserException("Cannot parse field: " + f.getName() + "; " + f.getType() + "; with " + item.getTextContent(), e);
													}
												}
												if(found) {
													loaded.add(f);
													break;
												}
											}
										}
										if(searching && !found) {
											throw new ConfigParserException(itemTop.getNodeName() + "-> " + itemIn.getNodeName() + " -> " + item.getNodeName() + ": No appropriate field found for tag: " + item.getNodeName());
										}
									}
								}
							} else if(itemIn.getNodeName().toLowerCase(Locale.ENGLISH).equals("combination")) {
								if(this instanceof Combinable) {
									if(((Combinable<?, ?, ?, ?>) this).getAddOn() != null) {
										((Combinable<?, ?, ?, ?>) this).getAddOn().parse(itemIn);
									}
								} else {
									throw new ConfigParserException(itemTop.getNodeName() + " -> " + itemIn.getNodeName() + " class is not combinable " + this + ", but has combinable tag");
								}
							} else {
								throw new ConfigParserException("tag \"" + itemTop.getNodeName() + " -> " + itemIn.getNodeName() + "\" unknown in this context (has to be either \"BasicValues\" or \"Combinable\")");
							}
						}
					}
				}
			}
			if(!foundTop) {
				throw new ConfigParserException("Tag \"" + getTag() + "\" not found in configuation. Please create it (case insensitive)");
			}
			Annotation[] annotations = getClass().getAnnotations();
			for(Field f : fields) {
				f.setAccessible(true);
				ConfigurationElement annotation = f.getAnnotation(ConfigurationElement.class);
				if(annotation != null && !loaded.contains(f)) {
					throw new ConfigParserException("virtual field " + f.getName() + " (" + annotation.name() + ") not found. Please define a tag \"" + annotation.name() + "\" inside the <BasicValues> of \"" + getTag() + "\"");
				}
			}
			initializedClient.add(getClass());
		}
	}
}
