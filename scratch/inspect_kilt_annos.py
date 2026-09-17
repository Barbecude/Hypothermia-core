import zipfile, subprocess

res = subprocess.run(['javap', '-v', '-p', 'scratch/xyz/bluspring/kilt/injects/client/renderer/entity/ItemRendererInject.class'], capture_output=True, text=True)
lines = res.stdout.split('\n')
for i, l in enumerate(lines):
    if 'kilt$' in l:
        print('\n'.join(lines[max(0, i-10):i+25]))
        print('='*50)
