package thederpgamer.combattweaks;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import glossar.GlossarCategory;
import glossar.GlossarEntry;
import glossar.GlossarInit;
import org.apache.commons.io.IOUtils;
import org.schema.schine.resource.ResourceLoader;
import thederpgamer.combattweaks.element.ElementManager;
import thederpgamer.combattweaks.element.blocks.systems.RepairPasteFabricator;
import thederpgamer.combattweaks.manager.ConfigManager;
import thederpgamer.combattweaks.manager.EventManager;
import thederpgamer.combattweaks.manager.ResourceManager;
import thederpgamer.combattweaks.network.client.SendAttackPacket;
import thederpgamer.combattweaks.network.server.JumpHudRemovePacket;
import thederpgamer.combattweaks.network.server.JumpHudUpdatePacket;

import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class CombatTweaks extends StarMod {

	public static void main(String[] args) {}
	private static CombatTweaks instance;
	public static CombatTweaks getInstance() {
		return instance;
	}
	public CombatTweaks() {
		instance = this;
	}
	private final String[] overwriteClasses = {"Hud", "HudConfig", "TargetPanel", "RepairBeamHandler", "RailRelation"}; //Todo: Find some way of doing this without overwriting classes

	@Override
	public void onEnable() {
		instance = this;
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerPackets();
	}

	@Override
	public void onClientCreated(ClientInitializeEvent clientInitializeEvent) {
		super.onClientCreated(clientInitializeEvent);
		initializeGlossary();
	}

	@Override
	public void onResourceLoad(ResourceLoader resourceLoader) {
		ResourceManager.loadResources(this);
	}

	@Override
	public void onBlockConfigLoad(BlockConfig blockConfig) {
		ElementManager.addBlock(new RepairPasteFabricator());
		ElementManager.initialize();
	}

	@Override
	public byte[] onClassTransform(String className, byte[] byteCode) {
		for(String name : overwriteClasses) if(className.endsWith(name)) return overwriteClass(className, byteCode);
		return super.onClassTransform(className, byteCode);
	}

	private byte[] overwriteClass(String className, byte[] byteCode) {
		byte[] bytes = null;
		try {
			ZipInputStream file = new ZipInputStream(Files.newInputStream(getSkeleton().getJarFile().toPath()));
			while(true) {
				ZipEntry nextEntry = file.getNextEntry();
				if(nextEntry == null) break;
				if(nextEntry.getName().endsWith(className + ".class")) bytes = IOUtils.toByteArray(file);
			}
			file.close();
		} catch(IOException exception) {
			exception.printStackTrace();
		}
		if(bytes != null) return bytes;
		else return byteCode;
	}

	private void registerPackets() {
		PacketUtil.registerPacket(JumpHudUpdatePacket.class);
		PacketUtil.registerPacket(JumpHudRemovePacket.class);
		PacketUtil.registerPacket(SendAttackPacket.class);
	}

	private void initializeGlossary() {
		GlossarInit.initGlossar(this);
		GlossarCategory combatTweaks = new GlossarCategory("Combat Tweaks");
		combatTweaks.addEntry(new GlossarEntry("Armor HP", "Armor HP has been re-added to the game. This allows armor to act as a sort of \"second shield\" system. Armor HP is a percentage of the total amount of armor provided by all armor blocks on the entity. When your Armor HP reaches 0, your entity will begin to take damage. Armor HP can be restored by using repair beams. This system also accounts for Armor thickness at contact points."));
//		combatTweaks.addEntry(new GlossarEntry("Repair Paste Fabricator", "Put these on your ship and they will allow your Astrotech beams to use repair paste instead of resources to repair entities."));
		combatTweaks.addEntry(new GlossarEntry("Misc. Changes", "- Added an indicator to show incoming jumps to your sector. Having a higher recon level will increase the accuracy of the marker.\n" +
//				"- Jumps now take longer to complete the further away they are from your sector.\n" +
				"- AI can now use Astrotech Beams to repair friendly entities."));
		GlossarInit.addCategory(combatTweaks);
	}
}
