#!/usr/bin/env python3
"""train_generator.py — trening dedykowanego modelu generującego wzory grawerunkowe.

Model: warunkowy autoregresyjny GRU z wyjściem MDN (mieszanina Gaussów) + kategoria
stanu pióra — czyli "sketch-RNN" przystosowany do rysunków wektorowych z tego programu.
Dane: build/train/shard*.jsonl wyeksportowane przez tools/export_training.cpp z TEGO
samego silnika (lc::Engine::generate).

Wyjście: modele/ai-generator.bin (wagi float32 + nagłówek) i modele/ai-generator.json (opis)

Użycie: python3 tools/train_generator.py [--epochs N] [--out modele/ai-generator]
"""

import argparse
import glob
import json
import math
import os
import struct
import sys
import time

import numpy as np
import torch
import torch.nn as nn

COND_DIM = 128          # 96 hashy tekstu + 32 hashy parametrów
TEXT_BUCKETS = 96
PARAM_BUCKETS = 32
SCALE = 40.0            # normalizacja współrzędnych (typowe delty ~1)
HIDDEN = 160
N_LAYERS = 2
N_MIX = 10
MAXLEN = 360
NORMALIZE = True        # normalizacja bbox każdego rysunku (stabilne delty)

PARAMS = {
    "category": ["hobby", "rodzina", "praca", "sport", "zwierzeta", "natura", "motoryzacja",
                 "kuchnia", "podroze", "humor"],
    "style": ["rycina", "retro", "minimal", "komiks", "gotyk", "geometryczny"],
    "product": ["tabliczka", "piersiwka", "kubek", "deska", "medal", "brelok", "zakladka", "szyld"],
    "detail": ["niski", "sredni", "wysoki"],
    "engrave": ["line", "fill", "fill+line"],
}
PARAM_KEYS = ["category", "style", "product", "detail", "engrave"]


def fnv1a(s: str) -> int:
    h = 2166136261
    for ch in s.encode("utf-8"):
        h ^= ch
        h = (h * 16777619) & 0xFFFFFFFF
    return h


def norm_text(s: str) -> str:
    s = s.lower()
    rep = {"ą": "a", "ć": "c", "ę": "e", "ł": "l", "ń": "n", "ó": "o", "ś": "s", "ź": "z", "ż": "z"}
    return "".join(rep.get(c, c) for c in s)


def cond_vector(rec):
    """Ta sama funkcja musi istnieć w C++ i Javie (kolejność i wagi identyczne)."""
    v = np.zeros(COND_DIM, dtype=np.float32)
    txt = norm_text(rec.get("prompt", ""))
    toks = [t for t in "".join(c if c.isalnum() else " " for c in txt).split() if t]
    for t in toks:
        v[fnv1a(t) % TEXT_BUCKETS] += 1.0
        for i in range(len(t) - 2):
            v[fnv1a(t[i:i + 3]) % TEXT_BUCKETS] += 0.5
    for key in PARAM_KEYS:
        val = rec.get(key, "")
        if val:
            v[TEXT_BUCKETS + fnv1a(key + "=" + val) % PARAM_BUCKETS] += 2.0
    n = float(np.linalg.norm(v))
    if n > 0:
        v /= n
    return v


def normalize_strokes(strokes):
    """Skaluje rysunek do jednostkowego bbox (jak w sketch-rnn): mniejsze i stabilniejsze delty."""
    xs = [p[0] for st in strokes for p in st]
    ys = [p[1] for st in strokes for p in st]
    if not xs:
        return strokes
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    ext = max(max(xs) - min(xs), max(ys) - min(ys), 1e-6)
    out = []
    for st in strokes:
        out.append([[(p[0] - cx) / ext + 0.5, (p[1] - cy) / ext + 0.5] for p in st])
    return out


def strokes_to_seq(strokes):
    """[[x,y],...] → delty + stan pióra (0 rysuj, 1 koniec kreski, 2 koniec rysunku)."""
    strokes = normalize_strokes(strokes)
    seq = []
    px = py = 0.0
    for si, st in enumerate(strokes):
        for pi, p in enumerate(st):
            x, y = float(p[0]) * SCALE, float(p[1]) * SCALE
            dx, dy = x - px, y - py
            px, py = x, y
            last = (pi == len(st) - 1)
            pen = 1 if last else 0
            seq.append((dx, dy, pen))
            if len(seq) >= MAXLEN - 1:
                break
        if len(seq) >= MAXLEN - 1:
            break
    seq.append((0.0, 0.0, 2))
    a = np.array(seq, dtype=np.float32)
    a[:, 0:2] = np.clip(a[:, 0:2], -8, 8)
    return a


SIMPLE_K = 1


def simplify(strokes, k):
    if k <= 1:
        return strokes
    out = []
    for st in strokes:
        if len(st) <= 3:
            out.append(st)
            continue
        keep = st[::k]
        if keep[-1] != st[-1]:
            keep.append(st[-1])
        out.append(keep)
    return out


