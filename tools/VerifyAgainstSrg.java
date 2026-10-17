import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Loads and links the add-on's classes against the full SRG classpath, which is what the game
 * will do at runtime. Loading is the real test: the JVM verifier resolves every method and field
 * reference the classes make, so a bad remap shows up here rather than in game.
 *
 * The classes are named as strings on purpose - this verifier must not depend on the mod itself,
 * or javac would need the very classpath that is being checked.
 */
public class VerifyAgainstSrg {
    public static void main(String[] args) throws Exception {
        String[] names = {
                "com.posatasa.aoa3farmland.AddonFarmland",
                "com.posatasa.aoa3farmland.FarmlandBlocks",
                "com.posatasa.aoa3farmland.block.AoAFarmlandBlock",
        };
        ClassLoader loader = VerifyAgainstSrg.class.getClassLoader();
        int failures = 0;
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                // Resolve every declared member's types without running static initialisers.
                for (Field field : type.getDeclaredFields()) {
                    field.getType().getName();
                }
                for (Method method : type.getDeclaredMethods()) {
                    method.getReturnType().getName();
                    for (Class<?> parameter : method.getParameterTypes()) {
                        parameter.getName();
                    }
                }
                System.out.println("OK    " + name + " - linked, "
                        + type.getDeclaredMethods().length + " methods");
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
}
