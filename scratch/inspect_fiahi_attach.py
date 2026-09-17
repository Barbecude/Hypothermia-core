import subprocess

res = subprocess.run(['javap', '-p', 'scratch/com/hexagram2021/fiahi/register/FIAHIAttachmentTypes.class'], capture_output=True, text=True)
print(res.stdout)
