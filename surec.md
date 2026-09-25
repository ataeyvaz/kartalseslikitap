# KartalSesliKitap — Süreç Günlüğü

Yapılan işler, alınan kararlar ve bilinen sorunlar tarih sırasıyla. Yeni girişler en alta eklenir.

Format:
```
## [Tarih] — Başlık
- Ne yapıldı
- Ne değişti / neden
- Test
- Bilinen sorunlar / sonraki adım
```

---

## [2026-07-31 – 2026-08-01] — Başlangıç (git geçmişinden özet)
- `607108f` Sesli kitap uygulaması: çek → OCR → oku akışı ve hibrit sağlayıcı mimarisi (ML Kit, Google Cloud
  Vision, Azure, AWS Textract OCR; Android TTS, Google Cloud TTS, ElevenLabs TTS; API anahtarları Keystore'da)
- `f35150b` Ses sabitleme: kullanıcının seçtiği ses otomatik seçimin önüne geçer
- `66f104b` Sesli okumada teşhis: hangi sesin okuduğu ve neden başarısız olduğu gösterilir

## [2026-09-24] — Piper sesi ("Ata (erkek)") entegrasyonu başladı
- Kullanıcı isteği: Sözcük masaüstü uygulamasında kullanılan, kendi sesiyle eğitilmiş Piper modelini bu uygulamaya
  da eklemek. Tamamen cihazda, internetsiz çalışacak yeni bir TTS sağlayıcısı olarak
- Plan:
  1. `pronunciation.py` (Sözcük, ~550 satır) → Kotlin `TurkishPhonemizer` (normalize, word_phonemes, phonemize,
     phoneme_ids, sentences)
  2. Python'dan altın çıktı dosyası üretip Kotlin çıktısını birim testiyle harfi harfine karşılaştırmak
  3. `onnxruntime-android` 1.30.0 + model `assets/sesler/`
  4. `PiperTtsProvider` (`TtsProvider` + `DirectSpeechTtsProvider`): cümle cümle sentez, bir sonraki cümleyi
     arka planda hazırlama, AudioTrack ile çalma; hız ayarı `length_scale`'e bağlanır (pitch desteklenmez)
  5. `ProviderModule`'e kayıt, ses eşleyici; `TtsProviderRegistry.defaultOnDevice()` birden çok cihaz üstü
     sağlayıcı olunca Android TTS'i tercih edecek şekilde netleştirilecek
- Ortam: Android Studio JBR, SDK kurulu. Mevcut birim testleri bu makinede çalıştırılıyor (temel çizgi)
- Kullanıcı isteğiyle bu dosya ve `CLAUDE.md` oluşturuldu

## [2026-09-24] — Piper sesi: kod yazıldı, telaffuz Python ile birebir doğrulandı
- **`data/provider/piper/TurkishPhonemizer.kt`:** `pronunciation.py`'nin birebir karşılığı (metni okunacak
  biçime getirme, sözcük → ses birimi, vurgu, Piper kimlik kodlaması, cümle bölme). Korunan Python davranışları:
  Unicode regex (`(?U)`), `"" in "ei"` doğruluğu (sözcük başındaki ğ/â/ken kuralları), Türkçe küçük harf,
  Python'un `isspace`/`split`/`strip` tanımı, büyük sayılar için `BigInteger`, görünmez karakterler kaçış dizisiyle
- **Altın test** (`src/test/resources/piper/altin.jsonl`, üretici `altin_uret.py`): eğitimdeki 300 cümle, Sözcük
  yardım metninin tamamı, tüm benzersiz sözcükler ve zorlayıcı örnekler (tarih, saat, yüzde, sıra sayısı, birimler,
  kısaltmalar, simgeler, ğ/â/çift ünsüz, vurgu ekleri) → **4.148 metin + 552 cümle bölme örneği: sıfır fark**
  (`TurkishPhonemizerGoldenTest`)
- **`PiperVoice`:** model `assets/sesler/ata.onnx` ilk kullanımda `noBackupFilesDir/sesler/`'e bir kez kopyalanır
  (boyut değişince yeniden), onnxruntime-android 1.30.0 ile açılır; ayarlar json'dan (`length_scale 1.25`)
- **`PiperTtsProvider`** (`piper_ata`, "Ata'nın sesi (cihaz üzerinde)"): `speak()` cümle cümle sentezler, bir
  sonraki cümleyi arka planda hazırlar (kanal kapasitesi 1), AudioTrack akış kipinde çalar, iptal/`stop()` ile
  ~0,1 sn içinde susar; `synthesize()` tüm metni tek WAV'a yazar. Hız → `length_scale / hız`; perde desteklenmez
