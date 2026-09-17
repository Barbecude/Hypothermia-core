import subprocess

res = subprocess.run(['javap', '-c', '-p', 'scratch/com/hexagram2021/fiahi/client/model/FIAHIModelBaker.class'], capture_output=True, text=True)
print(res.stdout)
