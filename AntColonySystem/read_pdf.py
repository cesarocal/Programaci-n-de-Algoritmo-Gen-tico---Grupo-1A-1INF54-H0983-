import pdfplumber

with pdfplumber.open(r'c:\Users\RODRIGO\Desktop\DP1\avitaillement_v3.pdf') as pdf:
    for i, page in enumerate(pdf.pages):
        text = page.extract_text()
        if text:
            print(f"\n--- PAGE {i+1} ---")
            print(text)
        else:
            print(f"\n--- PAGE {i+1}: (no text) ---")
