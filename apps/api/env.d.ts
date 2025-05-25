declare global {
    namespace NodeJS {
        interface ProcessEnv {
            ELECTIVE_API_DB: string
            ELECTIVE_API_HOSTNAME: string
            ELECTIVE_API_PORT: string
        }
    }
}

export {}
