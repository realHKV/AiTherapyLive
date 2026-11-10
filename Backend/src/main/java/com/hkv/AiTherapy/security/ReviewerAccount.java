package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;

/** Existing user record marker for a read-only website reviewer. */
public final class ReviewerAccount {
    public static final String PROVIDER = "razorpay-review";

    private ReviewerAccount() { }

    public static boolean isReviewer(User user) {
        return user != null && PROVIDER.equals(user.getOauthProvider());
    }
}
