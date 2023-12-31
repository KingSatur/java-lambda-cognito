package com.jdlearner.users.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

public class CognitoUserService {

    private final CognitoIdentityProviderClient cognitoIdentityProviderClient;

    public CognitoUserService(String region) {
        this.cognitoIdentityProviderClient = CognitoIdentityProviderClient.builder().region(Region.of(region)).build();
    }

    public JsonObject createUser(JsonObject userDetails, String appClientId, String appSecret) {
        String email = userDetails.get("email").getAsString();
        String password = userDetails.get("password").getAsString();
        String firstName = userDetails.get("firstName").getAsString();
        String lastName = userDetails.get("lastName").getAsString();

        AttributeType userIdAttribute = AttributeType.builder().name("custom:userId")
                .value(UUID.randomUUID().toString())
                .build();
        AttributeType nameAttribute = AttributeType.builder().name("name").value(firstName + " " + lastName).build();
        AttributeType emailAttribute = AttributeType.builder().name("email").value(email).build();

        SignUpRequest signUpRequest = SignUpRequest.builder()
                .username(email)
                .password(password)
                .userAttributes(List.of(emailAttribute, nameAttribute, userIdAttribute))
                .clientId(appClientId)
                .secretHash(calculateSecretHash(appClientId, appSecret, email))
                .build();

        SignUpResponse response = this.cognitoIdentityProviderClient.signUp(signUpRequest);
        JsonObject createdUserResult = new JsonObject();
        createdUserResult.addProperty("isSuccess", response.sdkHttpResponse().isSuccessful());
        createdUserResult.addProperty("statusCode", response.sdkHttpResponse().statusCode());
        createdUserResult.addProperty("cognitoUserId", response.userSub());
        createdUserResult.addProperty("isConfirmed", response.userConfirmed());

        return createdUserResult;
    }

    public JsonObject confirmUser(String appClientId, String appSecret, String email, String confirmationCode) {
        ConfirmSignUpRequest confirmSignUpRequest = ConfirmSignUpRequest.builder()
                .secretHash(calculateSecretHash(appClientId, appSecret, email))
                .username(email)
                .confirmationCode(confirmationCode)
                .clientId(appClientId)
                .build();

        ConfirmSignUpResponse confirmUserResponse = this.cognitoIdentityProviderClient
                .confirmSignUp(confirmSignUpRequest);

        JsonObject confirmProcessResponse = new JsonObject();
        confirmProcessResponse.addProperty("isSuccess", confirmUserResponse.sdkHttpResponse().isSuccessful());
        confirmProcessResponse.addProperty("statusCode", confirmUserResponse.sdkHttpResponse().statusCode());

        return confirmProcessResponse;
    }

    public JsonObject loginUser(String email, String password, String clientId, String clientSecret){
        InitiateAuthRequest initiateAuthRequest = InitiateAuthRequest.builder()
                .clientId(clientId)
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(Map.of(
                        "USERNAME", email,
                        "PASSWORD", password,
                        "SECRET_HASH", calculateSecretHash(clientId, clientSecret, email))
                )
                .build();
        InitiateAuthResponse initiateAuthResponse = this.cognitoIdentityProviderClient.initiateAuth(initiateAuthRequest);
        JsonObject loginUserResult = new JsonObject();
        loginUserResult.addProperty("isSuccess",initiateAuthResponse.sdkHttpResponse().isSuccessful());
        loginUserResult.addProperty("statusCode", initiateAuthResponse.sdkHttpResponse().statusCode());
        loginUserResult.addProperty("idToken", initiateAuthResponse.authenticationResult().idToken());
        loginUserResult.addProperty("token", initiateAuthResponse.authenticationResult().accessToken());
        loginUserResult.addProperty("refresh", initiateAuthResponse.authenticationResult().refreshToken());
        return loginUserResult;
    }


    public JsonObject addUserToGroup(String groupName, String username, String userPoolId){
        AdminAddUserToGroupRequest adminAddUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .groupName(groupName)
                .username(username)
                .userPoolId(userPoolId)
                .build();

        AdminAddUserToGroupResponse adminAddUserToGroupResponse = this.cognitoIdentityProviderClient
                .adminAddUserToGroup(adminAddUserToGroupRequest);
        JsonObject loginUserResult = new JsonObject();
        loginUserResult.addProperty("isSuccess",adminAddUserToGroupResponse.sdkHttpResponse().isSuccessful());
        loginUserResult.addProperty("statusCode", adminAddUserToGroupResponse.sdkHttpResponse().statusCode());

        return loginUserResult;
    }

    public JsonObject getUser(String accessToken){
        GetUserRequest getUserRequest = GetUserRequest.builder().accessToken(accessToken).build();
        GetUserResponse getUserResponse = this.cognitoIdentityProviderClient.getUser(getUserRequest);

        JsonObject getUserResult = new JsonObject();
        getUserResult.addProperty("isSuccess", getUserResponse.sdkHttpResponse().isSuccessful());
        getUserResult.addProperty("statusCode", getUserResponse.sdkHttpResponse().statusCode());

        JsonObject userAttrs = new JsonObject();
        getUserResponse.userAttributes().stream().forEach((attribute) -> {
            userAttrs.addProperty(attribute.name(), attribute.value());
        });

        getUserResult.add("user", userAttrs);

        return getUserResult;
    }

    private String calculateSecretHash(String userPoolClientId, String userPoolClientSecret, String userName) {
        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

        SecretKeySpec signingKey = new SecretKeySpec(
                userPoolClientSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(userName.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(userPoolClientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating ");
        }
    }

}
