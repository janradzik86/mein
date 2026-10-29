#!/usr/bin/env python3
"""Konwersja kontenera "WhisperSpec" (format TensorFlow/whisper) do pliku GGML
czytanego przez whisper.cpp.

Wejście:  plik model.bin (kontener WhisperSpec, fp16) + tokenizer.json (HF) + vocabulary.txt
Wyjście: model w formacie ggml (magic 0x67676d6c), gotowy dla whisper.cpp / core/stt.cpp

Format kontenera (ustalony empirycznie na pliku 145 MB, 212 tensorów):
  u16 len; nazwa (len-1 znaków + NUL); u8 rank; u32 dims[rank]; u8 dtype;
  dane (prod(dims) * rozmiar(dtype)); u32 (stopka, nieużywana)

  dtype 1 -> 1 B (flagi/liczby całkowite), 2 -> 2 B (int16), 4 -> 2 B (float16)

Nazwy tensorów w kontenerze (przykład):
  decoder/layer_0/self_attention/linear_0/weight   [1536, 512]  (Q|K|V sklejone)
  decoder/layer_0/attention/linear_1/weight        [1024, 512]  (K|V cross-attn)
  encoder/layer_0/ffn/linear_0/weight              [2048, 512]

Nazwy oczekiwane przez whisper.cpp: encoder.blocks.N.attn.query.weight itd.

Użycie:
  python3 tools/convert_whisperspec.py model.bin tokenizer.json out-ggml-f16.bin
"""

import json
import os
import struct
import sys

import numpy as np

GGML_MAGIC = 0x67676D6C

# ---------------------------------------------------------------- parser


def parse_container(path):
    data = open(path, "rb").read()
    n = len(data)

    def name_at(pos):
        if pos + 2 > n:
            return None
        ln = struct.unpack("<H", data[pos:pos + 2])[0]
        if not (3 <= ln <= 90):
            return None
        s = data[pos + 2:pos + 2 + ln]
        if len(s) != ln or s[-1] != 0:
            return None
        if not all(32 <= c < 127 for c in s[:-1]):
            return None
        return s[:-1].decode()

    # nagłówek: u32 (6), u16 len + "WhisperSpec", u32 wersja, u32 liczba wpisów
    assert data[4:6] == b"\x0c\x00" and data[6:17] == b"WhisperSpec", "to nie jest kontener WhisperSpec"
    version, count = struct.unpack("<II", data[18:26])

    tensors = {}
    scalars = {}
    p = 26
    seen = 0
    while p < n and seen < count:
        nm = name_at(p)
        if nm is None:
            raise RuntimeError(f"nieprawidłowa nazwa na pozycji {p}")
        ln = struct.unpack("<H", data[p:p + 2])[0]
        q = p + 2 + ln
        rank = data[q]
        q += 1
        dims = [struct.unpack("<I", data[q + 4 * i:q + 4 * i + 4])[0] for i in range(rank)]
        q += 4 * rank
        dtype = data[q]
        q += 1
        extra = struct.unpack("<I", data[q:q + 4])[0]   # pole pomocnicze (nieużywane)
        q += 4
        nelem = 1
        for d in dims:
            nelem *= d
        size = {1: 1, 2: 2, 4: 2}.get(dtype)
        if size is None:
            raise RuntimeError(f"nieznany dtype {dtype} przy {nm}")
        raw = data[q:q + nelem * size]
        if rank == 0:
            val = int.from_bytes(raw, "little", signed=True) if size > 1 else raw[0]
            scalars[nm] = val
        elif dtype == 4:
            arr = np.frombuffer(raw, dtype=np.float16).reshape(dims).astype(np.float32)
            tensors[nm] = arr
        else:
            arr = np.frombuffer(raw, dtype=np.int16 if size == 2 else np.uint8).reshape(dims)
            tensors[nm] = arr
        p = q + nelem * size
        seen += 1
    return version, scalars, tensors


# ---------------------------------------------------------------- nazwy



QKV_LAYOUT = os.environ.get("QKV_LAYOUT", "block")


def split_qkv(arr, n_parts, n_heads):
    if QKV_LAYOUT == "block":
        parts = np.split(arr, n_parts, axis=0)
    else:
        parts = split_head_major(arr, n_parts, n_heads)
    return [p for p in parts]


