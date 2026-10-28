"""Update only this component's immutable release references in an existing GitOps checkout."""
import argparse,hashlib,json,pathlib,subprocess,time
import yaml
p=argparse.ArgumentParser();p.add_argument('root');p.add_argument('role',choices=['backend','web']);p.add_argument('repo');p.add_argument('tag');p.add_argument('release');a=p.parse_args()
root=pathlib.Path(a.root).resolve();release=pathlib.Path(a.release).resolve()
parts=['backend','worker'] if a.role=='backend' else ['web']
def run(*cmd):return subprocess.run(cmd,cwd=root,check=True,capture_output=True,text=True)
for attempt in range(4):
    run('git','fetch','origin','main');run('git','reset','--hard','origin/main')
    manifest=json.loads((root/'gitops.json').read_text());stack=next(s for s in manifest['stacks'] if s['name']=='sqmusic')
    doc=yaml.safe_load((root/stack['file']).read_text())
    builds=stack.setdefault('image_builds',[])
    for part in parts:
        sha=hashlib.sha256((release/(part+'.tar.gz')).read_bytes()).hexdigest()
        service={'backend':'sqmusic_main','worker':'lyricflow','web':'sqmusic_web'}[part]
        if service not in doc['services']:raise RuntimeError('Bootstrap service configuration before enabling automatic releases')
        image='local/sqmusic-'+part+':'+sha[:20]
        doc['services'][service].update(image=image,pull_policy='never')
        builds[:]=[b for b in builds if b.get('component')!=part]
        builds.append({'component':part,'image':image,'sha256':sha,'url':f'https://github.com/{a.repo}/releases/download/{a.tag}/{part}.tar.gz'})
    (root/stack['file']).write_text(yaml.safe_dump(doc,sort_keys=False,allow_unicode=True))
    (root/'gitops.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    run('git','add',stack['file'],'gitops.json')
    if not run('git','diff','--cached','--name-only').stdout.strip():break
    run('git','-c','user.name=SQMusic release','-c','user.email=actions@users.noreply.github.com','commit','-m',f'Update SQMusic {a.role} to {a.tag}')
    try:run('git','push','origin','HEAD:main');break
    except subprocess.CalledProcessError:
        if attempt==3:raise
        time.sleep(2+attempt)
