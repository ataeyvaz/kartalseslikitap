package com.kartal.seslikitap.domain.provider

/**
 * Bir OCR/TTS sağlayıcısının kalıcı kimliği.
 *
 * Veritabanında ve ayarlarda bu string saklanır; bu yüzden bir kez yayınlandıktan
 * sonra değeri asla değiştirilmemelidir (sağlayıcının görünen adı değişebilir).
 */
@JvmInline
value class ProviderId(val value: String) {
    override fun toString(): String = value
}

object ProviderIds {
    // OCR
    val MlKit = ProviderId("ml_kit")
    val GoogleCloudVision = ProviderId("google_cloud_vision")
    val AzureVision = ProviderId("azure_vision")
    val AwsTextract = ProviderId("aws_textract")

    // Metin düzeltme
    val NoCorrection = ProviderId("no_correction")
    val TurkishDictionary = ProviderId("turkish_dictionary")
    val ClaudeCorrection = ProviderId("claude_correction")

    // TTS
    val AndroidTts = ProviderId("android_tts")
    val GoogleCloudTts = ProviderId("google_cloud_tts")
    val ElevenLabs = ProviderId("elevenlabs")
    val AzureNeuralTts = ProviderId("azure_neural_tts")
}
