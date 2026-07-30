package com.kartal.seslikitap.domain.provider

sealed class ProviderException(
    val providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class OcrProviderException(
    providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : ProviderException(providerId, message, cause)

class TtsProviderException(
    providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : ProviderException(providerId, message, cause)

/** Ayarlarda seçili sağlayıcı bulunamadığında/kullanılamadığında. */
class ProviderUnavailableException(
    providerId: ProviderId,
    message: String = "Sağlayıcı kullanılamıyor: $providerId",
    cause: Throwable? = null,
) : ProviderException(providerId, message, cause)
