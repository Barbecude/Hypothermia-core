import zipfile, subprocess

with zipfile.ZipFile(r'D:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\ColdSweat-2.4.3.jar', 'r') as z:
    for n in z.namelist():
        if 'Temperature$Trait' in n:
            z.extract(n, 'scratch')
            res = subprocess.run(['javap', '-p', 'scratch/' + n], capture_output=True, text=True)
            print(res.stdout)
