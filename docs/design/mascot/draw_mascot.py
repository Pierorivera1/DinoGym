from PIL import Image
import sys
N=32
C={'O':(0x33,0x21,0x3F),'Y':(0xF9,0xC7,0x5A),'S':(0xE3,0x9E,0x3C),'H':(0xFF,0xE3,0x8F),
   'K':(0x1A,0x10,0x20),'W':(0xFF,0xFF,0xFF),'M':(0xA8,0x5A,0x1E),'A':(0xE0,0x7B,0x16)}
def inside(x,y):
    px,py=x+0.5,y+0.5
    head=((px-16)/7.2)**2+((py-9)/7.0)**2<=1
    body=((px-16)/14.6)**2+((py-21.5)/9.6)**2<=1 and py<=30.5
    return head or body
g=[['.']*N for _ in range(N)]
for y in range(N):
    for x in range(N):
        if inside(x,y): g[y][x]='Y'
def filled(x,y): return 0<=x<N and 0<=y<N and inside(x,y)
for y in range(N):
    for x in range(N):
        if g[y][x]=='Y':
            if not filled(x+1,y+1) or not filled(x+1,y+2) and y>20 or not filled(x,y+2): g[y][x]='S'
            elif not filled(x-1,y-1) and x<16: g[y][x]='H'
for y in range(N):
    for x in range(N):
        if g[y][x]=='.' and any(filled(x+dx,y+dy) for dx,dy in((1,0),(-1,0),(0,1),(0,-1))): g[y][x]='O'
def put(pts,c):
    for x,y in pts: g[y][x]=c
for y in range(10,15):
    for x in range(8,24):
        if g[y][x]=='S' and filled(x,y+2): g[y][x]='Y'
# eyes 4x5 with rounded corners, highlights
for ex in (10,17):
    eye=[(ex+i,5+j) for i in range(5) for j in range(5) if not ((i in(0,4)) and (j in(0,4)))]
    put(eye,'K'); put([(ex+1,6),(ex+2,6),(ex+1,7),(ex+2,7)],'W'); put([(ex+3,8)],'W')
# smirk
put([(14,12),(15,12),(16,12),(17,12),(18,12),(19,11),(20,10),(21,11)],'M')
# arms: left "⊃", right "⊂"
put([(6,18),(7,18),(8,18),(9,19),(9,20),(6,21),(7,21),(8,21)],'A')
put([(23,18),(24,18),(25,18),(22,19),(22,20),(23,21),(24,21),(25,21)],'A')
img=Image.new('RGBA',(N,N),(0,0,0,0))
for y in range(N):
    for x in range(N):
        if g[y][x]!='.': img.putpixel((x,y),C[g[y][x]]+(255,))
out=sys.argv[1]
img.save(out+'/mascot-32.png')
big=img.resize((N*12,N*12),Image.NEAREST); big.save(out+'/mascot-384.png')
# preview on both theme backgrounds
pv=Image.new('RGB',(N*12*2+60,N*12+40),(0xFB,0xF3,0xE8))
pv.paste(Image.new('RGB',(N*12+30,N*12+40),(0x0C,0x09,0x15)),(N*12+30,0))
pv.paste(big,(15,20),big); pv.paste(big,(N*12+45,20),big)
pv.save(out+'/preview.png')
