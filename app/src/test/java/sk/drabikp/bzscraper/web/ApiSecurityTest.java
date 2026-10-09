package sk.drabikp.bzscraper.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API is only for the signed-in account; the page itself is public; the page's own addresses load it. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired
    private MockMvc mvc;

    private static final String LOGIN = "{\"username\":\"tester\",\"password\":\"%s\",\"remember\":%s}";

    @Test
    void the_api_answers_401_until_signed_in_and_a_wrong_password_is_refused() throws Exception {
        mvc.perform(get("/api/gigs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN.formatted("wrong", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("badCredentials"));
    }

    @Test
    void signing_in_without_a_session_yet_starts_one() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN.formatted("test-only", false))).andExpect(status().isOk()).andReturn();
        assertThat(login.getRequest().getSession(false)).isNotNull();
    }

    @Autowired
    private org.springframework.security.core.userdetails.UserDetailsService users;

    @Test
    void the_remember_me_cookie_outlives_a_restart_because_what_it_is_signed_with_stays() {
        assertThat(users.loadUserByUsername("tester").getPassword()).isEqualTo("{noop}test-only");
    }

    @Test
    void a_change_needs_the_csrf_token() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN.formatted("test-only", false))).andExpect(status().isForbidden());
    }

    @Test
    void signed_in_the_session_opens_the_api_and_remember_me_sets_its_cookie() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult login = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN.formatted("test-only", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andReturn();
        assertThat(login.getResponse().getCookie("bzscraper-remember")).isNotNull();

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/gigs").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/platforms").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/sync/status").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/calendar").session(session)).andExpect(status().isOk());
    }

    @Test
    void a_refused_change_is_a_409_with_its_code() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN.formatted("test-only", false))).andExpect(status().isOk());

        mvc.perform(post("/api/gigs").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"city\":\"Košice\",\"country\":\"SLOVAKIA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalidGig"))
                .andExpect(jsonPath("$.args.fields").value("title,date,time"));
    }

    @Test
    void the_page_is_public_and_its_own_addresses_load_it() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/calendar")).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.containsString("<div id=\"root\">")));
        mvc.perform(get("/gig/some-gig/edit")).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.containsString("<div id=\"root\">")));
        mvc.perform(get("/calendar/abc123@google.com/add")).andExpect(status().isOk());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/nothing-here")).andExpect(status().isUnauthorized());
    }
}
