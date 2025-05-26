export const BaseUrl = `http://${process.env.ELECTIVES_API_HOSTNAME ?? 'localhost'}:${process.env.ELECTIVES_API_PORT ?? 3000}`

try {
    await fetch(BaseUrl)
} catch {
    console.error('Server is not running. Please start the server before running tests.')
    process.exit(1)
}
