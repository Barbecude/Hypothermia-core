import os, glob, zipfile

mods_dir = r"D:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods"
for j in glob.glob(os.path.join(mods_dir, "*.jar")):
    try:
        with zipfile.ZipFile(j, 'r') as z:
            for name in z.namelist():
                if name.endswith('.class'):
                    data = z.read(name)
                    if b'ItemEntityRenderer' in data or b'ItemInHandRenderer' in data or b'ItemRenderer' in data:
                        # Filter out common libs unless relevant
                        if any(x in j for x in ['fiahi', 'hypothermia', 'kilt', 'cold', 'diet', 'eating']):
                            print(f"{os.path.basename(j)} -> {name}")
    except:
        pass
