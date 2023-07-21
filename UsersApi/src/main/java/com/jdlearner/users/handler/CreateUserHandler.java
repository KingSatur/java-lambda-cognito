package com.jdlearner.users.handler;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.util.Base64;
import com.jdlearner.users.service.CognitoUserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.jdlearner.users.util.DecryptUtil;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Handler for requests to Lambda function.
 */
public class CreateUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoUserService cognitoUserService;
    private final String appClientId;
    private final String appClientSecret;
    private final Gson gson;

    public CreateUserHandler() {
        this.appClientId = DecryptUtil.decryptKey("APP_CLIENT_ID");
        this.appClientSecret = DecryptUtil.decryptKey("APP_CLIENT_SECRET");
        this.cognitoUserService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.gson = new Gson();
    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(Map.of("Content-Type", "application/json"));

        String requestBody = input.getBody();
        context.getLogger().log("Original json request body: " + requestBody);

        JsonObject userDetails = JsonParser.parseString(input.getBody()).getAsJsonObject();
        try {

            JsonObject useCreatedResponse = this.cognitoUserService.createUser(userDetails, appClientId,
                    appClientSecret);

            response
                    .withStatusCode(200)
                    .withBody(this.gson.toJson(useCreatedResponse, JsonObject.class));
        } catch (AwsServiceException e) {
            context.getLogger().log(e.awsErrorDetails().errorMessage());
            response.withStatusCode(500);
            response.withBody(e.awsErrorDetails().errorMessage());
        }
        return response;

    }

}
