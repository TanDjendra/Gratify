"""Build a static review site from the same legal Markdown bundled in Gratify."""
from pathlib import Path
from html import escape
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/legal"
OUT = SOURCE / "site"

CSS = """*{box-sizing:border-box}html{color-scheme:dark;scroll-behavior:smooth}body{margin:0;background:#101312;color:#edf1ee;font:17px/1.75 system-ui,sans-serif}a{color:#5ee58e;text-underline-offset:4px}a:hover{color:#a5ffbf}a:focus-visible{outline:3px solid #5ee58e;outline-offset:5px}header,main,footer{max-width:820px;margin:auto;padding:28px 24px}header{display:flex;justify-content:space-between;gap:24px;align-items:center;border-bottom:1px solid #303832}.brand{font-weight:800;font-size:25px;letter-spacing:-1px;color:#edf1ee;text-decoration:none}nav{display:flex;gap:20px;flex-wrap:wrap}h1{font-size:clamp(32px,6vw,48px);line-height:1.15;letter-spacing:-1.5px;margin:32px 0}h2{font-size:24px;line-height:1.3;margin-top:38px;letter-spacing:-.4px}p{margin:18px 0}aside{border-left:4px solid #5ee58e;padding:16px 20px;background:#1a241d;color:#d9e8dd;border-radius:0 12px 12px 0}footer{border-top:1px solid #303832;font-size:14px;color:#b8c2bc}.skip{position:absolute;left:12px;top:-80px;padding:8px;background:#101312}.skip:focus{top:8px}.choices{display:grid;gap:16px;margin:30px 0}.choices a{display:block;padding:22px;border:1px solid #39463e;border-radius:14px;text-decoration:none;font-weight:700}.choices span{display:block;color:#b8c2bc;font-weight:400;font-size:15px;margin-top:4px}@media(max-width:540px){header{align-items:flex-start;flex-direction:column;gap:12px}nav{gap:18px}header,main,footer{padding:24px 20px}}@media print{body{background:white;color:black}header,aside,.skip{display:none}a{color:black}main{max-width:none}}"""


def render_markdown(source):
    blocks = []
    paragraph = []
    def flush():
        if paragraph:
            blocks.append("<p>" + "<br>".join(escape(line) for line in paragraph) + "</p>")
            paragraph.clear()
    for line in source.splitlines():
        if line.startswith("# "):
            flush()
            blocks.append("<h1>" + escape(line[2:]) + "</h1>")
        elif line.startswith("## "):
            flush()
            blocks.append("<h2>" + escape(line[3:]) + "</h2>")
        elif not line.strip():
            flush()
        else:
            paragraph.append(line)
    flush()
    return "\n".join(blocks)


def page(title, body, prefix):
    return f"""<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><meta name="description" content="Draf Terms of Use dan Privacy Policy Gratify untuk peninjauan."><title>{escape(title)} · Gratify</title><style>{CSS}</style></head>
<body><a class="skip" href="#main">Langsung ke dokumen</a><header><a class="brand" href="{prefix}">Gratify<span aria-hidden="true" style="color:#5ee58e">.</span></a><nav aria-label="Dokumen"><a href="{prefix}terms/">Terms</a><a href="{prefix}privacy/">Privacy</a><a href="mailto:supportgratify@gmail.com">Dukungan</a></nav></header>
<main id="main"><aside><strong>Draf untuk peninjauan.</strong> Dokumen ini belum disetujui sebagai kebijakan resmi atau ketentuan yang berlaku. Publikasi halaman ini tidak mengubah status draf.</aside>{body}</main>
<footer>Tan Heradhe Rat Djendra (TanDjendra) · Indonesia<br>Kontak: <a href="mailto:supportgratify@gmail.com">supportgratify@gmail.com</a><br>Halaman ini tidak memakai analytics, cookie aplikasi, formulir, atau layanan font eksternal.</footer></body></html>
"""


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for route, filename, title in (("terms", "terms-of-use.md", "Terms of Use"), ("privacy", "privacy-policy.md", "Privacy Policy")):
        source = (SOURCE / filename).read_bytes()
        target = OUT / route
        target.mkdir(exist_ok=True)
        (target / "index.html").write_text(page(title, render_markdown(source.decode("utf-8")), "../"), encoding="utf-8")
        hashes[filename] = hashlib.sha256(source).hexdigest()
    body = """<h1>Dokumen Gratify</h1><p>Baca penjelasan penggunaan aplikasi dan pengelolaan data. Kedua dokumen masih dalam tahap peninjauan pemilik.</p><div class="choices"><a href="terms/">Terms of Use<span>Aturan penggunaan, akun, konten, dan dukungan.</span></a><a href="privacy/">Privacy Policy<span>Data akun, fitur cloud, integrasi, dan penghapusan.</span></a></div>"""
    (OUT / "index.html").write_text(page("Dokumen", body, "./"), encoding="utf-8")
    (OUT / ".nojekyll").write_text("")
    (OUT / "source-manifest.json").write_text(json.dumps({"status": "DRAFT", "source_sha256": hashes}, indent=2) + "\n")
    print("Built static Terms/Privacy review pages from current legal drafts.")


if __name__ == "__main__":
    main()
