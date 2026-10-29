"""Fetch explicitly selected reusable source files and preserve their attribution/hash.
No generated game assets are downloaded: these photos/materials feed build_assets.py.
"""
from pathlib import Path
import urllib.request, urllib.parse, json, hashlib, re, html, concurrent.futures
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'visual-source'
UA='Pixelcity-AssetBuilder/0.4 (https://github.com/hatake716/pixelcity)'
def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':UA}),timeout=90).read()
def js(url): return json.loads(fetch(url))
def save(url,path):
    path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists(): path.write_bytes(fetch(url))
    return hashlib.sha256(path.read_bytes()).hexdigest()
def plain(s): return html.unescape(re.sub('<[^>]*>','',s))
references={
'TOKYO_TOWER':'Tokyo Tower close up 1.jpg',
'ARC_DE_TRIOMPHE':'Arc de Triomphe (27710590284).jpg',
'COLOSSEUM':'Colosseum romanum.jpg',
'STATUE_OF_LIBERTY':'Front view of Statue of Liberty with pedestal and base 2024.jpg',
'BIG_BEN':'Big Ben in 2025.01.jpg',
'LEANING_TOWER':'Leaning Tower of Pisa, 2023.jpg',
'TAJ_MAHAL':'Taj Mahal 2018.jpg',
'PYRAMID':'Great Pyramid (Pyramid of Cheops Khufu), Giza, GG, EGY (40936205113).jpg'}
records=[]
for ident,title in references.items():
    url='https://commons.wikimedia.org/w/api.php?'+urllib.parse.urlencode(dict(action='query',titles='File:'+title,prop='imageinfo',iiprop='url|extmetadata',format='json'))
    data=js(url); p=next(iter(data['query']['pages'].values())); info=p['imageinfo'][0]; meta=info['extmetadata']
    license=meta['LicenseShortName']['value']
    if license!='CC0': raise RuntimeError((title,license))
    path=OUT/'references'/f'{ident}.jpg'
    sha=save(info['url'],path)
    records.append(dict(id=ident,type='photographic model reference',title=title,source=info['descriptionurl'],download=info['url'],author=plain(meta['Artist']['value']),license=license,license_url=meta['LicenseUrl']['value'],file=str(path.relative_to(ROOT)),sha256=sha,modification='Unmodified reference photo. Proportions, colour and architectural features interpreted in authored 3D geometry; not photogrammetry.'))
    (OUT/'references'/f'{ident}.metadata.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
    print('reference',ident,flush=True)
materials=['aerial_grass_rock','aerial_asphalt_01','red_bricks_04','grey_roof_tiles','white_plaster_rough_02','sandstone_blocks_04','coast_sand_02']
for ident in materials:
    files=js('https://api.polyhaven.com/files/'+ident); info=js('https://api.polyhaven.com/info/'+ident)
    paths=[]
    for channel in ['Diffuse','Rough','nor_gl']:
        sizes=files.get(channel,{})
        if '1k' not in sizes: continue
        formats=sizes['1k']; data=formats.get('jpg',formats.get('png'))
        if not data: continue
        url=data['url']; path=OUT/'textures'/f'{ident}_{channel}.{url.rsplit(".",1)[-1]}'
        sha=save(url,path)
        paths.append(dict(channel=channel,file=str(path.relative_to(ROOT)),download=url,sha256=sha))
    records.append(dict(id=ident,type='PBR material',source='https://polyhaven.com/a/'+ident,author=list(info.get('authors',{})),license='CC0',license_url='https://creativecommons.org/publicdomain/zero/1.0/',files=paths,modification='Used as PBR surface maps in Blender; UV scale and base colour adjusted for miniature city architecture.'))
    print('texture',ident,flush=True)
(OUT/'sources.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
font='https://raw.githubusercontent.com/google/fonts/main/ofl/outfit/Outfit%5Bwght%5D.ttf'
font_path=ROOT/'app/src/main/assets/fonts/Outfit.ttf'
sha=save(font,font_path)
save('https://raw.githubusercontent.com/google/fonts/main/ofl/outfit/OFL.txt',ROOT/'app/src/main/assets/licenses/Outfit-OFL.txt')
(OUT/'font.json').write_text(json.dumps(dict(source=font,sha256=sha,license='SIL OFL 1.1',name='Outfit',modification='Unmodified'),indent=2)+'\n')
print('complete',flush=True)
