package thederpgamer.combattweaks.data;

import api.mod.StarLoader;
import api.mod.StarMod;
import api.utils.other.HashList;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import thederpgamer.combattweaks.CombatTweaks;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

public class ControlBindingData {

	private static final HashList<StarMod, ControlBindingData> bindings = new HashList<>();
	private static final byte VERSION = 0;

	private String name;
	private String description;
	private StarMod mod;
	private int binding;

	private ControlBindingData(String name, String description, int binding, StarMod mod) {
		this.name = name;
		this.description = description;
		this.binding = binding;
		this.mod = mod;
	}

	private ControlBindingData(JSONObject data) {
		deserialize(data);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getBinding() {
		return binding;
	}

	public void setBinding(int binding) {
		this.binding = binding;
		save(mod);
	}

	public StarMod getMod() {
		return mod;
	}

	private JSONObject serialize() {
		JSONObject data = new JSONObject();
		data.put("version", VERSION);
		data.put("mod", mod.getName());
		data.put("name", name);
		data.put("description", description);
		data.put("binding", binding);
		return data;
	}

	private void deserialize(JSONObject data) {
		byte version = (byte) data.getInt("version");
		name = data.getString("name");
		mod = StarLoader.getModFromName(data.getString("mod")).getRealMod();
		description = data.getString("description");
		binding = data.getInt("binding");
	}

	/**
	 * Registers a new control binding for a mod. This will create the control bindings file if it does not exist.
	 * @param mod The mod to register the control binding for
	 * @param name The name of the control binding (this should be unique per mod)
	 * @param description A short description of the control binding (for display purposes)
	 * @param defaultBinding The default binding for this control (typically a key code or input ID). Use 0 or negative values to indicate no binding.
	 */
	public static void registerBinding(StarMod mod, String name, String description, int defaultBinding) {
		if(bindingExists(mod, name)) {
			return;
		}
		// Create the control binding data
		ControlBindingData bindingData = new ControlBindingData(name, description, defaultBinding, mod);
		// Add to the bindings list
		bindings.add(mod, bindingData);
		// Save to file
		save(mod);
	}
	
	public static boolean bindingExists(StarMod mod, String bindingName) {
		for(ControlBindingData bindingData : bindings.getList(mod)) {
			if(bindingData.name.equals(bindingName)) {
				// Binding already exists for this mod
				return true;
			}
		}
		// Binding does not exist for this mod
		return false;
	}

	private static File getBindingsFile(StarMod mod) {
		return new File(mod.getSkeleton().getResourcesFolder() + "/control_bindings.json");
	}

	public static ArrayList<ControlBindingData> getModBindings(StarMod mod) {
		if(bindings.getList(mod).isEmpty()) {
			load(mod);
		}
		return new ArrayList<>(bindings.getList(mod));
	}
	
	public static void save(StarMod mod) {
		File file = getBindingsFile(mod);
		if(!file.exists()) {
			try {
				file.createNewFile();
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("An error occurred while creating control bindings file", exception);
			}
		}
		try {
			JSONObject data = new JSONObject();
			data.put("version", VERSION); // Store the version of the serialization format
			JSONArray array = new JSONArray(); // Create an array to hold the serialized data
			for(ControlBindingData bindingData : bindings.getList(mod)) {
				array.put(bindingData.serialize());
			}
			data.put("bindings", array); // Add the array to the main JSON object
			FileWriter writer = new FileWriter(file);
			writer.write(data.toString());
			writer.flush();
			writer.close();
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("An error occurred while saving control bindings file", exception);
		}
	}

	public static void load(StarMod mod) {
		File file = getBindingsFile(mod);
		if(file.exists()) {
			try {
				JSONObject data = new JSONObject(FileUtils.readFileToString(file));
				byte version = (byte) data.getInt("version");
				JSONArray dataArray = data.optJSONArray("bindings");
				for(int i = 0; i < dataArray.length(); i++) {
					JSONObject bindingsData = dataArray.getJSONObject(i); // Get the JSON object for each binding
					bindings.add(mod, new ControlBindingData(bindingsData));
				}
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("An error occurred while loading control bindings file", exception);
			}
		}
	}

	public static HashList<StarMod, ControlBindingData> getBindings() {
		return bindings;
	}

	public static ArrayList<ControlBindingData> getAllBindings() {
		ArrayList<ControlBindingData> allBindings = new ArrayList<>();
		for(StarMod mod : bindings.keySet()) {
			allBindings.addAll(bindings.get(mod));
		}
		return allBindings;
	}

	public static ControlBindingData getBinding(StarMod mod, String bindingName) {
		for(ControlBindingData bindingData : bindings.getList(mod)) {
			if(bindingData.name.equals(bindingName)) {
				return bindingData;
			}
		}
		return null;
	}

	public static ControlBindingData getBinding(String bindingName) {
		for(ControlBindingData bindingData : getAllBindings()) {
			if(bindingData.name.equals(bindingName)) {
				return bindingData;
			}
		}
		return null;
	}
}
