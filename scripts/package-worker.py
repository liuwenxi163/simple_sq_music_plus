"""Package a pinned, independently licensed LyricFlow worker, no NAS credentials."""
import pathlib, shutil, subprocess, tarfile
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'release/worker';out.mkdir(parents=True,exist_ok=True)
up=root/'release/lyricflow'
if not up.exists():subprocess.run(['git','clone','https://github.com/laoning666/lyricFlow.git',str(up)],check=True)
subprocess.run(['git','-C',str(up),'checkout','--detach','ef2aef4a615f03e66d9942b6f6ec2d061405befa'],check=True)
shutil.copytree(up/'src',out/'src',dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__'))
shutil.copy(up/'LICENSE',out/'LICENSE-LyricFlow')
shutil.copy(root/'lyricflow-worker/server.py',out/'server.py')
shutil.copy(root/'deploy/Worker.Dockerfile',out/'Dockerfile')
subprocess.run(['python','-m','pip','download','--dest',str(out/'wheels'),'--only-binary=:all:','--platform','any','--python-version','313','--implementation','py','--abi','none','httpx==0.28.1','mutagen==1.47.0'],check=True)
with tarfile.open(root/'release/worker.tar.gz','w:gz') as tar:
    for p in out.iterdir():tar.add(p,arcname=p.name)
