package th.ac.bodin2.electives.api

import th.ac.bodin2.electives.utils.env

val isTest by lazy { env("APP_ENV") == "test" }
val isDev by lazy { env("APP_ENV") == "development" }