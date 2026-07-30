# Sesli Kitap Uygulaması — Hibrit OCR + TTS Mimari Planı

**Hedef platform:** Android (önce) → iOS (sonra)
**Vizyon:** Kullanıcı kitap sayfası fotoğrafı çeker → OCR ile metne dönüştürülür → TTS ile sesli okunur. Sistem, hem ücretsiz/on-device hem de kullanıcının kendi API anahtarıyla (BYOK) bağlanabileceği premium bulut sağlayıcılarını destekleyen **hibrit ve provider-agnostik** bir mimariye sahip olacak.

---

## 1. Temel Mimari Prensibi: "Provider Abstraction Layer"

Uygulamanın kalbi, OCR ve TTS işlemlerinin **değiştirilebilir modüller (provider)** olarak tasarlanmasıdır. Uygulama kodu asla "Google Vision kullan" demez, "aktif OCR provider'ı çağır" der. Böylece:

- Yeni bir sağlayıcı eklemek (örn. Azure, Claude, ElevenLabs) mevcut kodu bozmaz.
- Kullanıcı ayarlardan istediği sağlayıcıyı seçebilir.
- Ücretsiz kullanıcı on-device çözümle, ileri düzey kullanıcı kendi API anahtarıyla çalışır.

```
interface OcrProvider {
    suspend fun recognize(image: Bitmap): OcrResult
    val requiresApiKey: Boolean
    val isOnDevice: Boolean
    val name: String
}

interface TtsProvider {
    suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream
    suspend fun registerClonedVoice(audioSample: AudioFile, label: String): ClonedVoiceId?
    val supportsVoiceCloning: Boolean
    val requiresApiKey: Boolean
    val isOnDevice: Boolean
    val name: String
}
```

### OCR Provider Katmanı
| Provider | Tip | Maliyet | Ne zaman kullanılır |
|---|---|---|---|
| ML Kit Text Recognition | On-device | Ücretsiz | Varsayılan, hızlı deneme |
| Google Cloud Vision | Bulut (API key) | Ücretli | Yüksek doğruluk isteyen kullanıcı |
| Azure AI Vision (Read) | Bulut (API key) | Ücretli | Çoklu dil / alternatif |
| AWS Textract | Bulut (API key) | Ücretli | Karmaşık sayfa düzeni |

### TTS Provider Katmanı
| Provider | Tip | Maliyet | Doğallık | Ne zaman kullanılır |
|---|---|---|---|---|
| Android TextToSpeech | On-device | Ücretsiz | Orta-düşük (robotik hissedebilir) | Varsayılan, hızlı deneme |
| Google Cloud TTS (Neural2/Studio) | Bulut (API key) | Ücretli | Yüksek | Kaliteli sabit fiyat |
| ElevenLabs | Bulut (API key) | Ücretli | En yüksek (insansı, prozodi çok iyi) | Kitap dinleme deneyimi öncelikliyse |
| Azure Neural TTS | Bulut (API key) | Ücretli | Yüksek | Çoklu dil alternatifi |

**Fallback mantığı:** On-device OCR güven skoru düşükse → otomatik bulut provider'a geç (kullanıcı ayarında açık/kapalı olabilir, çünkü bulut = maliyet).

