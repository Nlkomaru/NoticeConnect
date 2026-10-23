package com.noticemc.noticeconnect.common

import com.noticemc.noticeconnect.common.mcapi.McApiClient
import com.noticemc.noticeconnect.common.mcapi.McApiRequest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * mc-api v1 へのリクエスト組み立て (純粋関数) のテスト。
 */
class McApiClientTest {
    private val uuid = UUID.fromString("00068061-a5fc-4e10-abb3-43e23006cf40")

    @Test
    fun buildsRegisterRequest() {
        assertEquals(
            McApiRequest(
                method = "PUT",
                url = "https://mc-api.morinoparty.workers.dev/v1/players/00068061-a5fc-4e10-abb3-43e23006cf40",
                body = """{"name":"Nikomaru"}""",
            ),
            McApiClient.registerRequest("https://mc-api.morinoparty.workers.dev", uuid, "Nikomaru"),
        )
    }

    @Test
    fun buildsBatchRequest() {
        assertEquals(
            McApiRequest(
                method = "POST",
                url = "https://mc-api.morinoparty.workers.dev/v1/players/batch",
                body = """{"uuids":["00068061-a5fc-4e10-abb3-43e23006cf40","11111111-2222-3333-4444-555555555555"],"fetch":false}""",
            ),
            McApiClient.batchRequest(
                "https://mc-api.morinoparty.workers.dev",
                listOf("00068061-a5fc-4e10-abb3-43e23006cf40", "11111111-2222-3333-4444-555555555555"),
            ),
        )
    }

    @Test
    fun trimsTrailingSlashFromBaseUrl() {
        // 設定の url が "/" で終わっていても "//v1" にならない
        assertEquals(
            "https://example.com/v1/players/batch",
            McApiClient.batchRequest("https://example.com//", emptyList()).url,
        )
        assertEquals(
            "https://example.com/v1/players/00068061-a5fc-4e10-abb3-43e23006cf40/avatar?size=64",
            McApiClient.mcApiAvatarUrl("https://example.com/", uuid),
        )
    }
}
