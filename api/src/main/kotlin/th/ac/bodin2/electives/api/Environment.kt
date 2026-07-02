package th.ac.bodin2.electives.api

import th.ac.bodin2.electives.utils.env

val isTest: Boolean get() = env("APP_ENV") == "test"
val isDev: Boolean get() = env("APP_ENV") == "development"
