"""Logo do FUTÉBAS: três conceitos desenhados com supersampling (Pillow + numpy).

Uso:
    python tools/gerar_logo.py                 # aplica o conceito padrão (PADRAO) e gera os esboços
    python tools/gerar_logo.py --conceito 2    # aplica outro conceito como ícone/logo do jogo
    python tools/gerar_logo.py --so-esbocos    # só docs/esbocos/logos (não mexe no ícone)

Conceitos:
    1 "Escudo"   escudo moderno: verde profundo com faixas de gramado na diagonal, aro dourado,
                 bola no centro e uma estrela dourada no alto
    2 "Selo"     selo redondo: anel verde com "FUTÉBAS" e "2026" em dourado, disco de gramado
                 com o F condensado em itálico e a bola
    3 "Arranque" monograma em movimento: F itálico pesado feito de faixas de velocidade sobre
                 um bloco inclinado, com a bola saindo pela frente

Saídas (conceito aplicado):
    assets/logo/futebas.ico, assets/logo/icone-<n>.png, assets/logo/logo-1024.png
    jogo/src/main/resources/br/futebas/ui/icone/icone-<n>.png e logo-512.png
Esboços: docs/esbocos/logos/conceito-1.png, -2, -3 (1024 px) e conceitos.png (folha de contato)
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PADRAO = 3  # conceito usado no jogo (trocar aqui ou passar --conceito)

RAIZ = Path(__file__).resolve().parent.parent
SAIDA = RAIZ / "assets" / "logo"
RECURSOS = RAIZ / "jogo" / "src" / "main" / "resources" / "br" / "futebas" / "ui" / "icone"
ESBOCOS = RAIZ / "docs" / "esbocos" / "logos"
FONTE = "C:/Windows/Fonts/bahnschrift.ttf"

VERDE_FUNDO = (7, 46, 30)
VERDE = (12, 92, 56)
VERDE_CLARO = (22, 128, 78)
OURO = (246, 196, 48)
OURO_ESC = (196, 140, 18)
BRANCO = (248, 248, 242)
TINTA = (10, 22, 17)

import os
L = int(os.environ.get("FUTEBAS_LOGO_L", "4096"))  # tela de desenho (supersampling 4x para 1024)


# ------------------------------------------------------------------ utilidades
def fonte(px, peso=700, largura=75):
    f = ImageFont.truetype(FONTE, int(px))
    try:
        f.set_variation_by_axes([peso, largura])
    except Exception:
        pass
    return f


def camada():
    return Image.new("RGBA", (L, L), (0, 0, 0, 0))


def mascara(pontos=None, elipse=None):
    m = Image.new("L", (L, L), 0)
    d = ImageDraw.Draw(m)
    if pontos is not None:
        d.polygon([(float(x), float(y)) for x, y in pontos], fill=255)
    if elipse is not None:
        d.ellipse(elipse, fill=255)
    return m


def cor_solida(cor, alfa=255):
    return Image.new("RGBA", (L, L), cor + (alfa,))


def gradiente_linear(c1, c2, angulo=90, ini=0.0, fim=1.0):
    """Gradiente de c1 a c2 na direção do ângulo (graus, 90 = de cima para baixo)."""
    y, x = np.mgrid[0:L, 0:L].astype(np.float32) / L
    a = math.radians(angulo)
    t = (x - 0.5) * math.cos(a) + (y - 0.5) * math.sin(a) + 0.5
    t = np.clip((t - ini) / max(1e-6, fim - ini), 0, 1)[..., None]
    c = np.array(c1, np.float32) * (1 - t) + np.array(c2, np.float32) * t
    a8 = np.full((L, L, 1), 255, np.float32)
    return Image.fromarray(np.concatenate([c, a8], axis=2).astype(np.uint8), "RGBA")


def gradiente_radial(c1, c2, cx, cy, r):
    y, x = np.mgrid[0:L, 0:L].astype(np.float32)
    t = np.clip(np.hypot(x - cx, y - cy) / r, 0, 1)[..., None]
    c = np.array(c1, np.float32) * (1 - t) + np.array(c2, np.float32) * t
    a8 = np.full((L, L, 1), 255, np.float32)
    return Image.fromarray(np.concatenate([c, a8], axis=2).astype(np.uint8), "RGBA")


def pintar(base, fonte_img, m):
    """Cola fonte_img em base onde a máscara m permite (somando alfa)."""
    base.alpha_composite(Image.composite(fonte_img, Image.new("RGBA", (L, L), (0, 0, 0, 0)), m))


def bezier(p0, p1, p2, p3, n=80):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return out


def encolher(pontos, cx, cy, d):
    """Desloca o contorno para dentro (aproximação por escala em torno do centro)."""
    out = []
    for x, y in pontos:
        vx, vy = x - cx, y - cy
        n = math.hypot(vx, vy) or 1
        out.append((x - vx / n * d, y - vy / n * d))
    return out


def poligono_regular(cx, cy, r, lados, rot=-90):
    return [(cx + r * math.cos(math.radians(rot + i * 360 / lados)),
             cy + r * math.sin(math.radians(rot + i * 360 / lados))) for i in range(lados)]


def sombra(m, raio, deslocamento, alfa):
    s = m.filter(ImageFilter.GaussianBlur(raio))
    s = Image.eval(s, lambda v: int(v * alfa))
    img = Image.new("RGBA", (L, L), (0, 0, 0, 0))
    preto = Image.new("RGBA", (L, L), (0, 0, 0, 255))
    img.paste(preto, (0, 0), s)
    return img.transform(img.size, Image.AFFINE, (1, 0, -deslocamento[0], 0, 1, -deslocamento[1]))


def estrela(cx, cy, r, ri=0.42, rot=-90):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * ri
        a = math.radians(rot + i * 36)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def _bola_faces():
    """Faces da bola (icosaedro truncado): 12 pentágonos e 20 hexágonos, em coordenadas 3D."""
    fi = (1 + 5 ** 0.5) / 2
    ico = []
    for a in (-1, 1):
        for b in (-fi, fi):
            ico += [(0, a, b), (a, b, 0), (b, 0, a)]
    ico = np.array(ico, float)
    arestas = [(i, j) for i in range(12) for j in range(i + 1, 12) if abs(np.linalg.norm(ico[i] - ico[j]) - 2) < 1e-6]
    vs = []
    for i, j in arestas:
        vs.append(ico[i] + (ico[j] - ico[i]) / 3)
        vs.append(ico[i] + 2 * (ico[j] - ico[i]) / 3)
    vs = np.array(vs)
    vs /= np.linalg.norm(vs, axis=1)[:, None]
    def face(centro, k):
        c = centro / np.linalg.norm(centro)
        idx = np.argsort(np.linalg.norm(vs - c, axis=1))[:k]
        pts = vs[idx]
        u = np.cross(c, [0.3, 0.5, 0.8]); u /= np.linalg.norm(u)
        w = np.cross(c, u)
        ang = [math.atan2(np.dot(p - c, w), np.dot(p - c, u)) for p in pts]
        return pts[np.argsort(ang)], c
    faces = [face(v, 5) + (True,) for v in ico]
    tri = set()
    for i in range(12):
        for j in range(12):
            for k in range(12):
                if i < j < k and all(abs(np.linalg.norm(ico[a] - ico[b]) - 2) < 1e-6 for a, b in ((i, j), (j, k), (i, k))):
                    tri.add((i, j, k))
    faces += [face(ico[i] + ico[j] + ico[k], 6) + (False,) for i, j, k in tri]
    return faces


_FACES = None


def bola(img, cx, cy, r, tinta=TINTA, branco=BRANCO, contorno=None, giro=(0.35, -0.5, 0.18)):
    """Bola de gomos de verdade: icosaedro truncado girado e projetado, com luz de cima à esquerda."""
    global _FACES
    if _FACES is None:
        _FACES = _bola_faces()
    if contorno:
        pintar(img, cor_solida(contorno[1]), mascara(elipse=(cx - r - contorno[0], cy - r - contorno[0], cx + r + contorno[0], cy + r + contorno[0])))
    disco = mascara(elipse=(cx - r, cy - r, cx + r, cy + r))
    ax, ay, az = giro
    Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    R = Rz @ Ry @ Rx
    luz = np.array([-0.45, -0.6, 0.66]); luz /= np.linalg.norm(luz)
    desenho = camada()
    d = ImageDraw.Draw(desenho)
    costura = max(2, int(r * 0.028))
    ordem = []
    for pts, c, penta in _FACES:
        pr = pts @ R.T
        cr = R @ c
        if cr[2] <= -0.05:
            continue
        ordem.append((cr[2], pr, cr, penta))
    ordem.sort(key=lambda t: t[0])
    for z, pr, cr, penta in ordem:
        # face levemente esférica: subdivide cada aresta e reprojeta na esfera
        cont = []
        n = len(pr)
        for i in range(n):
            a, b = pr[i], pr[(i + 1) % n]
            for t in np.linspace(0, 1, 8, endpoint=False):
                p = a * (1 - t) + b * t
                p = p / np.linalg.norm(p)
                cont.append((cx + p[0] * r, cy + p[1] * r))
        lam = max(0.0, float(np.dot(cr, luz)))
        k = 0.58 + 0.42 * lam
        if penta:
            cor = tuple(int(min(255, c * (0.75 + 0.6 * lam))) for c in tinta)
        else:
            cor = tuple(int(c * k) for c in branco)
        d.polygon(cont, fill=cor + (255,))
        d.line(cont + [cont[0]], fill=tuple(int(c * 0.9) for c in tinta) + (255,), width=costura, joint="curve")
    img.alpha_composite(Image.composite(desenho, Image.new("RGBA", (L, L), (0, 0, 0, 0)), disco))
    # sombra de borda (volume) e brilho
    y, x = np.mgrid[0:L, 0:L].astype(np.float32)
    dist = np.hypot(x - cx, y - cy) / r
    borda = np.clip((dist - 0.72) / 0.28, 0, 1) ** 2 * 0.45
    sb = Image.new("RGBA", (L, L), (0, 0, 0, 0))
    sb.putalpha(Image.fromarray(np.minimum((borda * 255).astype(np.uint8), np.array(disco))))
    img.alpha_composite(sb)
    db = np.hypot(x - (cx - r * 0.34), y - (cy - r * 0.42)) / (r * 0.42)
    br = np.clip(1 - db, 0, 1) ** 1.6 * 0.38
    bb = Image.new("RGBA", (L, L), (255, 255, 255, 0))
    bb.putalpha(Image.fromarray(np.minimum((br * 255).astype(np.uint8), np.array(disco))))
    img.alpha_composite(bb)


# ------------------------------------------------------------------ conceito 1: escudo
def contorno_escudo(x0, y0, x1, y1):
    w, h = x1 - x0, y1 - y0
    P = lambda u, v: (x0 + u * w, y0 + v * h)
    pts = []
    pts += bezier(P(0.0, 0.10), P(0.22, 0.10), P(0.36, 0.06), P(0.5, 0.0), 40)          # ombro esquerdo sobe ao bico
    pts += bezier(P(0.5, 0.0), P(0.64, 0.06), P(0.78, 0.10), P(1.0, 0.10), 40)[1:]      # desce ao ombro direito
    pts += bezier(P(1.0, 0.10), P(1.0, 0.36), P(1.0, 0.50), P(0.97, 0.60), 40)[1:]      # lateral direita
    pts += bezier(P(0.97, 0.60), P(0.90, 0.82), P(0.70, 0.93), P(0.5, 1.0), 50)[1:]     # vai à ponta
    pts += bezier(P(0.5, 1.0), P(0.30, 0.93), P(0.10, 0.82), P(0.03, 0.60), 50)[1:]
    pts += bezier(P(0.03, 0.60), P(0.0, 0.50), P(0.0, 0.36), P(0.0, 0.10), 40)[1:]
    return pts


def conceito_1(simples=False):
    img = camada()
    x0, y0, x1, y1 = L * 0.13, L * 0.06, L * 0.87, L * 0.95
    cx = (x0 + x1) / 2
    ext = contorno_escudo(x0, y0, x1, y1)
    cy = y0 + (y1 - y0) * 0.5
    m_ext = mascara(ext)
    img.alpha_composite(sombra(m_ext, L * 0.012, (0, L * 0.010), 0.55))
    # aro dourado com degradê
    pintar(img, gradiente_linear((255, 220, 96), OURO_ESC, 100), m_ext)
    aro = L * (0.030 if not simples else 0.045)
    m_meio = mascara(encolher(ext, cx, cy, aro))
    pintar(img, cor_solida(VERDE_FUNDO), m_meio)
    filete = L * 0.012
    m_int = mascara(encolher(ext, cx, cy, aro + filete))
    # campo: verde profundo com faixas de corte na diagonal
    campo = gradiente_linear(VERDE_CLARO, VERDE_FUNDO, 90, 0.05, 1.0)
    if not simples:
        y, x = np.mgrid[0:L, 0:L].astype(np.float32)
        faixa = (((x + y) / (L * 0.085)).astype(int) % 2 == 0)
        arr = np.array(campo).astype(np.float32)
        arr[..., :3][faixa] *= 0.90
        campo = Image.fromarray(arr.astype(np.uint8), "RGBA")
    pintar(img, campo, m_int)
    # luz de cima (refletor)
    luz = gradiente_radial((255, 255, 255), (0, 0, 0), cx, y0 + (y1 - y0) * 0.05, L * 0.55)
    la = np.array(luz)[..., 0].astype(np.float32) / 255.0
    luz_img = Image.new("RGBA", (L, L), (255, 255, 255, 0))
    alfa = (la * 0.22 * 255).astype(np.uint8)
    luz_img.putalpha(Image.fromarray(np.minimum(alfa, np.array(m_int))))
    img.alpha_composite(luz_img)
    if simples:
        bola(img, cx, y0 + (y1 - y0) * 0.50, L * 0.25, contorno=(L * 0.014, TINTA))
        return img
    # faixa com o nome, de lado a lado (só horizontal: nada de cruz)
    fy0, fy1 = y0 + (y1 - y0) * 0.255, y0 + (y1 - y0) * 0.385
    faixa_m = Image.fromarray(np.minimum(np.array(mascara([(0, fy0), (L, fy0), (L, fy1), (0, fy1)])), np.array(m_int)))
    img.alpha_composite(sombra(faixa_m, L * 0.008, (0, L * 0.008), 0.6))
    pintar(img, gradiente_linear((255, 222, 100), OURO_ESC, 90, 0.27, 0.36), faixa_m)
    f = fonte((fy1 - fy0) * 0.86, 700, 75)
    t = camada()
    dd = ImageDraw.Draw(t)
    bb = dd.textbbox((0, 0), "FUTÉBAS", font=f)
    track = (fy1 - fy0) * 0.06
    larg = sum(dd.textbbox((0, 0), ch, font=f)[2] - dd.textbbox((0, 0), ch, font=f)[0] for ch in "FUTÉBAS") + track * 6
    xx = cx - larg / 2
    base_y = (fy0 + fy1) / 2 - (bb[1] + bb[3]) / 2 + (fy1 - fy0) * 0.04
    for ch in "FUTÉBAS":
        cb = dd.textbbox((0, 0), ch, font=f)
        dd.text((xx - cb[0], base_y), ch, font=f, fill=VERDE_FUNDO + (255,))
        xx += cb[2] - cb[0] + track
    img.alpha_composite(t)
    # estrela dourada no alto
    e = estrela(cx, y0 + (y1 - y0) * 0.155, L * 0.050)
    me = mascara(e)
    img.alpha_composite(sombra(me, L * 0.006, (0, L * 0.005), 0.5))
    pintar(img, gradiente_linear((255, 226, 110), OURO_ESC, 90, 0.08, 0.22), me)
    # bola embaixo da faixa
    bola(img, cx, y0 + (y1 - y0) * 0.635, L * 0.19, contorno=(L * 0.012, TINTA))
    return img


# ------------------------------------------------------------------ conceito 2: selo
def texto_em_arco(img, texto, cx, cy, raio, centro_graus, fnt, cor, por_baixo=False, espaco=1.0):
    """Escreve o texto ao longo de um arco (letra a letra)."""
    larguras = [fnt.getbbox(ch)[2] - fnt.getbbox(ch)[0] if ch != " " else fnt.size * 0.28 for ch in texto]
    track = fnt.size * 0.06 * espaco
    total = sum(larguras) + track * (len(texto) - 1)
    ang_total = total / raio
    sentido = -1 if por_baixo else 1
    a = math.radians(centro_graus) - sentido * ang_total / 2
    for ch, w in zip(texto, larguras):
        meio = a + sentido * (w / 2) / raio
        if ch != " ":
            t = Image.new("RGBA", (int(fnt.size * 2), int(fnt.size * 2)), (0, 0, 0, 0))
            dd = ImageDraw.Draw(t)
            bb = dd.textbbox((0, 0), ch, font=fnt)
            dd.text((t.width / 2 - (bb[0] + bb[2]) / 2, t.height / 2 - (bb[1] + bb[3]) / 2), ch, font=fnt, fill=cor + (255,))
            rot = -math.degrees(meio) - 90 if not por_baixo else -math.degrees(meio) + 90
            t = t.rotate(rot, resample=Image.BICUBIC)
            px, py = cx + raio * math.cos(meio), cy + raio * math.sin(meio)
            img.alpha_composite(t, (int(px - t.width / 2), int(py - t.height / 2)))
        a += sentido * (w + track) / raio


def conceito_2(simples=False):
    img = camada()
    c = L / 2
    R = L * 0.46
    m = mascara(elipse=(c - R, c - R, c + R, c + R))
    img.alpha_composite(sombra(m, L * 0.012, (0, L * 0.01), 0.55))
    pintar(img, gradiente_linear((255, 220, 96), OURO_ESC, 100), m)
    R2 = R - L * (0.022 if not simples else 0.035)
    pintar(img, gradiente_linear(VERDE, VERDE_FUNDO, 90), mascara(elipse=(c - R2, c - R2, c + R2, c + R2)))
    Ri = R * (0.66 if not simples else 0.80)
    if not simples:
        f = fonte(L * 0.074, 700, 75)
        texto_em_arco(img, "FUTÉBAS", c, c, (R2 + Ri) / 2 + L * 0.004, -90, f, OURO, espaco=3.0)
        f2 = fonte(L * 0.056, 700, 75)
        texto_em_arco(img, "2026", c, c, (R2 + Ri) / 2 + L * 0.002, 90, f2, OURO, por_baixo=True, espaco=5.0)
        for lado in (-1, 1):
            ang = math.radians(90 + lado * 38)
            rr = (R2 + Ri) / 2
            pintar(img, cor_solida(OURO), mascara(estrela(c + rr * math.cos(ang), c + rr * math.sin(ang), L * 0.022)))
    # anel dourado fino em volta do disco
    pintar(img, cor_solida(OURO), mascara(elipse=(c - Ri - L * 0.012, c - Ri - L * 0.012, c + Ri + L * 0.012, c + Ri + L * 0.012)))
    md = mascara(elipse=(c - Ri, c - Ri, c + Ri, c + Ri))
    # disco de gramado com faixas verticais
    y, x = np.mgrid[0:L, 0:L].astype(np.float32)
    faixa = ((x / (L * 0.07)).astype(int) % 2 == 0)
    campo = np.array(gradiente_linear(VERDE_CLARO, VERDE, 90)).astype(np.float32)
    campo[..., :3][faixa] *= 0.90
    pintar(img, Image.fromarray(campo.astype(np.uint8), "RGBA"), md)
    # F itálico condensado
    f = fonte(Ri * 1.55, 700, 75)
    t = camada()
    dd = ImageDraw.Draw(t)
    bb = dd.textbbox((0, 0), "F", font=f)
    fx = c - (bb[0] + bb[2]) / 2 - Ri * 0.16
    fy = c - (bb[1] + bb[3]) / 2
    dd.text((fx, fy), "F", font=f, fill=BRANCO + (255,))
    t = t.transform(t.size, Image.AFFINE, (1, 0.18, -0.18 * c, 0, 1, 0), resample=Image.BICUBIC)
    sm = t.split()[3]
    img.alpha_composite(sombra(Image.fromarray(np.minimum(np.array(sm), np.array(md))), L * 0.006, (L * 0.006, L * 0.008), 0.5))
    img.alpha_composite(Image.composite(t, Image.new("RGBA", (L, L), (0, 0, 0, 0)), md))
    bola(img, c + Ri * 0.40, c + Ri * 0.38, Ri * 0.30, contorno=(L * 0.010, TINTA))
    return img


# ------------------------------------------------------------------ conceito 3: arranque
def conceito_3(simples=False):
    img = camada()
    inc = 0.22  # inclinação
    # bloco inclinado (paralelogramo de cantos arredondados)
    bx0, by0, bx1, by1 = L * 0.16, L * 0.12, L * 0.84, L * 0.88
    h = by1 - by0
    bloco = [(bx0 + h * inc, by0), (bx1 + h * inc * 0.0, by0), (bx1 - h * inc, by1), (bx0 - h * inc * 0.0, by1)]
    bloco = [(x - h * inc * 0.0, y) for x, y in bloco]
    rr = L * 0.045  # raio dos cantos: polígono recuado + traço largo de juntas redondas
    cxm, cym = sum(x for x, _ in bloco) / 4, sum(y for _, y in bloco) / 4
    dentro = encolher(bloco, cxm, cym, rr * 1.25)
    mb = Image.new("L", (L, L), 0)
    dm = ImageDraw.Draw(mb)
    dm.polygon(dentro, fill=255)
    dm.line(dentro + [dentro[0], dentro[1]], fill=255, width=int(rr * 2), joint="curve")
    for x, y in dentro:
        dm.ellipse((x - rr, y - rr, x + rr, y + rr), fill=255)
    img.alpha_composite(sombra(mb, L * 0.012, (0, L * 0.01), 0.55))
    pintar(img, gradiente_linear(VERDE, VERDE_FUNDO, 60), mb)
    # filete dourado na base do bloco
    # F feito de faixas de velocidade
    F = camada()
    d = ImageDraw.Draw(F)
    gx = L * 0.36  # x do tronco (na altura do meio)
    def barra(xa, xb, ya, yb, cor):
        # paralelogramo com a mesma inclinação do bloco
        def sx(x, y):
            return x + (L / 2 - y) * inc
        d.polygon([(sx(xa, ya), ya), (sx(xb, ya), ya), (sx(xb, yb), yb), (sx(xa, yb), yb)], fill=cor + (255,))
    tronco = L * 0.125
    barra(gx, gx + tronco, L * 0.24, L * 0.76, BRANCO)
    barra(gx, L * 0.70, L * 0.24, L * 0.24 + tronco, BRANCO)
    barra(gx, L * 0.60, L * 0.44, L * 0.44 + tronco * 0.9, BRANCO)
    if not simples:
        # rastros de velocidade (à esquerda das barras)
        for (ya, yb, xa, xb) in [(L * 0.24, L * 0.24 + tronco, L * 0.14, L * 0.33),
                                 (L * 0.44, L * 0.44 + tronco * 0.9, L * 0.19, L * 0.33),
                                 (L * 0.64, L * 0.64 + tronco * 0.5, L * 0.10, L * 0.33)]:
            gr = gradiente_linear((246, 196, 48), (246, 196, 48), 0)
            arr = np.array(gr)
            mm = Image.new("L", (L, L), 0)
            ImageDraw.Draw(mm).polygon([(xa + (L / 2 - ya) * inc, ya), (xb + (L / 2 - ya) * inc, ya), (xb + (L / 2 - yb) * inc, yb), (xa + (L / 2 - yb) * inc, yb)], fill=255)
            y, x = np.mgrid[0:L, 0:L].astype(np.float32)
            fade = np.clip((x - xa) / (xb - xa), 0, 1)
            a = (np.array(mm).astype(np.float32) * fade).astype(np.uint8)
            arr[..., 3] = np.minimum(a, np.array(mb))
            img.alpha_composite(Image.fromarray(arr, "RGBA"))
    fm = F.split()[3]
    img.alpha_composite(sombra(fm, L * 0.006, (L * 0.008, L * 0.010), 0.45))
    img.alpha_composite(F)
    # bola saindo pela frente, embaixo à direita
    bola(img, L * 0.655, L * 0.665, L * 0.105, contorno=(L * 0.012, TINTA))
    return img


CONCEITOS = {1: ("Escudo", conceito_1), 2: ("Selo", conceito_2), 3: ("Arranque", conceito_3)}


def render(n, tam, simples=None):
    """Desenha o conceito n em tam px. Abaixo de 64 px usa a versão simplificada."""
    if simples is None:
        simples = tam <= 48
    img = CONCEITOS[n][1](simples)
    return img.resize((tam, tam), Image.LANCZOS)


def folha_de_contato(imgs):
    W, H = 2400, 1180
    folha = Image.new("RGBA", (W, H), (22, 25, 30, 255))
    d = ImageDraw.Draw(folha)
    ft = fonte(44, 600, 100)
    fp = fonte(22, 400, 100)
    for i, n in enumerate(sorted(imgs)):
        x = 40 + i * 790
        d.rounded_rectangle([x, 40, x + 760, H - 40], radius=18, fill=(30, 34, 40, 255))
        grande = imgs[n][700]
        folha.alpha_composite(grande, (x + 30, 70))
        d.text((x + 30, 790), f"{n}. {CONCEITOS[n][0]}", font=ft, fill=(236, 238, 240, 255))
        xs = x + 30
        for t in (128, 64, 48, 32, 16):
            im = imgs[n][t]
            folha.alpha_composite(im, (xs, 870 + (128 - t)))
            d.text((xs, 1010), f"{t}", font=fp, fill=(150, 156, 166, 255))
            xs += t + 40
        # tamanhos pequenos também sobre fundo claro (barra de tarefas clara)
        d.rounded_rectangle([xs, 870, xs + 150, 998], radius=10, fill=(236, 238, 240, 255))
        folha.alpha_composite(imgs[n][32], (xs + 14, 918))
        folha.alpha_composite(imgs[n][16], (xs + 66, 926))
    return folha


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--conceito", type=int, default=PADRAO, choices=sorted(CONCEITOS))
    ap.add_argument("--so-esbocos", action="store_true")
    ap.add_argument("--sem-esbocos", action="store_true")
    a = ap.parse_args()

    if not a.sem_esbocos:
        ESBOCOS.mkdir(parents=True, exist_ok=True)
        imgs = {}
        for n in CONCEITOS:
            grande = CONCEITOS[n][1](False)
            g1024 = grande.resize((1024, 1024), Image.LANCZOS)
            g1024.save(ESBOCOS / f"conceito-{n}.png")
            imgs[n] = {700: grande.resize((700, 700), Image.LANCZOS)}
            pequeno = CONCEITOS[n][1](True)
            for t in (16, 32, 48):
                imgs[n][t] = pequeno.resize((t, t), Image.LANCZOS)
            for t in (64, 128):
                imgs[n][t] = grande.resize((t, t), Image.LANCZOS)
            print("esboço", n, CONCEITOS[n][0])
        folha_de_contato(imgs).save(ESBOCOS / "conceitos.png")
    if a.so_esbocos:
        return

    SAIDA.mkdir(parents=True, exist_ok=True)
    RECURSOS.mkdir(parents=True, exist_ok=True)
    grande = CONCEITOS[a.conceito][1](False)
    pequeno = CONCEITOS[a.conceito][1](True)
    tamanhos = [16, 24, 32, 48, 64, 128, 256]
    imgs = {t: (pequeno if t <= 48 else grande).resize((t, t), Image.LANCZOS) for t in tamanhos}
    for t, im in imgs.items():
        im.save(SAIDA / f"icone-{t}.png")
        im.save(RECURSOS / f"icone-{t}.png")
    grande.resize((1024, 1024), Image.LANCZOS).save(SAIDA / "logo-1024.png")
    grande.resize((512, 512), Image.LANCZOS).save(RECURSOS / "logo-512.png")
    imgs[256].save(SAIDA / "futebas.ico", sizes=[(t, t) for t in tamanhos], append_images=[imgs[t] for t in tamanhos[:-1]])
    print("aplicado o conceito", a.conceito, CONCEITOS[a.conceito][0])


if __name__ == "__main__":
    main()
