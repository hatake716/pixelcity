"""Color-aware vertex clustering for distant live models; no external dependencies."""
import gzip,struct,math,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'app/src/main/assets/globe'
manifest=json.loads((root/'manifest.json').read_text())
for name,entry in manifest.items():
    with gzip.open(root/(name+'.meshdata'),'rb') as f:
        count=struct.unpack('>I',f.read(4))[0];values=struct.unpack('>'+str(count*9)+'f',f.read())
    vertices=[values[i:i+9] for i in range(0,len(values),9)]
    best=[]
    for cell in [.04,.065,.09,.13,.18,.25,.34]:
        groups={};keys=[]
        for v in vertices:
            key=tuple(round(a/cell) for a in v[:3])+tuple(round(c*24) for c in v[6:9]);keys.append(key)
            total,n=groups.get(key,([0.,0.,0.],0))
            groups[key]=([total[i]+v[i] for i in range(3)],n+1)
        points={k:tuple(x/n for x in total) for k,(total,n) in groups.items()}
        output=[];seen=set()
        for i in range(0,count,3):
            key=keys[i:i+3]
            if len(set(key))<3:continue
            signature=tuple(sorted(key))
            if signature in seen:continue
            seen.add(signature);a,b,c=[points[k] for k in key]
            e=[b[j]-a[j] for j in range(3)];f=[c[j]-a[j] for j in range(3)]
            n=[e[1]*f[2]-e[2]*f[1],e[2]*f[0]-e[0]*f[2],e[0]*f[1]-e[1]*f[0]]
            length=math.sqrt(sum(x*x for x in n))
            if length<1e-8:continue
            sign=1 if sum(n[j]*vertices[i][j+3] for j in range(3))>=0 else -1
            n=[x/length*sign for x in n]
            for p in [a,b,c]:output.extend((*p,*n,*vertices[i][6:9]))
        best=output
        if len(best)//9<=1800:break
    if not best:best=list(values)
    with gzip.GzipFile(filename=str(root/(name+'.lod.meshdata')),mode='wb',mtime=0) as f:
        f.write(struct.pack('>I',len(best)//9));f.write(struct.pack('>'+str(len(best))+'f',*best))
    entry['lodVertices']=len(best)//9
    entry['bounds']=[min(v[a] for v in vertices) for a in range(3)]+[max(v[a] for v in vertices) for a in range(3)]
(root/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('LOD models',len(manifest),'largest',max(x['lodVertices'] for x in manifest.values()))
