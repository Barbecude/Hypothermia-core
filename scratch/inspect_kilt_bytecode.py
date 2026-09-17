import subprocess

res = subprocess.run(['javap', '-c', '-p', 'scratch/xyz/bluspring/kilt/injects/client/renderer/entity/ItemRendererInject.class'], capture_output=True, text=True)
lines = res.stdout.split('\n')
for i, l in enumerate(lines):
    if 'kilt$applyCustomCameraTransforms' in l or 'kilt$tryRenderMultipleLayers' in l:
        print('\n'.join(lines[i:i+45]))
