package videogoose.combattweaks.utils;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Utility class for reflection-based operations.
 */
public class ReflectionUtils {

	public static Object invokePrivateMethod(Object instance, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
		Method method = instance.getClass().getDeclaredMethod(methodName, paramTypes);
		method.setAccessible(true);
		return method.invoke(instance, args);
	}

	public static Object invokePrivateStaticMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
		Method method = clazz.getDeclaredMethod(methodName, paramTypes);
		method.setAccessible(true);
		return method.invoke(null, args);
	}

	public static Object getPrivateField(Class<?> clazz, Object instance, String fieldName) throws Exception {
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(instance);
	}

	public static void setPrivateField(Class<?> clazz, Object instance, String fieldName, Object value) throws Exception {
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(instance, value);
	}

	/**
	 * Injects a new enum value into the specified enum class.
	 * @param enumClass The enum class to modify
	 * @param name The name of the new enum constant
	 * @param ordinal The ordinal value for the new enum constant
	 * @param params Additional parameters required by the enum constructor (if any)
	 * @return The newly created enum instance, or null if an error occurred
	 */
	public static Object injectEnumValue(Class<?> enumClass, String name, int ordinal, Object... params) throws Exception {
		// Get the $VALUES field which holds all enum constants
		Field valuesField = enumClass.getDeclaredField("$VALUES");
		valuesField.setAccessible(true);
		Object valuesArrayObj = valuesField.get(null);
		Object[] oldValues;

		// Handle both Object[] and properly typed enum arrays
		if(valuesArrayObj instanceof Object[]) {
			oldValues = (Object[]) valuesArrayObj;
		} else {
			throw new IllegalStateException("$VALUES field is not an Object array");
		}

		// Create a new array with one more element - MUST be properly typed
		Object newValuesObj = Array.newInstance(enumClass, oldValues.length + 1);
		Object[] newValues = (Object[]) newValuesObj;
		System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);

		// Build parameter types - String and int are always first for enums
		Class<?>[] paramTypes = new Class<?>[2 + params.length];
		paramTypes[0] = String.class;
		paramTypes[1] = int.class;

		// Convert parameter objects to their types, handling primitives correctly
		for(int i = 0; i < params.length; i++) {
			Class<?> paramClass = params[i].getClass();
			// Convert boxed types to their primitive equivalents if needed
			if(paramClass == Integer.class) {
				paramTypes[i + 2] = int.class;
			} else if(paramClass == Long.class) {
				paramTypes[i + 2] = long.class;
			} else if(paramClass == Double.class) {
				paramTypes[i + 2] = double.class;
			} else if(paramClass == Float.class) {
				paramTypes[i + 2] = float.class;
			} else if(paramClass == Boolean.class) {
				paramTypes[i + 2] = boolean.class;
			} else if(paramClass == Byte.class) {
				paramTypes[i + 2] = byte.class;
			} else if(paramClass == Short.class) {
				paramTypes[i + 2] = short.class;
			} else {
				paramTypes[i + 2] = paramClass;
			}
		}

		System.out.println("[CombatTweaks] [Reflection] Looking for constructor with types: " + Arrays.toString(paramTypes));
		System.out.println("[CombatTweaks] [Reflection] Available constructors in " + enumClass.getName() + ":");
		for(Constructor<?> c : enumClass.getDeclaredConstructors()) {
			System.out.println("[CombatTweaks] [Reflection]   - " + c);
		}

		// Try to find the constructor
		Constructor<?> constructor = null;
		try {
			constructor = enumClass.getDeclaredConstructor(paramTypes);
		} catch(NoSuchMethodException e) {
			// If exact match fails, try with boxed Integer type instead of int
			System.out.println("[CombatTweaks] [Reflection] Exact match failed, trying with boxed types...");
			Class<?>[] boxedParamTypes = new Class<?>[paramTypes.length];
			boxedParamTypes[0] = paramTypes[0]; // String stays String
			boxedParamTypes[1] = paramTypes[1]; // int stays int
			for(int i = 2; i < paramTypes.length; i++) {
				if(paramTypes[i] == int.class) {
					boxedParamTypes[i] = Integer.class;
				} else if(paramTypes[i] == long.class) {
					boxedParamTypes[i] = Long.class;
				} else {
					boxedParamTypes[i] = paramTypes[i];
				}
			}

			try {
				System.out.println("[CombatTweaks] [Reflection] Trying with boxed types: " + Arrays.toString(boxedParamTypes));
				constructor = enumClass.getDeclaredConstructor(boxedParamTypes);
			} catch(NoSuchMethodException e2) {
				System.out.println("[CombatTweaks] [Reflection] Boxed match also failed, searching all constructors...");
				Constructor<?>[] constructors = enumClass.getDeclaredConstructors();
				for(Constructor<?> c : constructors) {
					Class<?>[] ctorParams = c.getParameterTypes();
					if(ctorParams.length == paramTypes.length) {
						// Found a constructor with matching parameter count
						System.out.println("[CombatTweaks] [Reflection] Using constructor with matching parameter count: " + c);
						constructor = c;
						break;
					}
				}
				if(constructor == null) {
					throw new NoSuchMethodException("No matching constructor found for " + enumClass.getName() + " with " + paramTypes.length + " parameters");
				}
			}
		}

		constructor.setAccessible(true);

		// Build the arguments for the enum constructor
		Object[] args = new Object[2 + params.length];
		args[0] = name;
		args[1] = ordinal;
		System.arraycopy(params, 0, args, 2, params.length);

		System.out.println("[CombatTweaks] [Reflection] Creating enum instance with args: " + Arrays.toString(args));

		// Create the new enum instance
		Object newEnumInstance;

		// Method 1: Try ReflectionFactory (most reliable for enums)
		try {
			Class<?> reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory");
			Method getReflectionFactoryMethod = reflectionFactoryClass.getDeclaredMethod("getReflectionFactory");
			getReflectionFactoryMethod.setAccessible(true);
			Object reflectionFactory = getReflectionFactoryMethod.invoke(null);

			Method newConstructorAccessorMethod = reflectionFactoryClass.getDeclaredMethod("newConstructorAccessor", Constructor.class);
			newConstructorAccessorMethod.setAccessible(true);
			Object constructorAccessor = newConstructorAccessorMethod.invoke(reflectionFactory, constructor);

			Method newInstanceMethod = constructorAccessor.getClass().getDeclaredMethod("newInstance", Object[].class);
			newInstanceMethod.setAccessible(true); // CRITICAL: Must set accessible before invoke
			newEnumInstance = newInstanceMethod.invoke(constructorAccessor, new Object[]{args});
			System.out.println("[CombatTweaks] [Reflection] Used ReflectionFactory to create enum instance");
		} catch(Exception reflectionFactoryException) {
			System.err.println("[CombatTweaks] [Reflection] ReflectionFactory failed: " + reflectionFactoryException.getMessage());
			reflectionFactoryException.printStackTrace();

			// Method 2: Try setting override field to bypass access checks
			try {
				System.out.println("[CombatTweaks] [Reflection] Trying to bypass enum check via override field...");

				// Try to access the override field
				Field overrideField = null;
				try {
					overrideField = Constructor.class.getDeclaredField("override");
				} catch(NoSuchFieldException nsfe) {
					// Java 9+ uses different mechanism
					try {
						overrideField = AccessibleObject.class.getDeclaredField("override");
					} catch(NoSuchFieldException nsfe2) {
						System.err.println("[CombatTweaks] [Reflection] Could not find override field");
					}
				}

				if(overrideField != null) {
					overrideField.setAccessible(true);
					overrideField.set(constructor, true);
				}

				newEnumInstance = constructor.newInstance(args);
				System.out.println("[CombatTweaks] [Reflection] Successfully bypassed enum check");
			} catch(Exception directException) {
				System.err.println("[CombatTweaks] [Reflection] All methods failed!");
				directException.printStackTrace();
				throw new RuntimeException("Failed to create enum instance for " + name, directException);
			}
		}

		if(newEnumInstance == null) {
			throw new RuntimeException("Failed to create enum instance for " + name);
		}

		newValues[oldValues.length] = newEnumInstance;

		// Set the new array back using Unsafe to modify the static final field
		try {
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			Object unsafe = theUnsafeField.get(null);

			// Get the base object for static fields and the field offset
			Method staticFieldBaseMethod = unsafeClass.getDeclaredMethod("staticFieldBase", Field.class);
			Method staticFieldOffsetMethod = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

			Object staticFieldBase = staticFieldBaseMethod.invoke(unsafe, valuesField);
			long offset = (long) staticFieldOffsetMethod.invoke(unsafe, valuesField);

			// Use putObject instead of putObjectVolatile to avoid memory barriers during class init
			Method putObjectMethod = unsafeClass.getDeclaredMethod("putObject", Object.class, long.class, Object.class);
			putObjectMethod.invoke(unsafe, staticFieldBase, offset, newValues);

			System.out.println("[CombatTweaks] [Reflection] Used Unsafe to set static final $VALUES field");
		} catch(Exception unsafeException) {
			// Fallback: try removing final modifier and setting directly
			System.out.println("[CombatTweaks] [Reflection] Unsafe method failed, trying to remove final modifier...");
			System.err.println("[CombatTweaks] [Reflection] Unsafe error: " + unsafeException.getMessage());
			unsafeException.printStackTrace();
			try {
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(valuesField, valuesField.getModifiers() & ~Modifier.FINAL);
				valuesField.set(null, newValues);
				System.out.println("[CombatTweaks] [Reflection] Successfully modified final field by removing FINAL modifier");
			} catch(Exception modifierException) {
				System.err.println("[CombatTweaks] [Reflection] Failed to set $VALUES field: " + modifierException.getMessage());
				throw new RuntimeException("Could not set $VALUES field for enum injection", modifierException);
			}
		}

		System.out.println("[CombatTweaks] [Reflection] Successfully injected enum value: " + name);

		// Return the newly created enum instance
		return newEnumInstance;
	}
}
