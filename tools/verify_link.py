"""Loads the built classes with the full SRG classpath, so the JVM verifier resolves every
reference the mod makes - the same resolution the game performs at startup.

Also applies Forge's own listener rule: `EventBus#addListener` rejects any event that extends
`GenericEvent`, and it does so while the mod is being constructed (i.e. it takes the game down at
startup). Both checks are needed - the first catches broken bytecode, the second catches a valid
class that registers itself wrongly.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GRADLE_HOME = os.path.join(os.path.expanduser("~"), ".gradle")
SRG_JAR = os.path.join(
    GRADLE_HOME, "caches", "forge_gradle", "minecraft_user_repo", "net", "minecraftforge",
    "forge", "1.16.5-36.2.34", "forge-1.16.5-36.2.34-srg.jar")
JDK8 = r"C:\Program Files\Java\jdk-1.8"


def cached_jars():
    root = os.path.join(GRADLE_HOME, "caches", "modules-2", "files-2.1")
    jars = []
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if name.endswith(".jar") and "sources" not in name and "javadoc" not in name:
                jars.append(os.path.join(dirpath, name))
    return sorted(jars)


def run(program):
    result = subprocess.run([JAVA, "-cp", CLASSPATH, program], text=True)
    return result.returncode


if __name__ == "__main__":
    if len(sys.argv) > 1:
        JAR = sys.argv[1]
    else:
        dist = os.path.join(ROOT, "dist")
        jars = sorted(n for n in os.listdir(dist) if n.endswith(".jar"))
        if len(jars) != 1:
            raise SystemExit("expected exactly one jar in dist/, found %r" % jars)
        JAR = os.path.join(dist, jars[0])
    print("verifying", JAR)
    stage = os.path.join(ROOT, "build", "verify")
    os.makedirs(stage, exist_ok=True)
    JAVAC = os.path.join(JDK8, "bin", "javac.exe")
    JAVA = os.path.join(JDK8, "bin", "java.exe")
    sources = [os.path.join(HERE, name) for name in ("VerifyAgainstSrg.java",
                                                     "VerifyListeners.java")]
    subprocess.run([JAVAC, "-nowarn", "-d", stage] + sources, check=True)
    CLASSPATH = os.pathsep.join([stage, JAR, SRG_JAR] + cached_jars())

    failures = 0
    print("== bytecode links against the SRG classpath ==")
    failures += run("VerifyAgainstSrg") != 0
    print()
    print("== every listener is registrable with addListener ==")
    failures += run("VerifyListeners") != 0
    raise SystemExit(1 if failures else 0)
