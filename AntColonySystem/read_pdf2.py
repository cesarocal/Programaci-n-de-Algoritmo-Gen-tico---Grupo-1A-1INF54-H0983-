import fitz  # pymupdf
import sys

path = r'c:\Users\RODRIGO\Desktop\DP1\avitaillement_v3.pdf'
doc = fitz.open(path)
print(f"Total pages: {len(doc)}")
all_text = []
for i, page in enumerate(doc):
    t = page.get_text()
    if t.strip():
        all_text.append(f"\n=== PAGE {i+1} ===\n{t}")
    else:
        all_text.append(f"\n=== PAGE {i+1}: (no text layer) ===")

full = "\n".join(all_text)
with open('pdf_mupdf.txt', 'w', encoding='utf-8') as f:
    f.write(full)
print(f"Written {len(full)} chars to pdf_mupdf.txt")
