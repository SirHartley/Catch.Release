package catchrelease.helper.reflection;

import com.fs.starfarer.api.Global;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The little reflection this mod needs, done in the one way that works inside the game.
 * <p>
 * Starsector's mod classloader denies mod code access to {@code java.lang.reflect}, so
 * {@code getDeclaredMethod}/{@code invoke} called directly throw at runtime.
 * {@link MethodHandles} is not blocked, so everything here goes through handles onto the
 * reflection API instead of touching it directly.
 * <p>
 * Only ever look members up by names that are actually stable - real words the obfuscator leaves
 * alone, like {@code getParent} or {@code getChildrenCopy}. Anything that looks generated
 * ({@code o0OO}, {@code Ò00000}, {@code interfacenew}) is a different name in the next game build
 * and must not be reached for by name.
 */
public class ReflectionUtils {

    private static MethodHandle getDeclaredMethods;
    private static MethodHandle methodGetName;
    private static MethodHandle methodGetParameterCount;
    private static MethodHandle methodSetAccessible;
    private static MethodHandle methodInvoke;

    private static boolean available = false;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Class<?> methodClass = Class.forName("java.lang.reflect.Method");
            Class<?> methodArrayClass = Class.forName("[Ljava.lang.reflect.Method;");

            getDeclaredMethods = lookup.findVirtual(Class.class, "getDeclaredMethods",
                    MethodType.methodType(methodArrayClass));
            methodGetName = lookup.findVirtual(methodClass, "getName",
                    MethodType.methodType(String.class));
            methodGetParameterCount = lookup.findVirtual(methodClass, "getParameterCount",
                    MethodType.methodType(int.class));
            methodSetAccessible = lookup.findVirtual(methodClass, "setAccessible",
                    MethodType.methodType(void.class, boolean.class));
            //asFixedArity: Method#invoke is itself varargs, and a collector handle would take the
            //argument array as one more argument to collect rather than as the arguments themselves
            methodInvoke = lookup.findVirtual(methodClass, "invoke",
                    MethodType.methodType(Object.class, Object.class, Object[].class)).asFixedArity();

            available = true;
        } catch (Throwable t) {
            Global.getLogger(ReflectionUtils.class).error("Could not set up reflection", t);
        }
    }

    /**
     * Calls a method by name on whatever the object is, walking up its class hierarchy to find it.
     * Matched on name and argument count, which is enough for the obfuscated UI classes - they do
     * not overload on argument types.
     *
     * @return whatever the method returned, or null if there is no such method or the call threw
     */
    public static Object invoke(String methodName, Object instance, Object... args) {
        if (!available || instance == null) return null;

        Object method = findMethod(methodName, instance.getClass(), args.length);
        if (method == null) return null;

        try {
            methodSetAccessible.invokeWithArguments(method, true);
        } catch (Throwable ignored) {
            //public methods on public classes do not need it, and it is not worth failing over
        }

        try {
            return methodInvoke.invokeWithArguments(method, instance, args);
        } catch (Throwable t) {
            Global.getLogger(ReflectionUtils.class).error("Could not call " + methodName
                    + " on " + instance.getClass().getName(), t);
            return null;
        }
    }

    /** The {@code java.lang.reflect.Method}, as an opaque object - it cannot be named here. */
    private static Object findMethod(String methodName, Class<?> clazz, int argCount) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            Object[] methods;
            try {
                methods = (Object[]) getDeclaredMethods.invokeWithArguments(current);
            } catch (Throwable t) {
                return null;
            }

            for (Object method : methods) {
                try {
                    if (!methodName.equals(methodGetName.invokeWithArguments(method))) continue;
                    if ((Integer) methodGetParameterCount.invokeWithArguments(method) != argCount) continue;
                } catch (Throwable t) {
                    continue;
                }

                return method;
            }
        }

        return null;
    }
}
