package thederpgamer.combattweaks;

import api.config.BlockConfig;
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

	private final String[] overwriteClasses = {"RepairBeamHandler"};


	@Override
	public void onEnable() {
		super.onEnable();
		instance = this;
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerPackets();
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
			ZipInputStream file = new ZipInputStream(Files.newInputStream(this.getSkeleton().getJarFile().toPath()));
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
	}

	private void initializeGlossary() {
		GlossarInit.initGlossar(this);
		GlossarCategory combatTweaks = new GlossarCategory("Combat Tweaks");
		combatTweaks.addEntry(new GlossarEntry("Repair Paste Fabricator", "Put these on your ship and they will allow your Astrotech beams to use repair paste instead of resources to repair entities."));
		combatTweaks.addEntry(new GlossarEntry("Misc. Changes", "- Added an indicator to show incoming jumps to your sector. Having a higher recon level will increase the accuracy of the marker.\n" +
				"- Jumps now take longer to complete the further away they are from your sector.\n" +
				"- AI can use Astrotech Beams to repair friendly entities."));
		GlossarInit.addCategory(combatTweaks);
	}
}
