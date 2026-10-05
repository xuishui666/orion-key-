package com.orionkey.service;

import com.orionkey.model.request.ChangePasswordRequest;
import com.orionkey.model.response.UserProfileResponse;

import java.util.UUID;

public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    void changePassword(UUID userId, ChangePasswordRequest request);

}
