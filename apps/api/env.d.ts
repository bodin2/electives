declare global {
    namespace NodeJS {
        interface ProcessEnv {
            ELECTIVES_API_DB: string
            ELECTIVES_API_HOSTNAME: string
            ELECTIVES_API_PORT: string
            ELECTIVES_API_CORS_ORIGIN: string
            ELECTIVES_API_HASH_COST: string
            ELECTIVES_API_SESSION_DURATION: string
            ELECTIVES_API_SECRET_KEY: string
            ELECTIVES_API_PUBLIC_KEY: string
            ELECTIVES_API_RATE_LIMIT_AMOUNT: string
            ELECTIVES_API_RATE_LIMIT_WINDOW: string
        }
    }
}

export {}
