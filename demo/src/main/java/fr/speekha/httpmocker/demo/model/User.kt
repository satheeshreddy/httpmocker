package fr.speekha.httpmocker.demo.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class User
@JsonCreator
constructor(

    @JsonProperty("login")
    val login: String? = null,

    @JsonProperty("id")
    val id: Int? = null,

    @JsonProperty("avatar_url")
    val avatarUrl: String? = null,

    @JsonProperty("url")
    val url: String? = null,

    @JsonProperty("bio")
    val bio: Any? = null,

    @JsonProperty("public_repos")
    val publicRepos: Int? = null,

    @JsonProperty("contributions")
    val contributions: Int = 0
)