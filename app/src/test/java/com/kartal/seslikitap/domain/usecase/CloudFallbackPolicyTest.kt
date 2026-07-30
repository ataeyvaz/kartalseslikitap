package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.domain.model.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFallbackPolicyTest {

    private val fallbackEnabled = UserSettings.Default.copy(
        autoFallbackToCloud = true,
        cloudFallbackConfidenceThreshold = 0.6f,
    )

    @Test
    fun `dusuk guven ve izin varsa buluta gecilir`() {
        assertTrue(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.4f,
                settings = fallbackEnabled,
                isCloudProviderAvailable = true,
                isAlreadyCloud = false,
            ),
        )
    }

    @Test
    fun `yeterli guven varsa ucretli cagri yapilmaz`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.85f,
                settings = fallbackEnabled,
                isCloudProviderAvailable = true,
                isAlreadyCloud = false,
            ),
        )
    }

    @Test
    fun `kullanici izin vermediyse asla gecilmez`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.1f,
                settings = UserSettings.Default.copy(autoFallbackToCloud = false),
                isCloudProviderAvailable = true,
                isAlreadyCloud = false,
            ),
        )
    }

    @Test
    fun `anahtar yoksa gecilmez`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.1f,
                settings = fallbackEnabled,
                isCloudProviderAvailable = false,
                isAlreadyCloud = false,
            ),
        )
    }

    @Test
    fun `zaten bulutta taninmissa ikinci kez gonderilmez`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.2f,
                settings = fallbackEnabled,
                isCloudProviderAvailable = true,
                isAlreadyCloud = true,
            ),
        )
    }

    @Test
    fun `skor bilinmiyorsa korlemesine para harcanmaz`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = null,
                settings = fallbackEnabled,
                isCloudProviderAvailable = true,
                isAlreadyCloud = false,
            ),
        )
    }

    @Test
    fun `esik tam sinirda gecilmez`() {
        assertFalse(
            CloudFallbackPolicy.shouldFallback(
                confidence = 0.6f,
                settings = fallbackEnabled,
                isCloudProviderAvailable = true,
                isAlreadyCloud = false,
            ),
        )
    }
}
