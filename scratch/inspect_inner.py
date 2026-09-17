import subprocess

path = r'scratch/com/hexagram2021/fiahi/client/model/FIAHIBakedModel$1.class'
res = subprocess.run(['javap', '-c', '-p', path], capture_output=True, text=True)
print(res.stdout)
