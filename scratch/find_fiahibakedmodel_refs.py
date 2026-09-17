import zipfile, subprocess

jar_path = r"d:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\fiahi-4.1.0.jar"
with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if name.endswith('.class'):
            data = z.read(name)
            if b'FIAHIBakedModel' in data:
                print("Found FIAHIBakedModel reference in:", name)