def split_head_major(arr, n_parts, n_heads):
    """CTranslate2 skleja QKV w układzie [głowa][część][dim] — nie [część][głowa][dim]."""
    if arr.ndim == 1:
        head_dim = arr.shape[0] // (n_heads * n_parts)
        r = arr.reshape(n_heads, n_parts, head_dim)
        parts = [r[:, i, :].reshape(-1) for i in range(n_parts)]
    else:
        head_dim = arr.shape[0] // (n_heads * n_parts)
        r = arr.reshape(n_heads, n_parts, head_dim, arr.shape[1])
        parts = [r[:, i, :, :].reshape(n_heads * head_dim, arr.shape[1]) for i in range(n_parts)]
    return parts


def ggml_name(nm):
    """mapowanie nazw kontenera na nazwy oczekiwane przez whisper.cpp"""
    parts = nm.split("/")
    kind = parts[0]  # encoder / decoder
    if nm == "decoder/embeddings/weight":
        return "decoder.token_embedding.weight"
    if nm == "decoder/position_encodings/encodings":
        return "decoder.positional_embedding"
    if nm == "encoder/position_encodings/encodings":
        return "encoder.positional_embedding"
    if nm in ("encoder/conv1/weight", "encoder/conv1/bias", "encoder/conv2/weight", "encoder/conv2/bias"):
        return nm.replace("/", ".")
    if nm == "decoder/layer_norm/beta":
        return "decoder.ln.bias"
    if nm == "decoder/layer_norm/gamma":
        return "decoder.ln.weight"
    if nm == "encoder/layer_norm/beta":
        return "encoder.ln_post.bias"
    if nm == "encoder/layer_norm/gamma":
        return "encoder.ln_post.weight"
    # warstwy
    if len(parts) >= 5 and parts[1].startswith("layer_"):
        idx = parts[1].split("_")[1]
        block, rest = parts[2], parts[3:]
        if block == "self_attention":
            sub = "attn"
        elif block == "attention":
            sub = "cross_attn"
        elif block == "ffn":
            sub = None
        else:
            raise RuntimeError(f"nieznany blok: {nm}")
        last = rest[-1]
        tensor = rest[0] if len(rest) > 1 else None
        if sub is None:  # ffn
            if last in ("beta", "gamma"):
                return f"{kind}.blocks.{idx}.mlp_ln.{'bias' if last == 'beta' else 'weight'}"
            if tensor == "linear_0":
                return f"{kind}.blocks.{idx}.mlp.0.{'weight' if last == 'weight' else 'bias'}"
            if tensor == "linear_1":
                return f"{kind}.blocks.{idx}.mlp.2.{'weight' if last == 'weight' else 'bias'}"
        else:
            if last in ("beta", "gamma"):
                return f"{kind}.blocks.{idx}.{sub}_ln.{'bias' if last == 'beta' else 'weight'}"
            if tensor == "linear_0":
                return f"{kind}.blocks.{idx}.{sub}.query.{last}"
            if tensor == "linear_1":
                return f"{kind}.blocks.{idx}.{sub}.out.{last}" if sub == "attn" else f"{kind}.blocks.{idx}.{sub}.kv.{last}"
            if tensor == "linear_2":
                return f"{kind}.blocks.{idx}.{sub}.out.{last}"
    return None


def expand(tensors):
    """rozbija sklejone QKV / KV na osobne tensory wymagane przez whisper.cpp"""
    out = {}
    for nm, arr in tensors.items():
        target = ggml_name(nm)
        if target is None:
            continue
        if target.endswith(".attn.query.weight") and arr.shape[0] == 3 * arr.shape[1]:
            q, k, v = split_qkv(arr, 3, 8)
            base = target[:-len("query.weight")]
            out[base + "query.weight"] = q
            out[base + "key.weight"] = k
            out[base + "value.weight"] = v
        elif target.endswith(".cross_attn.kv.weight") and arr.shape[0] == 2 * arr.shape[1]:
            k, v = split_qkv(arr, 2, 8)
            base = target[:-len("kv.weight")]
            out[base + "key.weight"] = k
            out[base + "value.weight"] = v
        elif target.endswith(".attn.query.bias") and arr.ndim == 1 and arr.shape[0] % 3 == 0:
            q, k, v = split_qkv(arr, 3, 8)
            base = target[:-len("query.bias")]
            out[base + "query.bias"] = q
            out[base + "value.bias"] = v   # whisper.cpp nie używa biasu klucza
        elif target.endswith(".cross_attn.kv.bias") and arr.ndim == 1 and arr.shape[0] % 2 == 0:
            k, v = split_qkv(arr, 2, 8)
            base = target[:-len("kv.bias")]
            out[base + "value.bias"] = v   # bias klucza nieużywany
        else:
            out[target] = arr
    return out


