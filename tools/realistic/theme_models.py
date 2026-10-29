"""Distinct, original felt architecture. Two silhouettes per zone/development stage.

Theme colours are authored once in sRGB for both the baker and Android terrain.
Only the appearance changes; density and simulation remain governed by the city.
"""
import json
import math


def render_themes(g):
    palettes = json.loads((g['ROOT']/'visual-source/city-themes.json').read_text())
    f = g['felt_materials']
    box, roof, cyl, sphere, rod = (g[n] for n in ('box','roof','cyl','sphere','rod'))
    tree, window = g['tree'], g['window_wall']

    def building(style, kind, stage, variant, p):
        wall = f.felt('Dyed facade', f.dye(p['walls'][variant]))
        accent = f.felt('Applique accent', f.dye(p['accent']))
        white, slate, dark, glass = (g[n] for n in ('white','slate','dark','glass'))
        if kind == 'ZONE_I':
            g['building'](kind, stage, variant)
            if style == 'GRITTY':
                for x in [-.24,.04]:
                    cyl((x,.04,.37+stage*.08),.11,.35,accent)
                    sphere((x,.04,.55+stage*.08),(.11,.11,.055),white)
                    rod((x,.04,.64),(x,.34,.64),.022,accent)
                for y in [-.28,-.08,.12]:box((.31,y,.17),(.15,.16,.24),wall)
            elif style in ['RURAL','EUROPE','JAPAN']:
                # Small workshops / cooperatives, planted forecourts.
                tree(-.38,.33,.55,variant+1)
            elif style in ['PRIME','FUTURE']:
                box((0,0,.54+stage*.08),(.60,.53,.04),glass)
                for x in [-.2,0,.2]:box((x,0,.569+stage*.08),(.009,.53,.008),white)
            return
        g['base'](mat=g['stone'])
        floors = {
            'PRIME':[3,6,10], 'RURAL':[1,2,3], 'GRITTY':[2,3,5],
            'ANCIENT':[1,2,3], 'FUTURE':[2,4,7], 'EUROPE':[2,3,5], 'JAPAN':[1,2,4]
        }[style][stage-1] + (1 if kind=='ZONE_C' else 0)
        w,d=.68,.62
        h=floors*.145
        box((0,0,h/2+.03),(w,d,h),wall)
        for level in range(floors):
            z=.12+level*.145
            box((0,0,z+.077),(w+.022,d+.022,.014),white)
            for x in [-.21,0,.21]:window(x,d/2+.009,z,.115,.094)
            for y in [-.20,0,.20]:window(w/2+.009,y,z,.108,.094,True)
            if style=='JAPAN':
                for x in [-.30,.30]:box((x,d/2+.016,z),(.018,.024,.14),g['wood'])
            if style in ['RURAL','EUROPE'] and level%2==0:
                for x in [-.21,.21]:
                    box((x,.355,z-.059),(.16,.08,.036),accent)
                    for i in range(3):sphere((x-.05+i*.05,.36,z-.03),(.025,.026,.020),g['foliage'][i],12)
        z=h+.04
        if style=='PRIME':
            box((0,0,z),(.74,.68,.055),slate)
            box((-.06,-.04,z+.10),(.48,.40,.18),accent if variant else white)
            box((-.06,-.04,z+.20),(.51,.43,.028),slate)
            cyl((-.13,-.10,z+.35),.012,.30,white)
            for x in [-.27,.27]:box((x,.326,h/2),(.038,.036,h),accent)
        elif style in ['RURAL','EUROPE','JAPAN']:
            height=.22 if style=='EUROPE' else .18
            roof(0,0,z,.82 if style=='JAPAN' else .76,.74,height,slate)
            if style=='EUROPE':
                for x in [-.19,.19]:
                    box((x,.22,z+.06),(.14,.19,.14),wall)
                    roof(x,.22,z+.13,.18,.22,.065,slate)
                    window(x,.321,z+.065,.087,.083)
            if style=='JAPAN':
                roof(0,.36,.23,.76,.23,.07,slate)
                for x in [-.3,.3]:cyl((x,.40,.25),.028,.07,accent)
            else:box((-.24,-.18,z+.19),(.075,.085,.25),g['brick'])
        elif style=='ANCIENT':
            box((0,0,z),(.74,.68,.035),slate)
            for x in [-.31,0,.31]:box((x,.29,z+.06),(.12,.06,.13),wall)
            if variant:sphere((-.09,-.03,z+.04),(.24,.23,.22),accent)
            else:
                for y in [-.27,.27]:box((0,y,z+.04),(.68,.038,.10),wall)
        elif style=='FUTURE':
            box((0,0,z),(.79,.73,.06),accent,.05)
            sphere((0,0,z+.025),(.29,.27,.20 if variant else .09),glass)
            for x in [-.31,.31]:cyl((x,.30,h/2),.035,h,white)
        else:
            box((0,0,z),(.73,.68,.05),slate)
            for x in [-.21,.16]:cyl((x,-.06,z+.09),.10,.17,accent)
            for x in [-.3,.3]:box((x,.327,h/2),(.047,.034,h),g['brick'])
        box((0,.328,.087),(.105,.030,.15),dark)
        if kind=='ZONE_C':
            box((0,.365,.235),(.59,.15,.035),accent)
            for i in range(6):box((-.245+i*.098,.37,.256),(.045,.145,.008),white)
        tree(-.39,.30,.48 if style=='PRIME' else .60,variant+1)
        if style in ['RURAL','JAPAN','EUROPE']:
            for x in [.17,.29,.40]:
                sphere((x,.39,.06),(.055,.058,.035),g['foliage'][2])
                sphere((x,.39,.09),(.024,.025,.020),accent,12)

    for style,p in palettes.items():
        if style=='STANDARD':continue
        # Utilities use the same dyed yarn and local tree colours as houses.
        saved={n:g[n] for n in ['stone','grass','slate','brick','red','foliage']}
        for key,col in [('stone',p['paving']),('grass',p['grass']),('slate',p['roof']),('brick',p['walls'][0]),('red',p['accent'])]:
            g[key]=f.felt(style+' '+key,f.dye(col))
        g['foliage']=[f.felt(style+' leaf '+str(i),f.dye(c)) for i,c in enumerate(p['leaves'])]
        try:
            for kind in ['ZONE_R','ZONE_C','ZONE_I']:
                for stage in range(1,4):
                    for v in range(2):
                        g['render'](f'{style}_{kind}_{stage}_{v}',lambda k=kind,s=stage,v=v:building(style,k,s,v,p))
            for kind in ['PARK','GROVE','FARM','WATER_TOWER','SCHOOL']:
                g['render'](f'{style}_{kind}',lambda k=kind:g['utility'](k))
        finally:g.update(saved)
