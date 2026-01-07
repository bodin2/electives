package th.ac.bodin2.electives.api

import th.ac.bodin2.electives.utils.getEnv

val isTest by lazy { getEnv("APP_ENV") == "test" }
val isDev by lazy { getEnv("APP_ENV") == "development" }