# ---------------------------------------------------------------- voc/tokeny


def bytes_to_unicode():
    bs = list(range(ord("!"), ord("~") + 1)) + list(range(ord("¡"), ord("¬") + 1)) + list(range(ord("®"), ord("ÿ") + 1))
    cs = bs[:]
    nb = 0
    for b in range(2 ** 8):
        if b not in bs:
            bs.append(b)
            cs.append(2 ** 8 + nb)
            nb += 1
    return dict(zip(bs, [chr(c) for c in cs]))


def read_vocab(tokenizer_json, n_tokens=50258):
    tok = json.load(open(tokenizer_json, encoding="utf-8"))
    vocab = dict(tok["model"]["vocab"])
    for t in tok.get("added_tokens", []):
        vocab[t["content"]] = t["id"]
    rev = {i: s for s, i in vocab.items()}
    byte_decoder = {v: k for k, v in bytes_to_unicode().items()}
    out = []
    for i in range(n_tokens):
        s = rev.get(i)
        if s is None:
            raise RuntimeError(f"brak tokenu o id {i}")
        out.append(bytes(byte_decoder[c] for c in s))
    return out


def mel_filters(n_mels=80):
    import librosa
    return librosa.filters.mel(sr=16000, n_fft=400, n_mels=n_mels).astype(np.float32)


# ---------------------------------------------------------------- zapis


def write_ggml(path_out, tensors, tokens, filters, hp):
    fout = open(path_out, "wb")

    def w_i32(v):
        fout.write(struct.pack("<i", v))

    w_i32(GGML_MAGIC)
    w_i32(hp["n_vocab"])
    w_i32(hp["n_audio_ctx"])
    w_i32(hp["n_audio_state"])
    w_i32(hp["n_audio_head"])
    w_i32(hp["n_audio_layer"])
    w_i32(hp["n_text_ctx"])
    w_i32(hp["n_text_state"])
    w_i32(hp["n_text_head"])
    w_i32(hp["n_text_layer"])
    w_i32(hp["n_mels"])
    w_i32(1)  # ftype 1 = float16

    w_i32(filters.shape[0])
    w_i32(filters.shape[1])
    fout.write(filters.astype(np.float32).tobytes())

    w_i32(len(tokens))
    for t in tokens:
        w_i32(len(t))
        fout.write(t)

    for name in sorted(tensors):
        arr = tensors[name]
        one_d = arr.ndim < 2 or name in ("encoder.positional_embedding", "decoder.positional_embedding")
        if name in ("encoder.conv1.bias", "encoder.conv2.bias"):
            arr = arr.reshape(arr.shape[0], 1)
        if one_d or name in ("encoder.conv1.bias", "encoder.conv2.bias",
                             "encoder.positional_embedding", "decoder.positional_embedding"):
            data = arr.astype(np.float32)
            ftype = 0
        else:
            data = arr.astype(np.float16)
            ftype = 1
        name_bytes = name.encode("utf-8")
        fout.write(struct.pack("<iii", data.ndim, len(name_bytes), ftype))
        for i in range(data.ndim):
            w_i32(data.shape[data.ndim - 1 - i])
        fout.write(name_bytes)
        fout.write(data.tobytes())
    fout.close()


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    src, tokenizer, dst = sys.argv[1], sys.argv[2], sys.argv[3]
    version, scalars, tensors = parse_container(src)
    print(f"kontener: wersja {version}, tensorów {len(tensors)}, skalarów {len(scalars)}")
    merged = expand(tensors)
    print(f"tensorów po mapowaniu: {len(merged)}")
    n_vocab = tensors["decoder/embeddings/weight"].shape[0]
    n_state = tensors["decoder/embeddings/weight"].shape[1]
    n_enc = len([k for k in merged if k.startswith("encoder.blocks.") and k.endswith("attn.query.weight")])
    n_dec = len([k for k in merged if k.startswith("decoder.blocks.") and k.endswith(".attn.query.weight") and "cross_attn" not in k])
    hp = dict(n_vocab=n_vocab, n_audio_ctx=1500, n_audio_state=n_state, n_audio_head=8, n_audio_layer=n_enc,
              n_text_ctx=448, n_text_state=n_state, n_text_head=8, n_text_layer=n_dec, n_mels=80)
    print("hparams:", hp)
    tokens = read_vocab(tokenizer)
    filters = mel_filters(80)
    write_ggml(dst, merged, tokens, filters, hp)
    import os
    print(f"OK {dst} {os.path.getsize(dst)} B")


if __name__ == "__main__":
    main()
