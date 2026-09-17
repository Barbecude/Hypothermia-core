import zipfile, subprocess

with zipfile.ZipFile(r'd:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\Kilt-21.1.12.jar', 'r') as z:
    for n in z.namelist():
        if 'ItemRenderer' in n:
            print(n)
            z.extract(n, 'scratch')

res = subprocess.run(['javap', '-c', '-p', 'scratch/net/neoforged/neoforge/client/ItemRendererInject.class'], capture_output=True, text=True)
print(res.stdout)
