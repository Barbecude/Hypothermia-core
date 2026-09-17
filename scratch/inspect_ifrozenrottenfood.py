import zipfile, subprocess

jar_path = r"d:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\fiahi-4.1.0.jar"
with zipfile.ZipFile(jar_path, 'r') as z:
    for n in z.namelist():
        if 'IFrozenRottenFood' in n:
            z.extract(n, 'scratch')
            res = subprocess.run(['javap', '-c', '-p', 'scratch/' + n], capture_output=True, text=True)
            print(res.stdout)