**Doğallık/akıcılık notu:** Kitap dinleme deneyiminde robotik ses en büyük UX kırıcısıdır. Bu yüzden:
- Varsayılan on-device TTS, ücretsiz modda makul ama en robotik seçenektir — kullanıcıya "daha doğal ses için bir provider bağlayabilirsin" şeklinde nazikçe yönlendirme yapılmalı (onboarding'de veya ilk kitap bitiminde).
- Google Neural2/Studio ve ElevenLabs sesleri, uzun metinlerde **prozodi** (tonlama, duraklama, cümle vurgusu) açısından çok daha iyidir — bulut provider'ları ayarlarda "Doğal Okuma" etiketiyle öne çıkarılmalı.
- Metin gönderilmeden önce **SSML işaretleme** (noktalama duraklamaları, soru/ünlem tonlaması, paragraf arası nefes payı) eklenmesi robotik hissi ciddi azaltır — bu, provider fark etmeksizin metin hazırlama katmanında uygulanacak, provider-agnostik bir adım olarak tasarlanmalı.
- Çocuk kitabı modunda ses parametreleri farklılaşır: biraz daha yavaş konuşma hızı, daha sıcak/canlı tonlama — bu da ayrı bir voice preset olarak tutulmalı (aşağıya bkz.).

---

## 2. Kullanıcı Esnekliği: "BYOK" (Bring Your Own Key) Modeli

İki kullanım modu olacak:

1. **Varsayılan/Ücretsiz mod** → Tamamen on-device (ML Kit + Android TTS). Kurulum gerektirmez, internet gerektirmez.
2. **Gelişmiş mod (BYOK)** → Kullanıcı Ayarlar'dan kendi API anahtarını girer (Google Cloud, ElevenLabs vb.). Uygulama o anahtarla doğrudan sağlayıcıya bağlanır.

**Güvenlik notu:** API anahtarları asla düz metin saklanmamalı → **Android Keystore + EncryptedSharedPreferences** kullanılacak. Anahtar hiçbir zaman kendi sunucumuza gönderilmeyecek (kullanıcı gizliliği + bizim sorumluluğumuzu azaltır).

**Opsiyonel 3. mod (ileride):** Kendi backend proxy'niz üzerinden "yönetilen premium abonelik" — kullanıcı kendi anahtarını girmek istemezse, sizin sunucunuz üzerinden kotalı kullanım sunarsınız. Bu MVP'de gerekmez, faz 3'te değerlendirilir.

---

## 3. Teknoloji Yığını (Android)

- **Dil/UI:** Kotlin + Jetpack Compose
- **Mimari:** MVVM + Clean Architecture (data/domain/presentation katmanları)
- **Kamera:** CameraX (otomatik kenar tespiti + perspektif düzeltme için)
- **Yerel veritabanı:** Room (kitaplar, sayfalar, ilerleme kaydı, provider ayarları)
- **Ağ:** Retrofit + OkHttp (bulut provider'lar için)
- **Arka plan işleri:** WorkManager (uzun OCR/TTS işlemleri için)
- **Ses oynatma:** ExoPlayer/Media3 (streaming TTS çıktısı, hız kontrolü, sayfa takibi)
- **Bağımlılık yönetimi:** Hilt/Dagger

---

## 4. Veri Modeli (özet)

```
Book(id, title, coverImage, createdAt, isChildrenBook: Boolean, narratorGender: Enum[FEMALE, MALE, NEUTRAL])
Page(id, bookId, order, imagePath, rawOcrText, cleanedText, ocrProviderUsed, confidenceScore)
UserSettings(defaultOcrProvider, defaultTtsProvider, apiKeys[encrypted], defaultNarratorGender, playbackSpeed, autoFallbackToCloud: Boolean)
VoiceConfig(gender: Enum[FEMALE, MALE, NEUTRAL], isChildrenPreset: Boolean, speakingRate, pitch, customClonedVoiceId: String?)
ReadingProgress(bookId, currentPageId, currentAudioPosition)
```

**Not:** `isChildrenBook` ve `narratorGender`, kitap oluşturma/kaydetme adımında kullanıcıdan alınır (sistem bunu otomatik anlayamayacağı için). Bu iki alan `VoiceConfig`'e dönüştürülüp TTS provider'a iletilir — yani provider'lar "çocuk kitabı" veya "cinsiyet" kavramını bilmez, sadece kendilerine uygun ses/parametre eşlemesini alır (bkz. Bölüm 5.5).

---

## 4.5. Kitap Türü ve Anlatıcı Seçimi (Kullanıcı Girdisi)

Sistem bir kitabın çocuk kitabı olup olmadığını veya hangi cinsiyette bir sesle okunmasının uygun olacağını kendi başına anlayamaz — bu yüzden bu iki bilgi **kitap oluşturma ekranında kullanıcıdan alınır**:

- **"Çocuk kitabı" tikbox'ı** — işaretlenirse:
  - Konuşma hızı biraz düşürülür (varsayılan hızın ~%85-90'ı)
  - Daha "canlı/sıcak" tonlamalı bir ses/preset seçilir (ElevenLabs ve Google Neural2'de buna uygun hazır sesler mevcut — provider entegrasyonunda bu sesler ayrıca etiketlenmeli)
  - (İleride, Faz 3) OCR sonrası metin temizleme adımına "büyük punto/az kelime" gibi çocuk kitabı sayfa düzenine özel toleranslar eklenebilir
- **Anlatıcı cinsiyeti seçimi** — kullanıcı kitap bazında (istersen genel ayarlarda varsayılan olarak da) **Kadın / Erkek / Nötr** seçeneklerinden birini seçer. Bu seçim, aktif TTS provider'ının o cinsiyete uygun ses listesinden birine eşlenir.

Bu iki alan `VoiceConfig` nesnesine dönüştürülüp TTS provider'a parametre olarak geçilir; yani **provider'ların kendisi "çocuk kitabı" ya da "cinsiyet" kavramını bilmez** — sadece kendi ses kütüphanesinden uygun sesi/parametreyi seçen bir eşleme katmanı (`VoiceMappingResolver`) bu işi yapar. Bu tasarım, ileride yeni bir TTS sağlayıcısı eklendiğinde sadece o sağlayıcı için bir eşleme tablosu yazmanı yeterli kılar, üst katmanda hiçbir değişiklik gerekmez.

```
interface VoiceMappingResolver {
    fun resolveVoice(provider: TtsProvider, config: VoiceConfig): ProviderSpecificVoiceId
}
```

**UI akışı (öneri):** Kitap ekleme ekranında fotoğraf çekimi/yükleme adımından hemen sonra ya da öncesinde küçük bir "Kitap Bilgisi" adımı: `[ ] Bu bir çocuk kitabı` tikbox'ı + `Anlatıcı sesi: (Kadın / Erkek / Nötr)` seçici. Kullanıcı bu adımı atlarsa mantıklı varsayılanlar (Nötr ses, çocuk kitabı = hayır) kullanılır.

---

## 4.6. Kendi Sesini Kaydederek Klonlama (Voice Cloning)

Kullanıcının kendi sesini kaydedip TTS'e dahil edebilmesi için sisteme **ses klonlama** desteği eklenecek. Bu, "provider-agnostik" mimarinin gücünü tam olarak gösteren bir örnek: klonlama yeteneği her TTS provider'da yok, bu yüzden `TtsProvider` interface'ine `supportsVoiceCloning: Boolean` ve `registerClonedVoice()` metodu eklendi (bkz. Bölüm 1). Sadece bunu destekleyen provider'lar (örn. ElevenLabs) bu akışı sunar; desteklemeyenlerde (Android TTS, Google Neural2 vb.) bu seçenek arayüzde otomatik olarak gizlenir.

**Önerilen sağlayıcı ve seviyeler (ElevenLabs üzerinden):**
- **Instant Voice Cloning (IVC)** — 1-3 dakikalık temiz ses örneğiyle hızlıca "yeterli" bir klon üretir, model eğitmez, mevcut ses modeliyle eşleştirme yapar. Hızlı ama kalite tavanı var.
- **Professional Voice Cloning (PVC)** — daha uzun (birkaç dakika – birkaç saat) stüdyo kalitesinde örnekle çok daha yüksek sadakatte klon üretir. Sesli kitap gibi uzun form içerik için asıl hedeflenmesi gereken seviye bu.

**Akış:**
1. Kullanıcı, Ayarlar → "Kendi Sesini Kaydet" ekranından mikrofonla (sessiz ortamda, net okuma) bir örnek kaydeder veya hazır bir ses dosyası yükler.
2. Uygulama bu dosyayı aktif provider'ın klonlama endpoint'ine gönderir (`registerClonedVoice`).
3. Provider bir `voiceId` döner; bu, `VoiceConfig.customClonedVoiceId` alanında saklanır.
4. Kullanıcı bir kitabı bu özel sesle okutmak istediğinde, `VoiceMappingResolver` artık cinsiyet/preset eşlemesi yapmaz — doğrudan bu `voiceId`'yi kullanır.

**Alternatif sağlayıcılar (ileride değerlendirilebilir):** Azure Custom Neural Voice, PlayHT, ve düşük gecikmeli senaryolar için Inworld Voice AI (birkaç saniyelik örnekle gerçek zamanlı klon üretebiliyor, ama uzun form kitap okuma için ElevenLabs PVC kalite açısından hâlâ referans nokta).

**Kendi altyapımızda barındırma (uzun vadeli/opsiyonel alternatif):** Açık kaynak modeller (Coqui XTTS, OpenVoice, Tortoise TTS) self-host edilerek bulut sağlayıcıya bağımlı kalmadan klonlama yapılabilir. Ancak bu, GPU maliyeti ve model bakımı gerektirdiğinden MVP/Faz 3 için önerilmez — önce bulut API entegrasyonuyla ilerlenmeli, ileride ölçek/maliyet gerektirirse bu seçenek Faz 4+ olarak değerlendirilebilir.

**Onay/gizlilik notu:** Ses kaydı hassas kişisel veridir. Kullanıcıya kaydın hangi sağlayıcıya, ne amaçla gönderildiği açıkça belirtilmeli ve kayıt öncesi net bir onay (consent) ekranı gösterilmeli.

---

## 5. Yol Haritası (Fazlar)

### Faz 1 — MVP (On-device, tek platform)
- Kamera ile sayfa çekimi + otomatik kenar/perspektif düzeltme
- ML Kit OCR entegrasyonu
- Basit metin temizleme (satır birleştirme, tire düzeltme)
- Android TextToSpeech ile okuma (SSML işaretleme ile mümkün olduğunca akıcı hale getirilmiş)
- **Kitap ekleme ekranında "çocuk kitabı" tikbox'ı + anlatıcı cinsiyeti seçici** (bkz. Bölüm 4.5) — MVP'de bile bu iki alan Book modelinde tutulur ve Android TTS'in cinsiyet/hız parametrelerine eşlenir, böylece Faz 2'de bulut provider eklenince altyapı zaten hazır olur.
- Kitap/sayfa yönetimi (Room ile kaydetme, ilerleme takibi)
- **Çıktı:** Tamamen ücretsiz, internetsiz çalışan temel ürün.

### Faz 2 — Hibrit Provider Sistemi
- Provider abstraction katmanının kurulması (interface'ler + factory pattern)
- Google Cloud Vision + Google Cloud TTS entegrasyonu (ilk bulut sağlayıcı çifti)
- Ayarlar ekranı: provider seçimi + API key girişi (Keystore ile şifreli saklama)
- Güven skoruna dayalı otomatik fallback mantığı

### Faz 3 — Sağlayıcı Genişletme + Kalite
- ElevenLabs TTS entegrasyonu (en doğal ses seçeneği)
- **Ses klonlama (voice cloning) özelliği** — kullanıcının kendi sesini kaydedip TTS'e dahil edebilmesi (bkz. Bölüm 4.6): kayıt ekranı, ElevenLabs IVC/PVC entegrasyonu, onay/gizlilik akışı
- Azure/AWS OCR alternatifleri
- OCR sonrası LLM tabanlı metin düzeltme (opsiyonel, Claude API ile "bu taranmış metni düzelt")
- Çoklu dil desteği

### Faz 4 — iOS Portu + Cilalama
- Aynı provider abstraction mantığı Swift tarafında (Vision Framework + AVSpeechSynthesizer varsayılan)
- Performans optimizasyonu, offline kuyruk (kötü internet durumunda bekletip sonra işleme)
- (Opsiyonel) Yönetilen backend proxy / abonelik modeli

---

## 6. Claude Code ile Başlarken — İlk Adımlar

1. Proje iskeletini oluştur: Kotlin + Jetpack Compose + Hilt boilerplate
2. `domain/provider` paketinde `OcrProvider` ve `TtsProvider` interface'lerini tanımla
3. İlk somut implementasyon: `MlKitOcrProvider` ve `AndroidTtsProvider` (on-device, API key gerektirmeyen — en hızlı çalışan MVP parçası)
4. Room veritabanı şemasını (Book/Page/Settings) kur
5. Basit bir "çek → OCR → oku" akışını uçtan uca çalışır hale getir (tek provider ile)
6. Ayarlar ekranını ve provider seçim mantığını ekle
7. İkinci provider çiftini (Google Cloud) entegre ederek hibrit yapıyı kanıtla

Bu sırayla ilerlemek, Faz 1'i hızlıca çalışır bir demo haline getirir, sonra Faz 2'deki hibrit yapıyı üzerine inşa edersin.

---

## 7. Dikkat Edilmesi Gerekenler

- **Maliyet şeffaflığı:** Bulut provider kullanan kullanıcıya tahmini maliyet/kullanım göstergesi sunmak iyi bir UX pratiği olur.
- **Telif hakkı:** Kullanıcıların yükleyeceği kitap sayfaları telifli olabilir — uygulama sadece kişisel kullanım için "kendi kitabını sesli dinleme" aracı olarak konumlandırılmalı, içerik paylaşımı/dağıtımı özelliği eklenmemeli.
- **Erişilebilirlik:** Bu tür bir uygulama görme engelli kullanıcılar için de değerli olabilir — TalkBack uyumluluğuna baştan dikkat etmek iyi olur.