def load_data(paths):
    conds, seqs = [], []
    for p in paths:
        with open(p, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                r = json.loads(line)
                if len(r.get("strokes", [])) < 3:
                    continue
                conds.append(cond_vector(r))
                seqs.append(strokes_to_seq(simplify(r["strokes"], SIMPLE_K)))
    return np.array(conds), seqs


class SketchRNN(nn.Module):
    """Warunkowany model: opis projektu wchodzi jako stan początkowy GRU."""

    def __init__(self):
        super().__init__()
        self.cond = nn.Linear(COND_DIM, N_LAYERS * HIDDEN)
        self.gru = nn.GRU(5, HIDDEN, num_layers=N_LAYERS, batch_first=True)
        self.out = nn.Linear(HIDDEN, N_MIX * 5 + 3)

    def forward(self, x, c=None, h0=None):
        if h0 is None and c is not None:
            h0 = torch.tanh(self.cond(c)).view(-1, x.shape[0], HIDDEN).contiguous()
        o, h = self.gru(x, h0)
        return self.out(o), h


def mdn_loss(logits, target, mask):
    """logits [B,T,K*5+3]; target [B,T,3]; mask [B,T]."""
    B, T, _ = target.shape
    K = N_MIX
    means = logits[..., :2 * K].view(B, T, K, 2)
    logstd = torch.clamp(logits[..., 2 * K:4 * K].view(B, T, K, 2), -6, 3)
    pis = torch.log_softmax(logits[..., 4 * K:5 * K], dim=-1)
    pen = torch.log_softmax(logits[..., 5 * K:], dim=-1)
    xy = target[..., :2].unsqueeze(2)                       # [B,T,1,2]
    d = xy - means
    z = d * torch.exp(-logstd)
    gl = -0.5 * (z ** 2) - logstd - 0.5 * math.log(2 * math.pi)   # [B,T,K,2]
    gl = gl.sum(-1) + pis                                    # [B,T,K]
    lp = torch.logsumexp(gl, dim=-1)                         # [B,T]
    lt = lp + pen.gather(-1, target[..., 2:3].long()).squeeze(-1)
    return -(lt * mask).sum() / mask.sum().clamp(min=1)


WINDOW = 0          # 0 = cała sekwencja; >0 = losowe okno (augmentacja)


def window_seq(seq, rng):
    """Losowy fragment rysunku z doklejonym tokenem końca — augmentacja jak w sketch-rnn."""
    n = len(seq)
    if WINDOW <= 0 or n <= WINDOW:
        return seq
    start = int(rng.integers(0, n - WINDOW))
    frag = seq[start:start + WINDOW].copy()
    frag[-1, 2] = 2
    return frag


def build_batch(seqs, idx, rng=None):
    if rng is not None and WINDOW > 0:
        seqs = [window_seq(seqs[i], rng) for i in idx]
        idx = list(range(len(seqs)))
    T = min(MAXLEN, max(len(seqs[i]) for i in idx))
    B = len(idx)
    x = np.zeros((B, T, 5), dtype=np.float32)
    y = np.zeros((B, T, 3), dtype=np.float32)
    m = np.zeros((B, T), dtype=np.float32)
    for b, i in enumerate(idx):
        s = seqs[i][:T]
        n = len(s)
        m[b, :n] = 1
        y[b, :n] = s
        if n > 1:
            x[b, 1:n, :2] = s[:-1, :2]
            for t in range(1, n):
                prev_pen = int(s[t - 1, 2])
                x[b, t, 2:5] = 0
                x[b, t, 2 + prev_pen] = 1
    return torch.from_numpy(x), torch.from_numpy(y), torch.from_numpy(m)


@torch.no_grad()
def sample(model, cond, seed=1, maxlen=MAXLEN, temperature=0.75):
    g = torch.Generator().manual_seed(seed)
    model.eval()
    x = torch.zeros(1, 1, 5)
    h = torch.tanh(model.cond(cond.view(1, -1))).view(N_LAYERS, 1, HIDDEN).contiguous()
    pts, strokes = [], []
    px = py = 0.0
    for t in range(maxlen):
        out, h = model(x, h0=h)
        o = out[0, -1]
        K = N_MIX
        pi = torch.softmax(o[4 * K:5 * K] / temperature, dim=-1)
        k = int(torch.multinomial(pi, 1, generator=g))
        mean = o[: 2 * K].view(K, 2)[k]
        logstd = torch.clamp(o[2 * K:4 * K].view(K, 2)[k], -6, 1)
        z = torch.randn(2, generator=g)
        dx = float(mean[0] + z[0] * torch.exp(logstd[0])) / SCALE
        dy = float(mean[1] + z[1] * torch.exp(logstd[1])) / SCALE
        px += dx
        py += dy
        pts.append((px, py))
        pen = int(torch.multinomial(torch.softmax(o[5 * K:] / temperature, dim=-1), 1, generator=g))
        nx = torch.zeros(1, 1, 5)
        nx[0, 0, 0] = float(mean[0] + z[0] * torch.exp(logstd[0]))
        nx[0, 0, 1] = float(mean[1] + z[1] * torch.exp(logstd[1]))
        nx[0, 0, 2 + min(pen, 2)] = 1
        x = nx
        if pen == 1:
            if len(pts) > 1:
                strokes.append(list(pts))
            pts = []
        elif pen == 2:
            break
    if len(pts) > 1:
        strokes.append(list(pts))
    return strokes


def export_model(model, out_base):
    sd = model.state_dict()
    arrays = []
    meta = []
    order = ["cond.weight", "cond.bias", "gru.weight_ih_l0", "gru.weight_hh_l0", "gru.bias_ih_l0", "gru.bias_hh_l0",
             "gru.weight_ih_l1", "gru.weight_hh_l1", "gru.bias_ih_l1", "gru.bias_hh_l1",
             "out.weight", "out.bias"]
    for k in order:
        a = sd[k].detach().cpu().numpy().astype(np.float32)
        arrays.append(a)
        meta.append({"name": k, "shape": list(a.shape)})
    with open(out_base + ".bin", "wb") as f:
        f.write(b"WOJANAI1")
        f.write(struct.pack("<iiiiii", COND_DIM, HIDDEN, N_LAYERS, N_MIX, 5, MAXLEN))
        f.write(struct.pack("<i", len(arrays)))
        for a in arrays:
            f.write(a.tobytes())
    desc = {
        "name": "WOJAN LaserCraft AI — generator wzorów (GRU-MDN)",
        "format": "WOJANAI1", "cond_dim": COND_DIM, "hidden": HIDDEN, "layers": N_LAYERS,
        "n_mix": N_MIX, "maxlen": MAXLEN, "scale": SCALE,
        "text_buckets": TEXT_BUCKETS, "param_buckets": PARAM_BUCKETS,
        "params": PARAMS, "param_keys": PARAM_KEYS,
        "tensors": meta,
        "weights_bytes": sum(a.size for a in arrays) * 4,
    }
    with open(out_base + ".json", "w", encoding="utf-8") as f:
        json.dump(desc, f, ensure_ascii=False, indent=1)


def main():
    global WINDOW, HIDDEN, SIMPLE_K
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--out", default="modele/ai-generator")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--shards", default="build/train/shard*.jsonl")
    ap.add_argument("--window", type=int, default=0)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--hidden", type=int, default=HIDDEN)
    ap.add_argument("--seed", type=int, default=1234)
    ap.add_argument("--simple", type=int, default=1, help="co który punkt zostawić (1=wszystkie)")
    ap.add_argument("--ckpt-every", type=int, default=0, help="zapis wag co N epok (0=tylko na końcu)")
    args = ap.parse_args()
    WINDOW = args.window
    SIMPLE_K = args.simple
    HIDDEN = args.hidden

    paths = sorted(glob.glob(args.shards))
    if not paths:
        print("brak danych treningowych (build/train/shard*.jsonl)", file=sys.stderr)
        return 1
    conds, seqs = load_data(paths)
    if args.limit:
        conds, seqs = conds[: args.limit], seqs[: args.limit]
    print(f"projektów: {len(seqs)}, średnia długość sekwencji: {np.mean([len(s) for s in seqs]):.0f}")

    torch.manual_seed(args.seed)
    np.random.seed(args.seed)
    rng = np.random.default_rng(args.seed)
    model = SketchRNN()
    print("parametrów:", sum(p.numel() for p in model.parameters()))
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=max(1, args.epochs), eta_min=args.lr * 0.12)
    N = len(seqs)
    bs = 64
    for ep in range(1, args.epochs + 1):
        model.train()
        order = np.random.permutation(N)
        tot, cnt, t0 = 0.0, 0, time.time()
        for b in range(0, N, bs):
            idx = order[b:b + bs]
            x, y, m = build_batch(seqs, idx, rng)
            logits, _ = model(x, torch.from_numpy(conds[idx]))
            loss = mdn_loss(logits, y, m)
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step()
            tot += float(loss)
            cnt += 1
        sched.step()
        if args.ckpt_every and ep % args.ckpt_every == 0 and ep < args.epochs:
            tag = f"build/train/ckpt/ai-ep{ep:02d}"
            os.makedirs("build/train/ckpt", exist_ok=True)
            export_model(model, tag)
            print("  checkpoint:", tag + ".bin", flush=True)
        sample_out = sample(model, torch.from_numpy(conds[0]), seed=ep)
        print(f"epoka {ep}/{args.epochs}  loss {tot/max(cnt,1):.4f}  "
              f"({time.time()-t0:.0f}s)  lr {sched.get_last_lr()[0]:.5f}  próbka: {len(sample_out)} kresek")
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    export_model(model, args.out)
    print("OK", args.out + ".bin")
    return 0


if __name__ == "__main__":
    sys.exit(main())
