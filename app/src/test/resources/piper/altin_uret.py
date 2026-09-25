"""TurkishPhonemizer için altın dosyayı üretir: Sözcük'ün pronunciation.py'sinin çıktıları.

Kotlin karşılığı (TurkishPhonemizer.kt) bu çıktılarla harfi harfine aynı olmak zorunda: "Ata" sesi bu Python
koduyla eğitildi. pronunciation.py değişirse bu betik yeniden çalıştırılır:

    C:\\Users\\Ata\\Desktop\\sozcuk\\.venv\\Scripts\\python.exe app\\src\\test\\resources\\piper\\altin_uret.py

Çıktı: altin.jsonl — her satır {"t": metin, "p": ses birimleri} ya da {"s": metin, "c": [cümle parçaları]}
"""
import json
import re
import sys
from pathlib import Path

SOZCUK = Path(r"C:\Users\Ata\Desktop\sozcuk")
BABA = Path(r"C:\Users\Ata\Desktop\BabaKartalVoice")
sys.path.insert(0, str(SOZCUK))
from sozcuk.pronunciation import phonemize, sentences  # noqa: E402

OZEL = [
    # sayılar, tarih, saat, yüzde, sıra, birim
    "0", "7", "10", "11", "100", "101", "999", "1000", "1001", "1250", "2026", "10000", "1.250", "1.000.000",
    "3,5", "3,05", "0,5", "007", "12345678901234567", "1.000.000.000.000.000.000.000", "3,12345678901234567890",
    "21.09.2026", "21/09/2026", "31.13.2026", "10:30", "10:30'da", "09:05", "12:00", "%50", "% 50", "%50'si",
    "%3,5", "3. sayfa", "40. yıl", "4. madde", "1. bölüm", "5 km", "12 TL", "3 kg", "250 ml", "90 km/sa",
    "20 °C", "5 GB", "2026'da", "1990'lı yıllar", "3'e 5", "100'den fazla", "-5",
    # kısaltmalar
    "Dr. Ahmet", "Prof. Dr. Ayşe Yılmaz", "vb. şeyler", "vs.", "bkz. sayfa 3", "T.C. kimlik", "M.Ö. 500",
    "s. 45", "Atatürk Cad. No. 5", "ABC Ltd. Şti.", "THY", "THY'nin", "NATO", "NATO'ya", "TÜBİTAK", "ODTÜ'de",
    "PTT", "TBMM", "AB", "ABD'ye", "IBM", "USB", "PDF", "KDV'li", "TRT", "ASELSAN", "UNESCO",
    # simgeler
    "A & B", "2 + 2 = 4", "ad@yer.com", "5 ₺", "10 $", "20 €", "30 £", "5 × 3", "6 ÷ 2", "§ 5", "# 3", "ve/veya",
    # tırnaklar, tireler, üç nokta
    "“Merhaba” dedi.", "‘Evet’ diye yanıtladı…", "Bir – iki — üç", "Ali'nin kitabı", "O’nun evi",
    # ses kuralları
    "değil", "dağ", "ağaç", "ağa", "uğur", "çiğdem", "eğlence", "yağmur", "oğlum", "sığınak", "öğretmen",
    "düğün", "anne", "attı", "belli", "seyretti", "kâr", "kar", "lâle", "hâlâ", "hala", "rol", "gol", "alkol",
    "futbol", "kontrol", "kalp", "hayal", "ilan", "kabul", "rüzgâr", "dükkân", "Kâzım", "hükûmet", "millî",
    "ğ", "ğa", "âlem", "ken", "gelirken", "yürürken", "iken", "Kartal", "Kartal'a", "Kartal'ın", "Kartallar",
    "Beşiktaş", "Beşiktaş'ın", "qwerty", "xenon", "Washington", "brr", "hmm", "pst", "sss",
    # vurgu
    "gelmiyor", "geliyor", "gelmedi", "yapmadan", "gelmez", "sinemadan", "okuduğum", "anlatacağım",
    "alabilirsiniz", "gelmeli", "gelmelisin", "öğretmendir", "kaldır", "indir", "gelince", "Türkçe", "Almanca",
    "bence", "düşünce", "karınca", "arabayla", "evlerle", "yemekle", "gelirse", "okursa", "söylese", "Ankara'da",
    "İstanbul'dan", "şimdiden", "önceden", "annesi", "babalar", "gerçekten", "ve", "de", "mi", "ile",
    "arabalarımızdan", "kitaplarınızı", "gidiyorsunuz", "olmayacak", "yapmamalısınız",
    # büyük harf, İ/I, noktalama
    "IŞIK", "İSTANBUL", "ILIK", "Işık ılık", "Ne? Nasıl! Evet; hayır: belki (belki de) \"tabii\" - tamam.",
    "- Gel buraya!", "Kitap - defter", "", "   ", "...", "Süper!!!", "Café crème", "naïve",
]


def main():
    here = Path(__file__).parent
    metinler = list(OZEL)
    metin_csv = BABA / "kaggle-veri" / "metin.csv"
    if metin_csv.exists():
        metinler += [line.split("|", 1)[1] for line in metin_csv.read_text(encoding="utf-8").splitlines() if "|" in line]
    yardim = (SOZCUK / "sozcuk" / "yardim.md").read_text(encoding="utf-8")
    satirlar = [s.strip() for s in yardim.splitlines() if s.strip() and not s.startswith("<!--")]
    metinler += satirlar
    # sözcük listesi: tüm metinlerdeki benzersiz sözcükler (tek tek)
    sozcukler = sorted({w for m in metinler for w in re.findall(r"[A-Za-zÇĞİÖŞÜçğıöşüâîûÂÎÛ']+", m)})
    metinler += sozcukler

    with open(here / "altin.jsonl", "w", encoding="utf-8", newline="\n") as out:
        gorulen = set()
        for metin in metinler:
            if metin in gorulen:
                continue
            gorulen.add(metin)
            out.write(json.dumps({"t": metin, "p": phonemize(metin)}, ensure_ascii=False) + "\n")
        # cümle bölme: yardım metni paragrafları ve uzun birleşik metin (BMP dışı karakter içermeyenler)
        bolme = satirlar + ["\n".join(satirlar[i:i + 12]) for i in range(0, len(satirlar), 12)]
        bolme += ["Dr. Ali geldi. Prof. Veli de. A. Yılmaz yazdı! Sonra? \"Evet.\" dedi. 3. madde Önemli.",
                  "Bu " + "çok uzun, " * 40 + "bir cümle.", "Boşluksuz" * 40, "Satır bir.\u2028Satır iki.\u2029Üç."]
        for metin in bolme:
            if any(ord(c) > 0xFFFF for c in metin):
                continue
            out.write(json.dumps({"s": metin, "c": [metin[a:b] for a, b in sentences(metin)]},
                                 ensure_ascii=False) + "\n")
    print(len(gorulen), "metin")


if __name__ == "__main__":
    main()