- **`PiperVoiceMappingResolver`:** tek ses "Ata (erkek)"; kadın sesi eğitilince cinsiyete göre seçim eklenecek
- `ProviderModule`'e kayıt; `TtsProviderRegistry.defaultOnDevice()` artık açıkça Android TTS'i tercih ediyor
  (iki cihaz üstü sağlayıcı varken küme sırası belirsizdi). Varsayılan sağlayıcı değişmedi: Ata'nın sesi
  Ayarlar'dan seçilir
- `build.gradle.kts`: `onnxruntime-android`, `androidResources.noCompress += "onnx"`
- `assets/sesler/NOTICE.txt`: lisans notu (CC BY-NC-SA 4.0, ticari kullanıma kapalı)
- Gözden geçirmede düzeltilen: `stop()` yalnız AudioTrack'i duraklatıyordu; iptal edilmeden çağrılırsa engelleyen
  `write()` duraklatılmış kanalda sonsuza dek bekleyebilirdi → yazma beklemesiz (`WRITE_NON_BLOCKING` + kısa
  bekleme), `stop()` okuma işini de iptal ediyor
- Derleme: `assembleDebug` başarılı. arm64 APK 153 MB (debug; içinde `libonnxruntime.so` 33 MB + model 63 MB,
  model APK'da sıkıştırılmadan duruyor — `openFd` için şart). Mevcut birim testleri + altın test geçiyor
- **Henüz telefonda denenmedi.** Kullanıcı APK'yı kendisi kuracak (`app-arm64-v8a-debug.apk`). Denenecekler:
  Ayarlar'da "Ata'nın sesi" seçimi, sesi dene, sayfa okuma, durdurma, hız, ilk açılışta model kopyalama süresi

## [2026-09-25] — Telefondaki çökme için düzeltme denemesi; yalnız Ata'nın sesi görünür
- **Telefon testi (24 Eylül sonrası):** uygulama açılıyor, seslendirmede ya kapanıyor ya da uzun bir hata metni
  çıkıyor. Tahmin edilen sebep: `TurkishPhonemizer`'daki desenlere eklenen `(?U)` bayrağı. Android düzenli
  ifadeleri ICU ile çalıştırır; orada `\w \b \d \s` zaten Unicode'dur ve bu bayrak geçersiz sayılabilir.
  **Doğrulanmadı** (logcat okunmadı)
- **Düzeltme:** `uRegex()` yardımcısı — `(?U)` yalnız Android dışında (bilgisayardaki birim testleri) eklenir.
  Android'de olup olmadığını `java.vm.vendor` / `java.vm.name` değerleri söyler. Altın test: sıfır fark (3/3)
- **Kullanıcı isteği: diğer sağlayıcılar şimdilik pasif.** `ProviderModule`'de Google Cloud Vision, Azure,
  AWS Textract (OCR), Android TTS, Google Cloud TTS, ElevenLabs ve bunların ses eşleyicilerinin kayıt satırları
  yorum satırına alındı; kodları yerinde. Kalanlar: ML Kit (OCR, cihazda) ve Ata'nın sesi (TTS)
  - Varsayılan TTS artık `piper_ata`. Telefonda eski ayar `android_tts` kayıtlı kalsa da kayıt defteri cihaz
    üstü varsayılana (Ata'nın sesi) düşer; Ayarlar ekranı da gerçekte kullanılan sağlayıcıyı seçili gösterir
  - Ayarlar'da "Sağlayıcı hesapları" (kimlik isteyen sağlayıcı yoksa) ve "Otomatik bulut geçişi" (bulut OCR
    yoksa) bölümleri gizlenir; bulut sesleri açıklaması da yalnız bulut TTS varken çıkar
  - Yeniden açmak: `ProviderModule`'de ilgili satırları ve importları geri açmak yeter
- **Yan etki:** Android TTS kapalı olduğundan Ata'nın sesi çalışmazsa yedek ses yok; okuma hatayla biter
- Test: tüm birim testleri (97) geçti, `assembleDebug` başarılı (arm64 APK 153 MB)
- Sonraki adım: telefona kurup denemek; hata sürerse `adb logcat` ile gerçek hata okunmalı

## [2026-09-25] — Telefon testi: uygulama çalışıyor, ses kalitesinin sınırı modelde
- **Telefonda (Xiaomi 23113RKC6G, arm64) çalıştı:** çökme yok, `libonnxruntime.so` yükleniyor, Ata'nın sesi
  22050 Hz'de çalıyor. `(?U)` düzeltmesi işe yaradı (önceki çökmenin sebebi logcat'le doğrulanmadı ama düzeltmeden
  sonra hata yok)
