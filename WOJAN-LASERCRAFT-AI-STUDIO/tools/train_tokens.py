#!/usr/bin/env python3
"""train_tokens.py — trening modelu rysującego wzory grawerunkowe jako SEKWENCJĘ TOKENÓW.

Inne podejście niż w train_generator.py (regresja MDN): rysunek zamieniamy na skwantowane
ruchy pióra (16 kierunków × 3 długości + podniesienie pióra + koniec) i uczymy GRU
przewidywać kolejny token. Klasyfikacja nad 50 tokenami uczy się znacznie łatwiej niż
regresja rozkładu ciągłego, więc model szybciej daje czytelne kształty.

Format wyjściowy: modele/ai-generator.bin, magic WOJANAI2:
  nagłówek: 6×i32 (cond=128, hidden, layers, vocab, emb, maxlen) + i32 liczba tensorów
  tensory f32 w kolejności: cond.W[2H,128] cond.b[2H] emb[V,E]
    gru0.ih[3H,E] gru0.hh[3H,H] gru0.bih[3H] gru0.bhh[3H]
    gru1.ih[3H,H] gru1.hh[3H,H] gru1.bih[3H] gru1.bhh[3H] out.W[V,H] out.b[V]
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

COND_DIM = 128
TEXT_BUCKETS = 96
PARAM_BUCKETS = 32
HIDDEN = 256
LAYERS = 2
EMB = 48
MAXLEN = 160
NDIR = 16
LENS = (0.022, 0.05, 0.09)
VOCAB = NDIR * len(LENS) + 2
T_PEN_UP = VOCAB - 2
T_END = VOCAB - 1
STEP = 0.05

PARAM_KEYS = ["category", "style", "product", "detail", "engrave"]


def fnv1a(s: str) -> int:
    h = 2166136261
    for ch in s.encode("utf-8"):
        h ^= ch
        h = (h * 16777619) & 0xFFFFFFFF
    return h


def norm_text(s: str) -> str:
    rep = {"ą": "a", "ć": "c", "ę": "e", "ł": "l", "ń": "n", "ó": "o", "ś": "s", "ź": "z", "ż": "z"}
    return "".join(rep.get(c, c) for c in s.lower())


def cond_vector(rec):
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


def normalize(strokes):
    xs = [p[0] for st in strokes for p in st]
    ys = [p[1] for st in strokes for p in st]
    if not xs:
        return strokes
    cx = (min(xs) + max(xs)) / 2
    cy = (min(ys) + max(ys)) / 2
    ext = max(max(xs) - min(xs), max(ys) - min(ys), 1e-6)
    return [[[(p[0] - cx) / ext + 0.5, (p[1] - cy) / ext + 0.5] for p in st] for st in strokes]


def resample(stroke):
    """Równomierne próbkowanie kreski (krok STEP) — stabilna reprezentacja ruchów."""
    if len(stroke) < 2:
        return [list(stroke[0])] if stroke else []
    out = [list(stroke[0])]
    cur = np.array(stroke[0], dtype=np.float64)
    for p in stroke[1:]:
        p = np.array(p, dtype=np.float64)
        while True:
            d = p - cur
            dist = float(np.hypot(*d))
            if dist < 1e-9:
                break
            if dist < STEP:
                cur = p
                break
            cur = cur + d / dist * STEP
            out.append([float(cur[0]), float(cur[1])])
    return out


def encode(strokes):
    """Rysunek → tokeny (16 kierunków × 3 długości, pen-up, end)."""
    strokes = normalize(strokes)
    toks = []
    px = py = 0.5
    for st in strokes:
        pts = resample(st)
        if len(pts) < 2:
            continue
        for p in pts[1:]:
            dx, dy = p[0] - px, p[1] - py
            px, py = p[0], p[1]
            dist = math.hypot(dx, dy)
            if dist < 1e-9:
                continue
            ang = math.atan2(dy, dx)
            d = int(round((ang + math.pi) / (2 * math.pi) * NDIR)) % NDIR
            li = 0 if dist < 0.03 else (1 if dist < 0.065 else 2)
            toks.append(li * NDIR + d)
            if len(toks) >= MAXLEN - 2:
                break
        toks.append(T_PEN_UP)
        if len(toks) >= MAXLEN - 1:
            break
    toks.append(T_END)
    return toks[:MAXLEN]


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
                seq = encode(r["strokes"])
                if len(seq) < 12:
                    continue
                conds.append(cond_vector(r))
                seqs.append(seq)
    return np.array(conds), seqs


class TokenGRU(nn.Module):
    def __init__(self, hidden, layers, vocab, emb):
        super().__init__()
        self.cond = nn.Linear(COND_DIM, layers * hidden)
        self.emb = nn.Embedding(vocab, emb)
        # wektor warunkujący doklejany do KAŻDEGO kroku (nie tylko do stanu początkowego):
        # bez tego model ignorował prompt i rysował to samo dla każdego opisu
        self.gru = nn.GRU(emb + COND_DIM, hidden, num_layers=layers, batch_first=True)
        self.out = nn.Linear(hidden, vocab)

    def forward(self, toks, c=None, h0=None, cond_vec=None):
        if h0 is None and c is not None:
            h0 = torch.tanh(self.cond(c)).view(-1, toks.shape[0], self.gru.hidden_size).contiguous()
        e = self.emb(toks)
        if cond_vec is not None:
            cv = cond_vec.unsqueeze(1).expand(-1, e.shape[1], -1)
            e = torch.cat([e, cv], dim=-1)
        o, h = self.gru(e, h0)
        return self.out(o), h


def batch(seqs, idx, rng):
    B = len(idx)
    T = max(len(seqs[i]) for i in idx)
    x = np.zeros((B, T), dtype=np.int64)
    y = np.full((B, T), -100, dtype=np.int64)
    for b, i in enumerate(idx):
        s = seqs[i]
        x[b, 0] = T_END          # token startowy
        x[b, 1:len(s)] = s[:-1]
        y[b, :len(s)] = s
    return torch.from_numpy(x), torch.from_numpy(y)


@torch.no_grad()
def sample(model, cond, seed=1, temperature=0.9, top_k=8, maxlen=MAXLEN):
    g = torch.Generator().manual_seed(seed)
    model.eval()
    h = torch.tanh(model.cond(cond.view(1, -1))).view(-1, 1, model.gru.hidden_size).contiguous()
    tok = torch.tensor([[T_END]])
    toks = []
    for _ in range(maxlen):
        out, h = model(tok, h0=h, cond_vec=cond.unsqueeze(0))
        logits = out[0, -1].clone()
        logits[T_PEN_UP] += 0.0
        probs = torch.softmax(logits / temperature, dim=-1)
        if top_k:
            v, _ = torch.topk(probs, top_k)
            probs[probs < v[-1]] = 0
            probs = probs / probs.sum()
        tok = torch.multinomial(probs, 1, generator=g).view(1, 1)
        if int(tok) == T_END:
            break
        toks.append(int(tok))
    return toks


def decode(toks, start=(0.5, 0.5)):
    """Tokeny → kreski (ta sama funkcja w C++)."""
    px, py = start
    strokes, cur = [], []
    for t in toks:
        if t == T_END:
            break
        if t == T_PEN_UP:
            if len(cur) > 1:
                strokes.append(cur)
            cur = []
            continue
        li, d = divmod(t, NDIR)
        ang = (d / NDIR) * 2 * math.pi - math.pi
        ln = LENS[li]
        px += math.cos(ang) * ln
        py += math.sin(ang) * ln
        px = min(1.6, max(-0.6, px))
        py = min(1.6, max(-0.6, py))
        cur.append((px, py))
    if len(cur) > 1:
        strokes.append(cur)
    return strokes


def export(model, out_base, hidden, layers, vocab, emb):
    sd = model.state_dict()
    order = ["cond.weight", "cond.bias", "emb.weight",
             "gru.weight_ih_l0", "gru.weight_hh_l0", "gru.bias_ih_l0", "gru.bias_hh_l0"]
    if layers == 2:
        order += ["gru.weight_ih_l1", "gru.weight_hh_l1", "gru.bias_ih_l1", "gru.bias_hh_l1"]
    order += ["out.weight", "out.bias"]
    arrays, meta = [], []
    for k in order:
        a = sd[k].detach().cpu().numpy().astype(np.float32)
        arrays.append(a)
        meta.append({"name": k, "shape": list(a.shape)})
    with open(out_base + ".bin", "wb") as f:
        f.write(b"WOJANAI2")
        f.write(struct.pack("<iiiiii", COND_DIM, hidden, layers, vocab, emb, MAXLEN))
        f.write(struct.pack("<i", len(arrays)))
        for a in arrays:
            f.write(a.tobytes())
    desc = {"name": "WOJAN LaserCraft AI — generator wzorów (tokenowy GRU)",
            "format": "WOJANAI2", "cond_dim": COND_DIM, "hidden": hidden, "layers": layers,
            "vocab": vocab, "emb": emb, "maxlen": MAXLEN, "dirs": NDIR, "lens": list(LENS),
            "pen_up": T_PEN_UP, "end": T_END, "tensors": meta,
            "params": int(sum(a.size for a in arrays)), "weights_bytes": int(sum(a.size for a in arrays) * 4)}
    with open(out_base + ".json", "w", encoding="utf-8") as f:
        json.dump(desc, f, ensure_ascii=False, indent=1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--shards", default="build/train/all.jsonl")
    ap.add_argument("--out", default="modele/ai-generator")
    ap.add_argument("--hidden", type=int, default=HIDDEN)
    ap.add_argument("--emb", type=int, default=EMB)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    paths = sorted(glob.glob(args.shards))
    conds, seqs = load_data(paths)
    if args.limit:
        conds, seqs = conds[:args.limit], seqs[:args.limit]
    print(f"projektów: {len(seqs)}, średnia długość tokenów: {np.mean([len(s) for s in seqs]):.0f}")
    torch.manual_seed(1234)
    model = TokenGRU(args.hidden, LAYERS, VOCAB, args.emb)
    print("parametrów:", sum(p.numel() for p in model.parameters()))
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=max(1, args.epochs), eta_min=args.lr * 0.1)
    lossf = nn.CrossEntropyLoss(ignore_index=-100)
    N, bs = len(seqs), 64
    idx_all = np.arange(N)
    for ep in range(1, args.epochs + 1):
        model.train()
        rng = np.random.default_rng(ep)
        order = rng.permutation(N)
        tot, cnt, t0 = 0.0, 0, time.time()
        for b in range(0, N, bs):
            idx = order[b:b + bs]
            x, y = batch(seqs, idx, rng)
            cb = torch.from_numpy(conds[idx])
            logits, _ = model(x, cb, cond_vec=cb)
            loss = lossf(logits.reshape(-1, VOCAB), y.reshape(-1))
            opt.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step()
            tot += float(loss)
            cnt += 1
        sched.step()
        toks = sample(model, torch.from_numpy(conds[0]), seed=ep)
        st = decode(toks)
        print(f"epoka {ep}/{args.epochs}  loss {tot/max(cnt,1):.3f}  ({time.time()-t0:.0f}s)  "
              f"próbka: {len(toks)} tokenów, {len(st)} kresek", flush=True)
        if ep % 10 == 0 and ep < args.epochs:
            os.makedirs("build/train/ckpt", exist_ok=True)
            export(model, f"build/train/ckpt/tok-ep{ep:02d}", args.hidden, LAYERS, VOCAB, args.emb)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    export(model, args.out, args.hidden, LAYERS, VOCAB, args.emb)
    print("OK", args.out + ".bin")
    return 0


if __name__ == "__main__":
    sys.exit(main())
