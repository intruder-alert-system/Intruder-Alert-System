package com.techsutra.intruderalert.security;

import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.entity.UserSession;

public class AuthenticatedUser {
    private final UserAccount user;
    private final UserSession session;

    public AuthenticatedUser(UserAccount user, UserSession session) {
        this.user = user;
        this.session = session;
    }

    public UserAccount getUser() {
        return user;
    }

    public UserSession getSession() {
        return session;
    }
}
