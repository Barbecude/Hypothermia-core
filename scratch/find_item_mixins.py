import os, glob, zipfile, json

mods_dir = r"D:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods"
for jar in glob.glob(os.path.join(mods_dir, "*.jar")):
    try:
        with zipfile.ZipFile(jar, 'r') as z:
            for name in z.namelist():
                if name.endswith('.json') and 'mixin' in name.lower():
                    try:
                        content = z.read(name).decode('utf-8', errors='ignore')
                        if 'GuiGraphics' in content or 'ItemRenderer' in content:
                            print(f"In {os.path.basename(jar)} -> {name}")
                    except:
                        pass
    except:
        pass
