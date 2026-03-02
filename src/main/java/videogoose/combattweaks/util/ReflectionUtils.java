package videogoose.combattweaks.util;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Small reflection helpers used by mixins.
 */
public final class ReflectionUtils {

	private ReflectionUtils() {
	}

	/**
	 * Create a new enum constant instance for the given enum class and append it to
	 * the enum's internal values array.
	 *
	 * @param enumClass   The enum class to extend
	 * @param name        The name of the new enum constant
	 * @param ordinal     The ordinal to assign to the new constant
	 * @param extraArgs   Extra constructor arguments (the parameters after the implicit String,int)
	 * @param <E>         Enum type
	 * @return The newly created enum constant instance
	 * @throws Exception on reflection failures
	 */
	@SuppressWarnings("unchecked")
	public static <E extends Enum<?>> E createEnumConstant(Class<E> enumClass, String name, int ordinal, Object... extraArgs) throws Exception {
		// Find a constructor whose first two parameters are (String, int) (or Integer)
		Constructor<?> chosen = null;
		for(Constructor<?> c : enumClass.getDeclaredConstructors()) {
			Class<?>[] pts = c.getParameterTypes();
			if(pts.length >= 2 && pts[0] == String.class && (pts[1] == int.class || pts[1] == Integer.class)) {
				chosen = c;
				break;
			}
		}
		if(chosen == null) {
			throw new NoSuchMethodException("No suitable enum constructor found for " + enumClass.getName());
		}
		chosen.setAccessible(true);

		Class<?>[] params = chosen.getParameterTypes();
		Object[] args = new Object[params.length];
		args[0] = name;
		args[1] = ordinal;

		// Fill remaining args with provided extraArgs where possible, or null defaults
		for(int i = 2; i < params.length; i++) {
			int extraIndex = i - 2;
			Object provided = extraIndex < extraArgs.length ? extraArgs[extraIndex] : null;
			Class<?> p = params[i];

			if(provided == null) {
				// Provide sensible defaults for primitives
				if(p == int.class) {
					args[i] = 0;
				} else if(p == short.class) {
					args[i] = (short) 0;
				} else if(p == boolean.class) {
					args[i] = false;
				} else {
					args[i] = null;
				}
				continue;
			}

			// If the parameter is primitive, attempt to box/unbox appropriately
			if(p == int.class) {
				args[i] = ((Number) provided).intValue();
			} else if(p == short.class) {
				args[i] = ((Number) provided).shortValue();
			} else if(p == boolean.class) {
				args[i] = provided;
			} else if(p.isPrimitive()) {
				// Try to handle other primitives generically
				args[i] = provided;
			} else {
				// Reference type - attempt assign
				if(!p.isInstance(provided)) {
					// Attempt simple convert for common cases (Number -> wrapper)
					if(provided instanceof Number && Number.class.isAssignableFrom(p)) {
						args[i] = provided;
					} else {
						// Best-effort - pass the provided value (may fail at invocation)
						args[i] = provided;
					}
				} else {
					args[i] = provided;
				}
			}
		}

		Object instance = chosen.newInstance(args);
		E enumInstance = (E) instance;

		// Locate the enum values field and append the new instance
		Field valuesField = null;
		for(Field f : enumClass.getDeclaredFields()) {
			if(f.getType().isArray() && f.getType().getComponentType() == enumClass) {
				valuesField = f;
				break;
			}
		}
		if(valuesField == null) {
			throw new NoSuchFieldException("Could not find enum values field for " + enumClass.getName());
		}
		valuesField.setAccessible(true);

		Object arrayObj = valuesField.get(null);
		int length = Array.getLength(arrayObj);
		Object newArray = Array.newInstance(enumClass, length + 1);
		for(int i = 0; i < length; i++) {
			Object val = Array.get(arrayObj, i);
			Array.set(newArray, i, val);
		}
		Array.set(newArray, length, enumInstance);
		valuesField.set(null, newArray);

		return enumInstance;
	}
}
