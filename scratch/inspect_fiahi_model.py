import zipfile, subprocess, os

jar_path = r"d:\Users\Zevaldo\curseforge\minecraft\Instances\Fabric 1.21\mods\fiahi-4.1.0.jar"
with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if 'FIAHI' in name and name.endswith('.class'):
            z.extract(name, 'scratch')

for root, dirs, files in os.walk('scratch/com/hexagram2021/fiahi'):
    for f in files:
        if f.endswith('.class'):
            full = os.path.join(root, f)
            res = subprocess.run(['javap', '-p', full], capture_output=True, text=True)
            first_line = res.stdout.strip().split('\n')[1] if len(res.stdout.strip().split('\n')) > 1 else res.stdout
            print(f, "->", first_line)
