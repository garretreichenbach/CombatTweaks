package thederpgamer.combattweaks.gui.elements;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUIListElement;
import org.schema.schine.graphicsengine.forms.gui.TooltipProvider;
import org.schema.schine.input.InputState;

import java.util.*;

/**
 * Version of GUIElementList that groups elements together and can color code said groups.
 *
 * @author TheDerpGamer
 */
public class GroupedElementList extends GUIElement implements List<GUIListElement>, TooltipProvider {

	private final Object2ObjectOpenHashMap<String, ObjectArrayList<GUIListElement>> elementMap = new Object2ObjectOpenHashMap<>();
	private boolean initialized;
	public int height;
	public int width;
	public int rightInset;
	public int leftInset;

	public GroupedElementList(InputState state) {
		super(state);
	}

	@Override
	public void onInit() {
		initialized = true;
	}

	@Override
	public void draw() {

	}

	@Override
	public void drawToolTip() {

	}

	@Override
	public void cleanUp() {
		for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
			for(GUIListElement element : entry.getValue()) element.cleanUp();
		}
	}

	public void cleanupGroup(String groupKey) {
		if(elementMap.containsKey(groupKey)) {
			for(GUIListElement element : elementMap.get(groupKey)) element.cleanUp();
		}
	}

	@Override
	public int size() {
		int size = 0;
		for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) size += entry.getValue().size();
		return size;
	}

	public int groupSize(String key) {
		if(elementMap.containsKey(key)) return elementMap.get(key).size();
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return elementMap.isEmpty();
	}

	public boolean isGroupEmpty(String key) {
		return !elementMap.containsKey(key);
	}

	@Override
	public boolean contains(Object o) {
		if(o instanceof GUIListElement) {
			for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
				if(entry.getValue().contains(o)) return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<GUIListElement> iterator() {
		throw new RuntimeException("iterator() not supported for GroupedElementList! Use getGroupIterator() instead.");
	}

	public Iterator<GUIListElement> getGroupIterator(String key) {
		if(elementMap.containsKey(key)) return elementMap.get(key).iterator();
		return null;
	}

	@Override
	public Object[] toArray() {
		return elementMap.values().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return elementMap.values().toArray(a);
	}

	@Override
	public boolean add(GUIListElement guiListElement) {
		throw new RuntimeException("add() not supported for GroupedElementList! Use addElement() instead.");
	}

	public boolean addElement(String key, GUIListElement guiListElement) {
		if(!elementMap.containsKey(key)) elementMap.put(key, new ObjectArrayList<GUIListElement>());
		if(!elementMap.get(key).contains(guiListElement)) {
			elementMap.get(key).add(guiListElement);
			if(initialized) guiListElement.onInit();
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		if(o instanceof GUIListElement) {
			for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
				if(entry.getValue().contains(o)) {
					entry.getValue().remove(o);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for(Object o : c) {
			if(!contains(o)) return false;
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends GUIListElement> c) {
		throw new RuntimeException("addAll() not supported for GroupedElementList! Use addElement() instead.");
	}

	@Override
	public boolean addAll(int index, Collection<? extends GUIListElement> c) {
		throw new RuntimeException("addAll() not supported for GroupedElementList! Use addElement() instead.");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		for(Object o : c) {
			if(!remove(o)) return false;
		}
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
			for(GUIListElement element : entry.getValue()) {
				if(!c.contains(element)) {
					entry.getValue().remove(element);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void clear() {
		elementMap.clear();
	}

	@Override
	public GUIListElement get(int index) {
		throw new RuntimeException("get() not supported for GroupedElementList! Use getElement() instead.");
	}

	public GUIListElement getElement(String key, int index) {
		if(elementMap.containsKey(key)) return elementMap.get(key).get(index);
		return null;
	}

	@Override
	public GUIListElement set(int index, GUIListElement element) {
		throw new RuntimeException("set() not supported for GroupedElementList! Use setElement() instead.");
	}

	public GUIListElement setElement(String key, int index, GUIListElement element) {
		if(elementMap.containsKey(key)) {
			elementMap.get(key).set(index, element);
			return element;
		}
		return null;
	}

	@Override
	public void add(int index, GUIListElement element) {
		throw new RuntimeException("add() not supported for GroupedElementList! Use addElement() instead.");
	}

	public void addElement(String key, int index, GUIListElement element) {
		if(elementMap.containsKey(key)) elementMap.get(key).add(index, element);
		else {
			elementMap.put(key, new ObjectArrayList<GUIListElement>());
			elementMap.get(key).add(index, element);
		}
	}

	@Override
	public GUIListElement remove(int index) {
		throw new RuntimeException("remove() not supported for GroupedElementList! Use removeElement() instead.");
	}

	public GUIListElement removeElement(String key, int index) {
		if(elementMap.containsKey(key)) return elementMap.get(key).remove(index);
		return null;
	}

	@Override
	public int indexOf(Object o) {
		if(o instanceof GUIListElement) {
			for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
				if(entry.getValue().contains(o)) return entry.getValue().indexOf(o);
			}
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		if(o instanceof GUIListElement) {
			for(Map.Entry<String, ObjectArrayList<GUIListElement>> entry : elementMap.entrySet()) {
				if(entry.getValue().contains(o)) return entry.getValue().lastIndexOf(o);
			}
		}
		return -1;
	}

	@Override
	public ListIterator<GUIListElement> listIterator() {
		throw new RuntimeException("listIterator() not supported for GroupedElementList! Use getGroupIterator() instead.");
	}

	@Override
	public ListIterator<GUIListElement> listIterator(int index) {
		throw new RuntimeException("listIterator() not supported for GroupedElementList! Use getGroupIterator() instead.");
	}

	public ListIterator<GUIListElement> getGroupIterator(String key, int index) {
		if(elementMap.containsKey(key)) return elementMap.get(key).listIterator(index);
		return null;
	}

	@Override
	public List<GUIListElement> subList(int fromIndex, int toIndex) {
		return null;
	}

	@Override
	public float getHeight() {
		return (float) this.height;
	}

	@Override
	public float getWidth() {
		return (float) (this.leftInset + this.width + this.rightInset);
	}
}
