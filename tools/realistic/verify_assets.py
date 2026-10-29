"""Validate source hashes, packaged images, landmark geometry/UVs and embedded photo provenance."""
import hashlib,json,struct,sys,zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
felt='--felt' in sys.argv
records=json.loads((ROOT/'visual-source/sources.json').read_text())
for r in records:
    assert r['license']=='CC0',r['id']
    files=[r] if 'file' in r else r.get('files',[])
    for f in files:
        p=ROOT/f['file'];assert hashlib.sha256(p.read_bytes()).hexdigest()==f['sha256'],p
models=[]
for p in sorted((ROOT/('visual-source/felt-models' if felt else 'visual-source/models')).glob('*.glb')):
    b=p.read_bytes();magic,version,total=struct.unpack_from('<4sII',b)
    assert magic==b'glTF' and version==2 and total==len(b)
    size=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+size])
    primitives=[q for m in g['meshes'] for q in m['primitives']]
    assert all('POSITION' in q['attributes'] and 'TEXCOORD_0' in q['attributes'] for q in primitives),p
    assert all('reference_photo' in n['extras'] for n in g['nodes'] if 'mesh' in n),p
    triangles=sum(g['accessors'][q['indices']]['count']//3 for q in primitives)
    assert triangles>100
    models.append(dict(id=p.stem,triangles=triangles,meshes=len(primitives),bytes=len(b),sha256=hashlib.sha256(b).hexdigest()))
assert len(models)==8
manifest=json.loads((ROOT/'app/src/main/assets/realistic/manifest.json').read_text())
assert len(manifest)==(322 if felt else 113)
assert len({m['id'] for m in manifest})==len(manifest)
for m in manifest:
    assert m.get('finish','photographic')==('felt' if felt else 'photographic')
    p=ROOT/'app/src/main/assets/realistic'/(m['id']+'.webp')
    assert p.read_bytes()[8:12]==b'WEBP'
    assert hashlib.sha256(p.read_bytes()).hexdigest()==m['sha256']
    assert 0<m['anchorX']<m['width'] and 0<m['anchorY']<m['height']
    if felt:
        assert m['generation']=='felt-city-v2',m['id']
        raw=(ROOT/'artifacts/felt/rendered'/(m['id']+'.png')).read_bytes()
        assert raw[:8]==b'\x89PNG\r\n\x1a\n'
        offset=8
        while offset<len(raw):
            size=struct.unpack_from('>I',raw,offset)[0]
            chunk=raw[offset+4:offset+8+size]
            expected=struct.unpack_from('>I',raw,offset+8+size)[0]
            assert zlib.crc32(chunk)&0xffffffff==expected, m['id']
            offset+=12+size
        assert offset==len(raw),m['id']
        if m['id'].startswith(('CAR_','PERSON_')):
            d=int(m['id'].split('_')[2])
            assert m['facing']==['EAST','SOUTH','WEST','NORTH'][d]
            assert m['forwardScreen']==[[2,1],[-2,1],[-2,-1],[2,-1]][d]
report=dict(sources=len(records),packaged_models=len(manifest),landmarks=models)
if felt:
    originals=json.loads((ROOT/'visual-source/felt-sources.json').read_text())
    for f in originals['files']+originals['packagedGround']:
        assert hashlib.sha256((ROOT/f['file']).read_bytes()).hexdigest()==f['sha256'],f['file']
    report['finish']='felt';report['originalMaterialLicense']=originals['license']
p=ROOT/('artifacts/felt/asset-validation.json' if felt else 'artifacts/realistic/asset-validation.json');p.write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
