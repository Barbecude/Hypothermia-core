import zipfile, subprocess, os, glob

# Search in ~/.gradle for minecraft client jar
gradle_user = os.path.expanduser('~/.gradle')
jars = glob.glob(os.path.join(gradle_user, '**/*minecraft*mapped*.jar'), recursive=True)
if not jars:
    jars = glob.glob(os.path.join(gradle_user, '**/*minecraft*.jar'), recursive=True)

print("Found jars:", len(jars))
gui_graphics_jar = None
for j in jars:
    try:
        with zipfile.ZipFile(j, 'r') as z:
            if 'net/minecraft/client/gui/GuiGraphics.class' in z.namelist():
                print("Found GuiGraphics in", j)
                gui_graphics_jar = j
                break
    except:
        pass

if gui_graphics_jar:
    with zipfile.ZipFile(gui_graphics_jar, 'r') as z:
        z.extract('net/minecraft/client/gui/GuiGraphics.class', 'scratch')
        z.extract('net/minecraft/client/renderer/entity/ItemRenderer.class', 'scratch')
    res = subprocess.run(['javap', '-p', 'scratch/net/minecraft/client/gui/GuiGraphics.class'], capture_output=True, text=True)
    for line in res.stdout.split('\n'):
        if 'renderItem' in line:
            print(line)