- **Kullanıcı gözlemi:** ses ince (çocuk sesi gibi) ve "Türk gibi okumuyor"
- **İnceleme — uygulama suçsuz:**
  - Telefonda altın test (`androidTest/.../TurkishPhonemizerDeviceGoldenTest`, altın dosya androidTest varlıklarına
    `src/test/resources`'tan gelir): **4.148 metin + 552 cümle bölme, sıfır fark** — ICU ve Türkçe cihaz dili
    çıktıyı değiştirmiyor
  - Model dosyası Sözcük'teki ile aynı (MD5), sentez parametreleri, cümle bölme ve 0,18 sn duraklama aynı
  - Aynı paragraf telefonda (`androidTest/.../PiperOrnekSesUret`, araç) ve bilgisayarda (Sözcük kodu) üretildi:
    süre örnek örnek aynı (38,9 sn), perde medyanı ikisinde de 111 Hz (tipik erkek sesi), tını merkezi 2015/2008 Hz.
    Kullanıcı iki dosyayı telefonda dinledi: "ikisi de aynı"
- **Asıl sebep:** eğitim verisi yalnız **300 cümle ≈ 30 dakika** (`BabaKartalVoice/dataset`). Temel model
  (dfki) üstüne bu kadar veriyle ince ayar, sesin tınısını ve söyleyişini tam öğretmiyor. Aynı veriyle daha uzun
  eğitim ezberlemeye yol açar; asıl kazanç daha çok kayıt (hedef 1,5–2 saat) + `last.ckpt`'tan devam eğitimi
- **Dikkat:** `connectedDebugAndroidTest` bittiğinde uygulamayı telefondan **kaldırır** (veriler dahil). Tek
  sınama için `assembleDebugAndroidTest` + `adb install` + `adb shell am instrument` daha güvenli
- Sonraki adım: kullanıcı kayıt sayısını artıracak; beklerken `length_scale`/`noise_scale`/`noise_w`
  denemeleriyle çıkış ayarı iyileştirilebilir

## [2026-09-25] — Çıkış ayarı "sakin" seçildi; "ğ" cümleleri kayıt listesine eklendi
- **Ayar denemesi:** aynı paragraf dört ayarla üretilip kullanıcıya dinletildi (A mevcut 1.25/0.667/0.8,
  B normal hız, C "sakin" 1.1/0.5/0.6, D "canlı" 1.1/0.8/1.0). Kullanıcı **C**'yi seçti
  - `ata.onnx.json` → `length_scale 1.1`, `noise_scale 0.5`, `noise_w 0.6`; üç kopya aynı tutuldu: bu depo,
    Sözcük deposu (`sozcuk/sesler/`), Sözcük'ün kurulu kopyası (`%LOCALAPPDATA%\Sözcük\sesler\`)
  - Sözcük `araclar/ses_egitimi/egitim.py` yeni modelde bu değerleri yazacak şekilde güncellendi (eskiden 1.25
    yazıyordu, bir sonraki eğitim ayarı geri alırdı); `BENIOKU.md` ve `CLAUDE.md` de
  - Bulgu: `noise_w` bu modelde süreleri hiç değiştirmiyor (0 ile 1 arası aynı süre, aynı boşluklar)
- **Kullanıcı gözlemleri:**
  - "topladı" gibi duyulan yer metinde "yamaçtaki otlağa" (ses birimi `ɔtɫˈaː` doğru) → model "ğ"li
    kelimeleri bulanık söylüyor; eğitim verisinde az
  - Kelime arasında virgül varmış gibi bekleme: ölçümde uzun boşlukların neredeyse hepsi virgülde (0,5–0,9 sn),
    noktalamasız yalnız bir tane (~0,25 sn). **Kullanıcı kararı: virgül duraklaması kısaltılmayacak**
- **Kayıt listesi:** `BabaKartalVoice/docs/prompts_tr.txt` sonuna "Yumuşak g (ğ)" bölümü, 20 cümle (ünlü arası,
  ünsüz önü, sözcük sonu ğ; -ğa/-ğe yönelme ekleri). Kimlikler metin özetinden geldiği için eski kayıtlar
  etkilenmez. Telefondaki kayıt uygulamasına girmesi için `scripts/build_apk.ps1` ile APK yeniden derlenmeli
- Test: yalnız json ve yorum değişti; `assembleDebug`
