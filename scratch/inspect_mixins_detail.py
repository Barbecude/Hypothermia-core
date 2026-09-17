import os, glob, zipfile, json

mods_dir = r"D:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods"

def inspect_jar(jar_name):
    jar_path = os.path.join(mods_dir, jar_name)
    if not os.path.exists(jar_path): return
    with zipfile.ZipFile(jar_path, 'r') as z:
        for name in z.namelist():
            if name.endswith('.json') and 'mixin' in name.lower():
                try:
                    data = json.loads(z.read(name).decode('utf-8'))
                    package = data.get('package', '')
                    mixins = data.get('mixins', []) + data.get('client', [])
                    for m in mixins:
                        if 'Item' in m or 'Gui' in m:
                            print(f"{jar_name}: {package}.{m}")
                except:
                    pass

for j in ["ImmediatelyFast-Fabric-1.6.14+1.21.1.jar", "sodium-fabric-0.8.13+mc1.21.1.jar", "fusion-1.3.15a-fabric-mc1.21.jar", "Kilt-21.1.12.jar"]:
    inspect_jar(j)
