/*
 * Copyright 2019-2020 David Blanc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.speekha.httpmocker.interceptor

import fr.speekha.httpmocker.Mode
import fr.speekha.httpmocker.Mode.DISABLED
import fr.speekha.httpmocker.Mode.ENABLED
import fr.speekha.httpmocker.assertThrows
import fr.speekha.httpmocker.model.ResponseDescriptor
import fr.speekha.httpmocker.okhttp.builder.mockInterceptor
import fr.speekha.httpmocker.scenario.RequestCallback
import fr.speekha.httpmocker.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Dynamic Mocks with OkHttp")
class DynamicMockTests : OkHttpTests() {

    @Nested
    @DisplayName("Given an mock interceptor that is disabled")
    inner class DisabledInterceptor {

        @Test
        @DisplayName("When a request is made, then the interceptor should not interfere with it")
        fun `should not interfere with requests when disabled`() {
            setupProvider(DISABLED) { null }
            enqueueServerResponse(REQUEST_OK_CODE, "body")

            val response = executeRequest("")

            assertResponseCode(response, REQUEST_OK_CODE, "OK")
            assertEquals("body", response.body?.string())
        }
    }

    @Nested
    @DisplayName("Given an enabled mock interceptor with a dynamic callback")
    inner class DynamicTests {

        @Test
        @DisplayName("When no response is provided, then a 404 error should occur")
        fun `should return a 404 error when response is not found`() {
            setupProvider(ENABLED) { null }

            val response = executeRequest("/unknown")

            assertResponseCode(response, NOT_FOUND_CODE, "Not Found")
        }

        @Test
        @DisplayName("When an error occurs while answering a request, then the exception should be let through")
        fun `should let exceptions through they occur`() {
            setupProvider(ENABLED) { error("Unexpected error") }

            assertThrows<IllegalStateException> {
                executeRequest("/unknown")
            }
        }

        @Test
        @DisplayName("When a lambda is provided, then it should be used to answer requests")
        fun `should reply with a dynamically generated response`() {
            val resultCode = 202
            setupProvider {
                ResponseDescriptor(code = resultCode, body = "some random body")
            }
            val response = executeRequest(url)

            assertEquals(resultCode, response.code)
            assertEquals("some random body", response.body?.string())
        }

        @Test
        @DisplayName("When a stateful callback is provided, then it should be used to answer requests")
        fun `should reply with a stateful callback`() {
            val resultCode = 201
            val body = "Time: ${System.currentTimeMillis()}"
            setupProvider { ResponseDescriptor(code = resultCode, body = body) }

            val response = executeRequest(url)

            assertEquals(resultCode, response.code)
            assertEquals(body, response.body?.string())
        }

        @Test
        @DisplayName(
            "When several callbacks are provided, " +
                "then they should be called in turn to find the appropriate response"
        )
        fun `should support multiple callbacks`() {
            val result1 = "First mock"
            val result2 = "Second mock"

            interceptor = mockInterceptor {
                useDynamicMocks { request ->
                    ResponseDescriptor(body = result1).takeIf {
                        request.path.contains("1")
                    }
                }
                useDynamicMocks {
                    ResponseDescriptor(body = result2)
                }
                setInterceptorStatus(ENABLED)
            }

            client = OkHttpClient.Builder().addInterceptor(interceptor).build()

            val response1 = executeRequest("http://www.test.fr/request1")
            val response2 = executeRequest("http://www.test.fr/request2")

            assertEquals(result1, response1.body?.string())
            assertEquals(result2, response2.body?.string())
        }

        @Test
        @DisplayName(
            "When the response is an error, then the proper exception should be thrown"
        )
        fun `should support exception results`() {

            setupProvider {
                error("Should throw an error")
            }

            assertThrows<IllegalStateException> {
                executeRequest("http://www.test.fr/request1")
            }
        }

        @Test
        @DisplayName(
            "When 2 request are executed simultaneously then proper responses are returned"
        )
        fun `should synchronize loadResponse`() {
            setupProvider(ENABLED) {
                ResponseDescriptor(body = "body${it.path.last()}")
            }
            repeat(1000) {
                testSimultaneousRequests()
            }
        }

        private fun testSimultaneousRequests() {
            val response: Array<Response?> = arrayOfNulls(2)
            val running = MutableStateFlow(false)
            runBlocking {
                response.indices.forEach { i ->
                    launch(Dispatchers.IO) {
                        response[i] = delayedRequest(running, i)
                    }
                }
                running.emit(true)
            }

            response.indices.forEach { i ->
                assertEquals("body$i", response[i]?.body?.byteString()?.utf8())
            }
        }

        private suspend fun delayedRequest(lock: StateFlow<Boolean>, i: Int): Response {
            lock.first { it }
            return executeRequest("/request$i")
        }
    }

    private fun setupProvider(
        status: Mode = ENABLED,
        callback: RequestCallback
    ) {
        interceptor = mockInterceptor {
            useDynamicMocks(callback)
            setInterceptorStatus(status)
        }

        client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    }
}
