import subprocess

res = subprocess.run(['javap', '-c', '-p', 'scratch/net/minecraft/client/gui/GuiGraphics.class'], capture_output=True, text=True)
lines = res.stdout.split('\n')
capture = False
for line in lines:
    if 'renderItem(net.minecraft.world.entity.LivingEntity, net.minecraft.world.level.Level, net.minecraft.world.item.ItemStack, int, int, int, int)' in line:
        capture = True
    if capture:
        print(line)
        if line.strip() == 'return' or line.strip() == '}':
            break
