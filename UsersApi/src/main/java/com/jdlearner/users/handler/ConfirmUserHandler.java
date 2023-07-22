package com.jdlearner.users.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jdlearner.users.dto.ErrorResponse;
import com.jdlearner.users.service.CognitoUserService;
import com.jdlearner.users.util.DecryptUtil;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

public class ConfirmUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final String appClientId;
    private final String appClientSecret;
    private final CognitoUserService cognitoUserService;
    private final Gson gson;

    public ConfirmUserHandler() {
        this.gson = new Gson();
        this.appClientId = DecryptUtil.decryptKey("APP_CLIENT_ID");
        this.appClientSecret = DecryptUtil.decryptKey("APP_CLIENT_SECRET");
        this.cognitoUserService = new CognitoUserService(System.getenv("AWS_REGION"));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        JsonObject userDetails = JsonParser.parseString(input.getBody()).getAsJsonObject();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            JsonObject confirmUserResponse = this.cognitoUserService.confirmUser(
                    this.appClientId,
                    this.appClientSecret,
                    userDetails.get("email").getAsString(),
                    userDetails.get("code").getAsString());
            response.withStatusCode(200);
            response.withBody(this.gson.toJson(confirmUserResponse, JsonObject.class));
        } catch (AwsServiceException e) {
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage());
            context.getLogger().log(e.awsErrorDetails().errorMessage());
            response.withStatusCode(500);
            response.withBody(this.gson.newBuilder().serializeNulls().create()
                    .toJson(errorResponse, ErrorResponse.class));
        } catch (Exception e) {
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage());
            context.getLogger().log(e.getMessage());
            response.withBody(this.gson.newBuilder().serializeNulls().create()
                    .toJson(errorResponse, ErrorResponse.class));
            response.withStatusCode(500);
        }
        return response;
    }
}
