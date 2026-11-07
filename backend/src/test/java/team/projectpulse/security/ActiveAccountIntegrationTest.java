package team.projectpulse.security;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a session already open when its account is deactivated ends at its next request.
 */
@DisplayName("Integration tests for the account-status check on bearer tokens")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN_ID = 4;
    private static final int ERIC_ID = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    private String login(String username) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(username, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private void disable(int userId) {
        PeerEvaluationUser user = this.userRepository.findById(userId).orElseThrow();
        user.setEnabled(false);
        this.userRepository.saveAndFlush(user);
    }

    @Test
    void testTokenIssuedBeforeDeactivationIsRefusedOnItsNextRequest() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        String ericToken = login("e.hudson@abc.edu");

        disable(JOHN_ID);

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN_ID).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("The access token provided is expired, revoked, malformed, or invalid for other reasons."));

        // A teammate's session is unaffected
        this.mockMvc.perform(get(this.baseUrl + "/students/" + ERIC_ID).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void testDeactivatedInstructorsTokenIsRefusedToo() throws Exception {
        String billToken = login("b.gates@abc.edu");

        disable(2);

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN_ID).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
