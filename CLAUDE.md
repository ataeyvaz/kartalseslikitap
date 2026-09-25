# KartalSesliKitap — Claude için proje notları

Android (Kotlin + Jetpack Compose) sesli kitap uygulaması: kitap sayfasının fotoğrafı çekilir ya da PDF alınır →
OCR ile metne çevrilir → sesli okunur. Ayrıntılı ürün planı: `sesli-kitap-hibrit-plan.md`.
Yapılan işler ve kararlar tarih sırasıyla: **`surec.md`** — her oturumdan sonra oraya kısa bir giriş eklenir.

## Çalışma kuralları

- Kullanıcıyla (Ata, "usta" diye hitap eder) **her zaman Türkçe** konuşulur: cevaplar, sorular, özetler.
- Kod yorumları, commit mesajları ve belgeler Türkçe.
- Her iş bitince `surec.md`'ye giriş eklenir (tarih, ne yapıldı, neden, test, bilinen sınırlar).
- Commit ve push: kullanıcı istediğinde. Kullanıcı kendisi push yapmıyor; iş bitince push da unutulmamalı
  (onaylandıktan sonra). Depo **herkese açık**: https://github.com/ataeyvaz/kartalseslikitap
- Mimari ilke (plan Bölüm 1): OCR/TTS sağlayıcıları değiştirilebilir modüldür. Yeni sağlayıcı =
  `di/ProviderModule.kt`'ye `@Binds @IntoSet` satırı; üst katmanlar değişmez.

## Derleme ve test

- JDK: Android Studio'nun JBR'si (`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`), SDK `local.properties`'te.
- Birim testleri: `./gradlew testDebugUnitTest` · APK: `./gradlew assembleDebug`
- Bu bilgisayar 8 GB bellekli ve yavaş (i5-4210U): Gradle derlemeleri uzun sürer, bellek darlığında arka plan
  işleri kapanabilir.
- ABI bölmeli APK (arm64-v8a, armeabi-v7a, x86_64); telefona `adb install` ile kurulur (telefon ekranı açık olmalı,
  Xiaomi onay penceresi çıkar).

## Piper sesi ("Ata (erkek)") — cihaz üstü, internetsiz TTS sağlayıcısı

Kullanıcının kendi sesiyle eğitilmiş Piper (VITS) modeli, Sözcük masaüstü uygulamasındaki sesin aynısıdır.

- **Model:** `app/src/main/assets/sesler/ata.onnx` (+ `ata.onnx.json`, ~63 MB). Kaynağı Sözcük deposu:
  `Desktop\sozcuk\sozcuk\sesler\`. Eğitim: Kaggle, `Desktop\sozcuk\araclar\ses_egitimi\` (14 saatlik sürüm,
  2026-09-24). Çıkış ayarı json'da `inference`: `length_scale 1.1`, `noise_scale 0.5`, `noise_w 0.6` (kullanıcı tercihi,
  2026-09-25: dört ayar dinlendi, "C/sakin" seçildi; `noise_w` bu modelde süreyi etkilemiyor). Sözcük'le aynı tutulur.
- **Telaffuz:** model ses birimi (IPA) girdisi bekler; eğitimde Sözcük'ün `sozcuk/pronunciation.py`'si kullanıldı.
  Kotlin karşılığı (`TurkishPhonemizer`) Python'la **harfi harfine aynı** çıktı vermek zorunda — yoksa model yanlış
  okur (BabaKartalVoice'taki ilk Piper denemesi fonem uyuşmazlığı yüzünden bozulmuştu). Doğrulama: Python'dan
  üretilen altın dosyayla karşılaştıran birim testi. **Python tarafı değişirse altın dosya yeniden üretilmeli.**
- **Python'dan taşırken tuzaklar:** Python `re` Unicode'dur (`\w`, `\b`, `\d`) → Kotlin'de
  `(?U)` gerekir, ama **yalnız bilgisayardaki testlerde** — Android ICU kullanır, orada bu sınıflar zaten Unicode'dur
  ve bayrak sorun çıkarır (`TurkishPhonemizer.uRegex`). Telefonda da doğrulama: `androidTest`'teki altın test; Türkçe küçük harf `I→ı, İ→i` önce elle; kaynakta görünmez
  karakterler var (cümle bölmede U+2028/U+2029, tırnaklar U+2018/2019/201C/201D, … U+2026, – U+2013) —
  Kotlin'de kaçış dizisiyle yazılır.
- **Lisans:** temel model `tr_TR dfki` CC BY-NC-SA 4.0 → ses **ticari kullanıma kapalı**. Uygulama ücretli
  yayınlanırsa bu ses çıkarılmalı.
- **Gizlilik kararı (kullanıcı, 2026-09-24):** ses modeli herkese açık Sözcük deposuna konuldu; aynı model bu
  depoya da girer. Sebepleri (2026-09-25): (1) hazır ses yerine kendi sesini koyarak herkesin kendi ses modelini
  eğitebileceğini göstermek; (2) bilgisayarda disk arızası olursa modeller kaybolmasın, sürümler kıyaslanabilsin.
- Ayrıca `onnxruntime-android` (MIT) kullanılır; masaüstünde de aynı sürüm (1.30.0).

## İlgili klasörler (bu bilgisayarda)

- `Desktop\sozcuk` — Sözcük (Türkçe kelime işlemci, Python). Sesli okuma ve telaffuz kodunun ana kaynağı.
- `Desktop\BabaKartalVoice` — kullanıcının ses kayıtları (asıl varlık, yeniden üretilemez), XTTS modeli, kayıt
  uygulaması (Android, `com.kartal.babavoice`).
