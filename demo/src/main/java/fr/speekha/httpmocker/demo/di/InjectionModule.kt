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

package fr.speekha.httpmocker.demo.di

import android.content.Context
import android.os.Environment
import fr.speekha.httpmocker.demo.service.GithubApiEndpoints
import fr.speekha.httpmocker.demo.ui.MainViewModel
import fr.speekha.httpmocker.jackson.JacksonMapper
import fr.speekha.httpmocker.okhttp.MockResponseInterceptor
import fr.speekha.httpmocker.okhttp.builder.mockInterceptor
import fr.speekha.httpmocker.policies.FilingPolicy
import fr.speekha.httpmocker.policies.MirrorPathPolicy
import fr.speekha.httpmocker.serialization.JSON_FORMAT
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

val injectionModule: Module = module {

    single<FilingPolicy> {
        MirrorPathPolicy(JSON_FORMAT)
    }

    single {
        mockInterceptor {
            decodeScenarioPathWith(get<FilingPolicy>())
            loadFileWith(get<Context>().assets::open)
            parseScenariosWith(JacksonMapper())
            saveScenarios(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                get()
            )
            addFakeNetworkDelay(500)
        }
    }

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(get<MockResponseInterceptor>())
            .build()
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.github.com")
            .client(get())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
    }

    single<GithubApiEndpoints> {
        get<Retrofit>().create(GithubApiEndpoints::class.java)
    }

    viewModel {
        MainViewModel(get(), get())
    }
}
