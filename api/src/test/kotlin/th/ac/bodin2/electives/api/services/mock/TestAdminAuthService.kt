package th.ac.bodin2.electives.api.services.mock

import th.ac.bodin2.electives.api.services.AdminAuthService

class TestAdminAuthService : AdminAuthService {
    override fun newChallenge() = "test-challenge"

    override suspend fun createSession(
        id: UInt,
        signature: String,
        aud: String,
        ip: String,
    ): AdminAuthService.CreateSessionResult {
        return if (id == TestServiceConstants.ADMIN_ID && signature == "admin-signature") {
            AdminAuthService.CreateSessionResult.Success(TestServiceConstants.ADMIN_TOKEN)
        } else {
            AdminAuthService.CreateSessionResult.InvalidSignature
        }
    }

    override fun permitsIP(ip: String): Boolean = true
}
