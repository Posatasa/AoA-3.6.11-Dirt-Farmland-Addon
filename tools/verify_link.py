"""Loads the built classes with the full SRG classpath, so the JVM verifier resolves every
reference the mod makes - the same resolution the game performs at startup."""
import glob
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


if __name__ == "__main__":
    jar = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        ROOT, "dist", "aoa3farmland-1.0.0.jar")
    stage = os.path.join(ROOT, "build", "verify")
    os.makedirs(stage, exist_ok=True)
    javac = os.path.join(JDK8, "bin", "javac.exe")
    java = os.path.join(JDK8, "bin", "java.exe")
    subprocess.run([javac, "-nowarn", "-d", stage,
                    os.path.join(HERE, "VerifyAgainstSrg.java")], check=True)
    classpath = os.pathsep.join([stage, jar, SRG_JAR] + cached_jars())
    result = subprocess.run([java, "-cp", classpath, "VerifyAgainstSrg"], text=True)
    raise SystemExit(result.returncode)
