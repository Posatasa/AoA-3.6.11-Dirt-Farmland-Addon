import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Loads and links the add-on's classes against the full SRG classpath, which is what the game does
 * at startup, and checks that every concrete class actually implements the abstract methods it
 * inherits.
 *
 * <p>Loading alone is not enough for the second check: the JVM happily loads a concrete class that
 * forgot to implement an abstract method and only throws {@code AbstractMethodError} when something
 * calls it. That is exactly how a missing rename in an override (a creative tab's {@code makeIcon}
 * still spelled with its MCP name) crashed the game while opening the creative inventory. Asking
 * for the resulting method set shows the still-abstract method before anything calls it.
 *
 * <p>The classes are named as strings on purpose - this verifier must not depend on the mod itself,
 * or javac would need the very classpath that is being checked.
 */
public class VerifyAgainstSrg {
    public static void main(String[] args) throws Exception {
        String[] names = {
                "com.posatasa.aoa3farmland.AddonFarmland",
                "com.posatasa.aoa3farmland.FarmlandBlocks",
                "com.posatasa.aoa3farmland.FarmlandBlocks$1",
                "com.posatasa.aoa3farmland.block.AoAFarmlandBlock",
                "com.posatasa.aoa3farmland.mixin.MixinMayPlaceOn",
        };
        ClassLoader loader = VerifyAgainstSrg.class.getClassLoader();
        int failures = 0;
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                // Resolve every declared member's types without running static initialisers.
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    field.getType().getName();
                }
                for (Method method : type.getDeclaredMethods()) {
                    method.getReturnType().getName();
                    for (Class<?> parameter : method.getParameterTypes()) {
                        parameter.getName();
                    }
                }
                String abstractLeft = unimplemented(type);
                if (abstractLeft != null) {
                    System.out.println("FAIL  " + name + " - concrete class leaves abstract "
                            + abstractLeft + " unimplemented (calling it throws AbstractMethodError)");
                    failures++;
                } else {
                    System.out.println("OK    " + name + " - linked, "
                            + type.getDeclaredMethods().length + " methods");
                }
            } catch (Throwable t) {
                System.out.println("FAIL  " + name + " - " + t);
                t.printStackTrace(System.out);
                failures++;
            }
        }
        System.out.println(failures == 0 ? "ALL CLASSES LINK AGAINST THE SRG CLASSPATH"
                : failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }

    /** The first abstract method this concrete class still exposes, or null. */
    private static String unimplemented(Class<?> type) {
        if (Modifier.isAbstract(type.getModifiers()) || type.isInterface()) {
            return null;                       // abstract classes are allowed to leave them open
        }
        for (Method method : type.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                return method.getDeclaringClass().getName() + "#" + method.getName()
                        + java.util.Arrays.toString(method.getParameterTypes());
            }
        }
        return null;
    }
}
