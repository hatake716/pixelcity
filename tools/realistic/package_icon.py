"""Package original wool-felt launcher layers and preview Android's 108/72 crop.

Only deterministic resizing, alpha conversion and adaptive-mask previews are
performed here. The artwork itself is rendered by build_icon.py in Blender.
"""
import base64,hashlib,json,subprocess,math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'artifacts/felt-icon'
RES=ROOT/'app/src/main/res/drawable-nodpi';RES.mkdir(parents=True,exist_ok=True)
DOC=ROOT/'docs/images';DOC.mkdir(parents=True,exist_ok=True)

def ffmpeg(src,dst,filters):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(src),'-vf',filters,'-frames:v','1',str(dst)],check=True)

fg=RES/'launcher_felt_foreground.png'
bg=RES/'launcher_felt_background.png'
mono=RES/'launcher_felt_monochrome.png'
ffmpeg(SOURCE/'foreground.png',fg,'scale=648:648:flags=lanczos')
ffmpeg(SOURCE/'background.png',bg,'scale=432:432:flags=lanczos,format=rgb24')
# Themed launchers tint alpha, so retain surface shading as alpha detail.
ffmpeg(fg,mono,"format=rgba,geq=r='255':g='255':b='255':a='alpha(X,Y)*min(1,0.20+1.05*(r(X,Y)*0.2126+g(X,Y)*0.7152+b(X,Y)*0.0722)/255)',format=rgba")

# Check the actually packaged alpha, not just the model's estimated bounds.
raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(fg),'-f','rawvideo','-pix_fmt','rgba','-'])
assert len(raw)==648*648*4
points=[(i%648,i//648) for i in range(648*648) if raw[i*4+3]>12]
radius=max(math.hypot(x-324,y-324) for x,y in points)
assert radius<648*33/108, ('outside adaptive safe circle',radius)

def data(p):return 'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()
fg_url,bg_url,mono_url=map(data,[fg,bg,mono])

def artwork(mask,mono=False):
    # Android expands each layer by 25% on all sides before applying the mask.
    background=f'<rect width="512" height="512" fill="#dbe8d6"/>' if mono else f'<image href="{bg_url}" x="-128" y="-128" width="768" height="768"/>'
    foreground=f'<image href="{mono_url if mono else fg_url}" x="-128" y="-128" width="768" height="768"/>'
    if mono:foreground=f'<g filter="url(#tint)">{foreground}</g>'
    return f'<g clip-path="url(#{mask})">{background}{foreground}</g>'

defs='''<defs>
  <clipPath id="circle"><circle cx="256" cy="256" r="256"/></clipPath>
  <clipPath id="rounded"><rect width="512" height="512" rx="112"/></clipPath>
  <clipPath id="square"><rect width="512" height="512"/></clipPath>
  <filter id="tint" color-interpolation-filters="sRGB"><feColorMatrix type="matrix" values="0 0 0 0 .20 0 0 0 0 .30 0 0 0 0 .25 0 0 0 1 0"/></filter>
</defs>'''
def render_svg(svg,path):
    temp=SOURCE/(path.stem+'.svg');temp.write_text(svg)
    subprocess.run(['nix','shell','nixpkgs#librsvg','--command','rsvg-convert','-o',str(path),str(temp)],check=True)

for mask in ['circle','rounded','square']:
    render_svg(f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512">{defs}{artwork(mask)}</svg>',DOC/f'felt-icon-{mask}.png')

sheet=f'<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="672">{defs}<rect width="1440" height="672" fill="#f7f0df"/>'
for i,(label,mask,monochrome) in enumerate([('丸形','circle',False),('角丸','rounded',False),('テーマアイコン','circle',True)]):
    x=36+i*480
    sheet+=f'<g transform="translate({x} 55) scale(.80)">{artwork(mask,monochrome)}</g>'
    sheet+=f'<text x="{x+205}" y="515" text-anchor="middle" font-family="sans-serif" font-size="27" fill="#314c44">{label}</text>'
    for j,size in enumerate([48,64,96]):
        sheet+=f'<g transform="translate({x+75+j*100} 550) scale({size/512})">{artwork(mask,monochrome)}</g>'
sheet+='</svg>'
render_svg(sheet,DOC/'felt-icon-preview.png')

source_dir=ROOT/'visual-source/icon';source_dir.mkdir(parents=True,exist_ok=True)
files=[Path(__file__),Path(__file__).with_name('build_icon.py'),Path(__file__).with_name('felt_materials.py'),fg,bg,mono]
report=dict(title='PIXELCITY original felt launcher icon',author='PIXELCITY',license='MIT',
    method='Original Blender geometry with the same wool shader as the city; preserves the three-building motif. No external image or model.',
    androidForegroundSize=648,androidBackgroundSize=432,alphaRadiusPixels=radius,safeRadiusPixels=198,
    monochrome='Surface luminance encoded as alpha for the launcher theme tint.',
    files=[dict(file=str(p.relative_to(ROOT)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in files])
(source_dir/'sources.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
