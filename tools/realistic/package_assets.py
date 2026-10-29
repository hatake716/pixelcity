"""Package only rendered textures, never source photographs or editable models, into the APK."""
import json, subprocess, hashlib, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
felt='--felt' in sys.argv
source=ROOT/('artifacts/felt/rendered' if felt else 'artifacts/realistic/rendered');out=ROOT/'app/src/main/assets/realistic';out.mkdir(parents=True,exist_ok=True)
finish='felt' if felt else 'photographic'
previous=json.loads((out/'manifest.json').read_text()) if (out/'manifest.json').exists() else []
force=not previous or any(m.get('finish','photographic')!=finish for m in previous)
manifest=[]
def convert(src,dst,scale=None):
    if not force and dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime: return
    cmd=['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(src)]
    if scale:cmd+=['-vf',scale]
    subprocess.run(cmd+['-c:v','libwebp','-quality','93','-compression_level','6',str(dst)],check=True)
def package(meta):
    item=json.loads(meta.read_text());ident=item['id']
    assert item.get('finish','photographic')==finish, (ident,item.get('finish'),finish)
    convert(source/(ident+'.png'),out/(ident+'.webp'))
    item['sha256']=hashlib.sha256((out/(ident+'.webp')).read_bytes()).hexdigest()
    return item
with ThreadPoolExecutor(max_workers=4) as pool:
    manifest=list(pool.map(package,sorted(source.glob('*.json'))))
assert len(manifest)==(322 if felt else 113), len(manifest)
(out/'manifest.json').write_text(json.dumps(manifest,indent=2))
if felt:
    (out/'city-themes.json').write_bytes((ROOT/'visual-source/city-themes.json').read_bytes())
    for name in ['grass','asphalt','sand','water']:
        convert(ROOT/('artifacts/felt/'+name+'.png'),out/('ground_'+name+'.webp'),'scale=512:512')
else:
    for name,texture in [('grass','aerial_grass_rock'),('asphalt','aerial_asphalt_01'),('sand','coast_sand_02')]:
        src=next((ROOT/'visual-source/textures').glob(texture+'_Diffuse.*'));convert(src,out/('ground_'+name+'.webp'),'scale=512:512')
    convert(ROOT/'artifacts/realistic/water.png',out/'ground_water.webp')
sources=json.loads((ROOT/'visual-source/sources.json').read_text())
credits='PIXELCITY — 写真・3D・マテリアルの出典\n\n記念建築は実際の写真を参照して独自制作した3Dモデルです。測量・フォトグラメトリによる復元ではありません。写真に見えない面・細部は推定しています。\n\n'
for item in sources:
    authors=item.get('author','')
    if isinstance(authors,dict): authors=', '.join(authors)
    if isinstance(authors,list): authors=', '.join(authors)
    credits+=f"{item.get('title',item['id'])}\n作者: {authors}\nライセンス: CC0\n出典: {item['source']}\nライセンス全文: {item['license_url']}\n\n"
credits+='独自制作の建物・車・水面: PIXELCITY / MIT\nタイトル書体: Outfit / SIL Open Font License 1.1（改変なし）\n日本語本文: Android標準フォント\n'
if felt:
    credits='PIXELCITY — フェルトの街\n\n現在の建築・車・歩行者・樹木・地面・海は、独自制作の毛羽・布地・縫い目を用いて描画しています。素材と生成コード: PIXELCITY / MIT。実写テクスチャおよび外部の樹木モデルは現在の描画には使用していません。\n\n以下は記念建築の形状の参考写真、および以前の版の制作資料の出典です。\n\n'+credits
    originals=[ROOT/'tools/realistic'/f for f in ['felt_materials.py','felt_ground.py','build_assets.py','theme_models.py']]
    originals += [ROOT/'visual-source/city-themes.json',out/'city-themes.json']
    originals += [ROOT/'artifacts/felt'/f for f in ['wool-fibres.png','grass.png','sand.png','water.png','asphalt.png']]
    (ROOT/'visual-source/felt-sources.json').write_text(json.dumps(dict(
        title='Original wool-felt city finish',author='PIXELCITY',license='MIT',
        source='tools/realistic/felt_materials.py',fibreSeed=716,
        modifications='Original procedural wool fibres, padded edges, embroidery and stuffed trees. No external textures or botanical meshes in this finish.',
        landmarkShapeReferences='visual-source/sources.json',
        files=[dict(file=str(p.relative_to(ROOT)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in originals],
        packagedGround=[dict(file=str(p.relative_to(ROOT)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(out.glob('ground_*.webp'))]
    ),indent=2)+'\n')
(ROOT/'app/src/main/assets/licenses/Realistic-assets.txt').write_text(credits)
print('Packaged',len(manifest),'models;',sum(p.stat().st_size for p in out.iterdir()),'bytes')
