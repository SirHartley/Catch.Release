package catchrelease.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Reflection that Starsector's classloader will actually let a mod perform.
 * <p>
 * The game rejects any mod class whose constant pool names {@code java.lang.reflect} directly, so every
 * reflective operation here goes through a {@link MethodHandle} bound once at class-init time via
 * {@link Class#forName(String, boolean, ClassLoader)} with {@code initialize=false} off the bootstrap
 * loader - the reflect classes are never named or loaded through the mod's own classloader.
 * <p>
 * Obfuscated member names differ per release and platform, so matching goes by shape instead (primitive
 * types, JDK types, implemented API interfaces), using the real JLS 5.3 invocation-compatibility rules
 * (widening, boxing) for parameter matching.
 * <p>
 * Ported from Starficz's ReflectionUtils.
 */
public final class ReflectionUtils {

    private ReflectionUtils() {
    }

    // --- Handles onto java.lang.reflect, none of which may be named directly. ---

    private static final MethodHandle GET_FIELD;
    private static final MethodHandle SET_FIELD;
    private static final MethodHandle GET_FIELD_TYPE;
    private static final MethodHandle GET_FIELD_NAME;
    private static final MethodHandle SET_FIELD_ACCESSIBLE;

    private static final MethodHandle INVOKE_METHOD;
    private static final MethodHandle GET_METHOD_NAME;
    private static final MethodHandle GET_METHOD_RETURN;
    private static final MethodHandle GET_METHOD_PARAMS;
    private static final MethodHandle SET_METHOD_ACCESSIBLE;

    private static final MethodHandle NEW_INSTANCE;
    private static final MethodHandle GET_CONSTRUCTOR_PARAMS;
    private static final MethodHandle SET_CONSTRUCTOR_ACCESSIBLE;

    static {
        try {
            ClassLoader bootstrap = Class.class.getClassLoader();

            Class<?> fieldClass = Class.forName("java.lang.reflect.Field", false, bootstrap);
            GET_FIELD = handle(fieldClass, "get", Object.class, Object.class);
            SET_FIELD = handle(fieldClass, "set", void.class, Object.class, Object.class);
            GET_FIELD_TYPE = handle(fieldClass, "getType", Class.class);
            GET_FIELD_NAME = handle(fieldClass, "getName", String.class);
            SET_FIELD_ACCESSIBLE = handle(fieldClass, "setAccessible", void.class, boolean.class);

            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, bootstrap);
            // invoke/newInstance are varargs; asFixedArity() stops the trailing array being wrapped again.
            INVOKE_METHOD = handle(methodClass, "invoke", Object.class, Object.class, Object[].class).asFixedArity();
            GET_METHOD_NAME = handle(methodClass, "getName", String.class);
            GET_METHOD_RETURN = handle(methodClass, "getReturnType", Class.class);
            GET_METHOD_PARAMS = handle(methodClass, "getParameterTypes", Class[].class);
            SET_METHOD_ACCESSIBLE = handle(methodClass, "setAccessible", void.class, boolean.class);

            Class<?> constructorClass = Class.forName("java.lang.reflect.Constructor", false, bootstrap);
            NEW_INSTANCE = handle(constructorClass, "newInstance", Object.class, Object[].class).asFixedArity();
            GET_CONSTRUCTOR_PARAMS = handle(constructorClass, "getParameterTypes", Class[].class);
            SET_CONSTRUCTOR_ACCESSIBLE = handle(constructorClass, "setAccessible", void.class, boolean.class);
        } catch (Throwable t) {
            throw new RuntimeException("Could not bind method handles onto java.lang.reflect", t);
        }
    }

    private static MethodHandle handle(Class<?> owner, String name, Class<?> returnType, Class<?>... paramTypes)
            throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.lookup().findVirtual(owner, name, MethodType.methodType(returnType, paramTypes));
    }

    // --- Reading the reflect objects, each call laundered through a handle. ---

    private static String nameOf(Object field) {
        try {
            return (String) GET_FIELD_NAME.invoke(field);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a field's name", t);
        }
    }

    private static Class<?> typeOf(Object field) {
        try {
            return (Class<?>) GET_FIELD_TYPE.invoke(field);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a field's type", t);
        }
    }

    private static String methodNameOf(Object method) {
        try {
            return (String) GET_METHOD_NAME.invoke(method);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a method's name", t);
        }
    }

    private static Class<?> returnTypeOf(Object method) {
        try {
            return (Class<?>) GET_METHOD_RETURN.invoke(method);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a method's return type", t);
        }
    }

    private static Class<?>[] paramTypesOf(Object method) {
        try {
            return (Class<?>[]) GET_METHOD_PARAMS.invoke(method);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a method's parameter types", t);
        }
    }

    private static Class<?>[] constructorParamTypesOf(Object constructor) {
        try {
            return (Class<?>[]) GET_CONSTRUCTOR_PARAMS.invoke(constructor);
        } catch (Throwable t) {
            throw new RuntimeException("Could not read a constructor's parameter types", t);
        }
    }

    // --- Convenience entry points. Each demands exactly one match. ---

    /** Reads the one field of the given name off the instance's own class. Throws if none or several match. */
    public static Object get(Object instance, String name) {
        return get(instance, name, null, false);
    }

    /**
     * Reads the one field named {@code name} whose declared type is assignable to {@code assignableTo} -
     * for reaching an obfuscated field when only the API interface it implements is known. Either
     * criterion may be null.
     */
    public static Object get(Object instance, String name, Class<?> assignableTo) {
        return get(instance, name, assignableTo, false);
    }

    /**
     * Reads the one matching field, optionally walking the superclass chain up to (but not including)
     * {@code Object}. Throws if nothing matches or more than one thing does.
     */
    public static Object get(Object instance, String name, Class<?> assignableTo, boolean searchSuperclass) {
        Class<?> clazz = instance.getClass();
        List<ReflectedField> matches = getFieldsMatching(clazz, name, null, assignableTo, null, searchSuperclass);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No field found for name: '" + orAny(name) + "' on class: "
                    + clazz.getName() + " that is assignable to type: '" + orAny(assignableTo) + "'.");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous fields with name: '" + orAny(name) + "' on class: "
                    + clazz.getName() + " assignable to type: '" + orAny(assignableTo) + "'. Multiple fields match.");
        }
        return matches.get(0).get(instance);
    }

    /** Writes the one field that carries the given name and can legally accept the value's type. */
    public static void set(Object instance, String name, Object value) {
        set(instance, name, value, false);
    }

    /**
     * Writes the one matching field, optionally walking the superclass chain. The value's runtime type
     * is checked against each candidate's declared type under the ordinary assignment rules, so a boxed
     * number will find a primitive field.
     */
    public static void set(Object instance, String name, Object value, boolean searchSuperclass) {
        Class<?> clazz = instance.getClass();
        Class<?> valueType = value == null ? null : value.getClass();
        List<ReflectedField> matches = getFieldsMatching(clazz, name, null, null, valueType, searchSuperclass);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No field found for name: '" + orAny(name) + "' on class: "
                    + clazz.getName() + " that accepts type: '" + orNull(valueType) + "'.");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous fields with name: '" + orAny(name) + "' on class: "
                    + clazz.getName() + " accepting type: '" + orNull(valueType) + "'. Multiple fields match.");
        }
        matches.get(0).set(instance, value);
    }

    /**
     * Calls the one method named {@code name} whose parameters accept the given arguments. The overload
     * is chosen from the arguments' runtime types; an ambiguous call throws rather than guessing.
     */
    public static Object invoke(Object instance, String name, Object... args) {
        Object[] arguments = args == null ? new Object[0] : args;
        Class<?> clazz = instance.getClass();
        Class<?>[] paramTypes = typesOf(arguments);
        List<ReflectedMethod> matches = getMethodsMatching(clazz, name, null, null, paramTypes);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No method found for name: '" + name + "' on class: " + clazz.getName()
                    + " with compatible parameter types derived from arguments: " + describe(paramTypes));
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous method call for name: '" + name + "' on class: "
                    + clazz.getName() + ". Multiple methods match parameter types derived from arguments: "
                    + describe(paramTypes));
        }
        return matches.get(0).invoke(instance, arguments);
    }

    /** Calls the one static method named {@code name} whose parameters accept the given arguments. Same all-or-nothing matching as {@link #invoke}. */
    public static Object invokeStatic(Class<?> clazz, String name, Object... args) {
        Object[] arguments = args == null ? new Object[0] : args;
        Class<?>[] paramTypes = typesOf(arguments);
        List<ReflectedMethod> matches = getMethodsMatching(clazz, name, null, null, paramTypes);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No method found for name: '" + name + "' on class: " + clazz.getName()
                    + " with compatible parameter types derived from arguments: " + describe(paramTypes));
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous method call for name: '" + name + "' on class: "
                    + clazz.getName() + ". Multiple methods match parameter types derived from arguments: "
                    + describe(paramTypes));
        }
        return matches.get(0).invoke(null, arguments);
    }

    // --- Capability checks and forgiving calls, for crawling UI objects. ---

    /** Whether the instance's class, or anything it inherits publicly from, declares this method name. */
    public static boolean hasMethodOfName(Object instance, String name) {
        return !getMethodsMatching(instance.getClass(), name, null, null, null).isEmpty();
    }

    /** Whether the instance's class or any superclass below {@code Object} declares this field name. */
    public static boolean hasFieldOfName(Object instance, String name) {
        return !getFieldsMatching(instance.getClass(), name, null, null, null, true).isEmpty();
    }

    /** Whether the instance carries a field whose declared type is assignable to the given type. */
    public static boolean hasFieldOfType(Object instance, Class<?> type) {
        return !getFieldsMatching(instance.getClass(), null, null, type, null, true).isEmpty();
    }

    /** Calls the named method if exactly one match exists, else returns null instead of throwing. */
    public static Object invokeIfExists(Object instance, String name, Object... args) {
        Object[] arguments = args == null ? new Object[0] : args;
        List<ReflectedMethod> matches = getMethodsMatching(instance.getClass(), name, null, null, typesOf(arguments));
        if (matches.size() != 1) return null;
        return matches.get(0).invoke(instance, arguments);
    }

    // --- Matching. ---

    /**
     * Finds every field of the class matching whichever of {@code name}, {@code exactType},
     * {@code assignableTo} and {@code accepts} are non-null; {@code assignableTo} is for reading
     * (field type must be a subtype), {@code accepts} is for writing (field must legally hold a value of
     * that type). Plain {@code Object}-typed fields are dropped unless requested by name or by
     * {@code Object} type explicitly, since otherwise they'd match every type query.
     */
    public static List<ReflectedField> getFieldsMatching(Class<?> clazz, String name, Class<?> exactType,
                                                        Class<?> assignableTo, Class<?> accepts,
                                                        boolean searchSuperclass) {
        List<ReflectedField> matches = new ArrayList<>();
        for (Object field : collectFields(clazz, searchSuperclass)) {
            if (name != null && !name.equals(nameOf(field))) continue;

            if (exactType != null || accepts != null || assignableTo != null) {
                Class<?> fieldType = typeOf(field);

                if (exactType != null && !exactType.equals(fieldType)) continue;
                if (accepts != null && !isParameterCompatible(fieldType, accepts)) continue;
                if (assignableTo != null && !assignableTo.isAssignableFrom(fieldType)) continue;

                if (fieldType == Object.class
                        && name == null
                        && exactType != Object.class
                        && assignableTo != Object.class
                        && accepts != Object.class) {
                    continue;
                }
            }
            matches.add(new ReflectedField(field));
        }
        return matches;
    }

    /** Instance-shaped {@link #getFieldsMatching(Class, String, Class, Class, Class, boolean)}. */
    public static List<ReflectedField> getFieldsMatching(Object instance, String name, Class<?> exactType,
                                                        Class<?> assignableTo, Class<?> accepts,
                                                        boolean searchSuperclass) {
        return getFieldsMatching(instance.getClass(), name, exactType, assignableTo, accepts, searchSuperclass);
    }

    /**
     * Finds every method of the class - declared, plus everything public it inherits - matching
     * whichever criteria are given. {@code returnTypeAssignableTo} requires the actual return type be a
     * subtype; {@code parameterTypes} is checked position by position under the invocation rules, with a
     * null element meaning "any parameter null could be passed to". Any criterion may be null.
     */
    public static List<ReflectedMethod> getMethodsMatching(Class<?> clazz, String name,
                                                          Class<?> returnTypeAssignableTo, Integer numOfParams,
                                                          Class<?>[] parameterTypes) {
        List<ReflectedMethod> matches = new ArrayList<>();
        for (Object method : collectMethods(clazz)) {
            if (name != null && !name.equals(methodNameOf(method))) continue;

            if (returnTypeAssignableTo != null && !returnTypeAssignableTo.isAssignableFrom(returnTypeOf(method))) {
                continue;
            }

            if (numOfParams != null || parameterTypes != null) {
                Class<?>[] actual = paramTypesOf(method);

                if (numOfParams != null && numOfParams != actual.length) continue;
                if (parameterTypes != null && !parametersCompatible(actual, parameterTypes)) continue;
            }
            matches.add(new ReflectedMethod(method));
        }
        return matches;
    }

    /** Instance-shaped {@link #getMethodsMatching(Class, String, Class, Integer, Class[])}. */
    public static List<ReflectedMethod> getMethodsMatching(Object instance, String name,
                                                           Class<?> returnTypeAssignableTo, Integer numOfParams,
                                                           Class<?>[] parameterTypes) {
        return getMethodsMatching(instance.getClass(), name, returnTypeAssignableTo, numOfParams, parameterTypes);
    }

    /** Finds every declared constructor whose parameter list matches by count or by type, same invocation rules as methods. Either criterion may be null. */
    public static List<ReflectedConstructor> getConstructorsMatching(Class<?> clazz, Integer numOfParams,
                                                                    Class<?>[] parameterTypes) {
        List<ReflectedConstructor> matches = new ArrayList<>();
        Object[] constructors = clazz.getDeclaredConstructors();
        for (Object constructor : constructors) {
            Class<?>[] actual = constructorParamTypesOf(constructor);

            if (numOfParams != null && numOfParams != actual.length) continue;
            if (parameterTypes != null && !parametersCompatible(actual, parameterTypes)) continue;

            matches.add(new ReflectedConstructor(constructor));
        }
        return matches;
    }

    private static boolean parametersCompatible(Class<?>[] actual, Class<?>[] requested) {
        if (requested.length != actual.length) return false;
        for (int i = 0; i < requested.length; i++) {
            if (!isParameterCompatible(actual[i], requested[i])) return false;
        }
        return true;
    }

    private static Set<Object> collectFields(Class<?> clazz, boolean searchSuperclass) {
        Set<Object> fields = new LinkedHashSet<>();
        if (!searchSuperclass) {
            Object[] declared = clazz.getDeclaredFields();
            Collections.addAll(fields, declared);
            return fields;
        }
        // Stops at Object, whose own fields belong to nobody's search.
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Object[] declared = current.getDeclaredFields();
            Collections.addAll(fields, declared);
            current = current.getSuperclass();
        }
        return fields;
    }

    private static Set<Object> collectMethods(Class<?> clazz) {
        Set<Object> methods = new LinkedHashSet<>();
        Object[] declared = clazz.getDeclaredMethods();
        Object[] inherited = clazz.getMethods();
        Collections.addAll(methods, declared);
        Collections.addAll(methods, inherited);
        return methods;
    }

    // --- Assignment compatibility, JLS 5.3. ---

    /** Every primitive paired with the class that boxes it. */
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = new HashMap<>();

    /** The same pairs read the other way. */
    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();

    /** Keyed by target primitive: the primitives that widen into it without a cast. */
    private static final Map<Class<?>, Set<Class<?>>> PRIMITIVE_WIDENS_FROM = new HashMap<>();

    static {
        PRIMITIVE_TO_WRAPPER.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_WRAPPER.put(byte.class, Byte.class);
        PRIMITIVE_TO_WRAPPER.put(char.class, Character.class);
        PRIMITIVE_TO_WRAPPER.put(short.class, Short.class);
        PRIMITIVE_TO_WRAPPER.put(int.class, Integer.class);
        PRIMITIVE_TO_WRAPPER.put(long.class, Long.class);
        PRIMITIVE_TO_WRAPPER.put(float.class, Float.class);
        PRIMITIVE_TO_WRAPPER.put(double.class, Double.class);
        PRIMITIVE_TO_WRAPPER.put(void.class, Void.class);

        for (Map.Entry<Class<?>, Class<?>> entry : PRIMITIVE_TO_WRAPPER.entrySet()) {
            WRAPPER_TO_PRIMITIVE.put(entry.getValue(), entry.getKey());
        }

        PRIMITIVE_WIDENS_FROM.put(short.class, widensFrom(byte.class));
        PRIMITIVE_WIDENS_FROM.put(int.class, widensFrom(byte.class, short.class, char.class));
        PRIMITIVE_WIDENS_FROM.put(long.class, widensFrom(byte.class, short.class, char.class, int.class));
        PRIMITIVE_WIDENS_FROM.put(float.class,
                widensFrom(byte.class, short.class, char.class, int.class, long.class));
        PRIMITIVE_WIDENS_FROM.put(double.class,
                widensFrom(byte.class, short.class, char.class, int.class, long.class, float.class));
    }

    private static Set<Class<?>> widensFrom(Class<?>... types) {
        Set<Class<?>> set = new HashSet<>();
        Collections.addAll(set, types);
        return set;
    }

    /**
     * Whether a value of {@code callerArgType} could be passed where {@code targetType} is declared,
     * counting reference widening, primitive widening, boxing and unboxing exactly as the compiler
     * would. A null caller type means "passing null", accepted by every reference parameter and no
     * primitive one.
     */
    private static boolean isParameterCompatible(Class<?> targetType, Class<?> callerArgType) {
        if (callerArgType == null) return !targetType.isPrimitive();
        if (targetType.equals(callerArgType)) return true;

        boolean targetPrimitive = targetType.isPrimitive();
        boolean callerPrimitive = callerArgType.isPrimitive();

        // Reference to reference: plain widening, Integer into Number.
        if (!callerPrimitive && !targetPrimitive) {
            return targetType.isAssignableFrom(callerArgType);
        }

        // Primitive into primitive: widening, int into long.
        if (callerPrimitive && targetPrimitive) {
            Set<Class<?>> widens = PRIMITIVE_WIDENS_FROM.get(targetType);
            return widens != null && widens.contains(callerArgType);
        }

        // Primitive into reference: box first, then widen - int into Integer into Number.
        if (callerPrimitive) {
            Class<?> boxed = PRIMITIVE_TO_WRAPPER.get(callerArgType);
            return boxed != null && targetType.isAssignableFrom(boxed);
        }

        // Reference into primitive: unbox first, then widen - Integer into int into long.
        Class<?> unboxed = WRAPPER_TO_PRIMITIVE.get(callerArgType);
        if (unboxed == null) return false;
        if (unboxed.equals(targetType)) return true;
        Set<Class<?>> widens = PRIMITIVE_WIDENS_FROM.get(targetType);
        return widens != null && widens.contains(unboxed);
    }

    // --- Small formatting helpers for the ambiguity messages. ---

    private static Class<?>[] typesOf(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? null : args[i].getClass();
        }
        return types;
    }

    private static String orAny(String name) {
        return name == null ? "<any>" : name;
    }

    private static String orAny(Class<?> type) {
        return type == null ? "<any>" : type.getName();
    }

    private static String orNull(Class<?> type) {
        return type == null ? "null" : type.getName();
    }

    private static String describe(Class<?>[] types) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) out.append(", ");
            out.append(types[i] == null ? "null" : types[i].getName());
        }
        return out.append(']').toString();
    }

    // --- Wrappers. The reflect object inside each is only ever an Object here. ---

    /**
     * One field found by a search, held as an opaque object and reached only through the handles.
     * Accessibility is forced open before every read and write.
     */
    public static final class ReflectedField {
        private final Object field;

        /** The field's declared type. */
        public final Class<?> type;

        /** The field's declared name (often obfuscated in game code). */
        public final String name;

        private ReflectedField(Object field) {
            this.field = field;
            this.type = typeOf(field);
            this.name = nameOf(field);
        }

        /** Reads the field off the given instance, or off the class itself when passed null. */
        public Object get(Object instance) {
            try {
                SET_FIELD_ACCESSIBLE.invoke(field, true);
                return GET_FIELD.invoke(field, instance);
            } catch (Throwable t) {
                throw new RuntimeException("Could not read field '" + name + "'", t);
            }
        }

        /** Writes the field on the given instance, or on the class itself when passed null. */
        public void set(Object instance, Object value) {
            try {
                SET_FIELD_ACCESSIBLE.invoke(field, true);
                SET_FIELD.invoke(field, instance, value);
            } catch (Throwable t) {
                throw new RuntimeException("Could not write field '" + name + "'", t);
            }
        }
    }

    /**
     * One method found by a search. Accessibility is forced open before every call, and anything the
     * method throws comes back wrapped.
     */
    public static final class ReflectedMethod {
        private final Object method;

        /** The method's declared parameter types, in order. */
        public final Class<?>[] parameterTypes;

        /** The method's declared return type, {@code void.class} included. */
        public final Class<?> returnType;

        /** The method's declared name (often obfuscated in game code). */
        public final String name;

        private ReflectedMethod(Object method) {
            this.method = method;
            this.parameterTypes = paramTypesOf(method);
            this.returnType = returnTypeOf(method);
            this.name = methodNameOf(method);
        }

        /** Calls the method on the given instance, or as a static when passed null. Void and null-returning methods both come back null. */
        public Object invoke(Object instance, Object... arguments) {
            Object[] args = arguments == null ? new Object[0] : arguments;
            try {
                SET_METHOD_ACCESSIBLE.invoke(method, true);
                return INVOKE_METHOD.invoke(method, instance, args);
            } catch (Throwable t) {
                throw new RuntimeException("Could not invoke method '" + name + "'", t);
            }
        }
    }

    /** One constructor found by a search, for building instances of classes with obfuscated names or non-public constructors. */
    public static final class ReflectedConstructor {
        private final Object constructor;

        /** The constructor's declared parameter types, in order. */
        public final Class<?>[] parameterTypes;

        private ReflectedConstructor(Object constructor) {
            this.constructor = constructor;
            this.parameterTypes = constructorParamTypesOf(constructor);
        }

        /** Builds a new instance with the given arguments. */
        public Object newInstance(Object... arguments) {
            Object[] args = arguments == null ? new Object[0] : arguments;
            try {
                SET_CONSTRUCTOR_ACCESSIBLE.invoke(constructor, true);
                return NEW_INSTANCE.invoke(constructor, args);
            } catch (Throwable t) {
                throw new RuntimeException("Could not invoke constructor", t);
            }
        }
    }
}
