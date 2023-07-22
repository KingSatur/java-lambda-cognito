package com.jdlearner.users.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
import software.amazon.awssdk.utils.Logger;

import java.util.Map;

public class GetUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoUserService cognitoUserService;
    private final Gson gson;

    public GetUserHandler() {
        this.cognitoUserService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(Map.of("Content-Type", "application/json"));
        LambdaLogger logger = context.getLogger();
        try {
            JsonObject getUserResponse = this.cognitoUserService.getUser(input.getHeaders().get("AccessToken"));
            response
                    .withStatusCode(201)
                    .withBody(this.gson.toJson(getUserResponse, JsonObject.class));
        } catch (AwsServiceException e) {
            context.getLogger().log(e.awsErrorDetails().errorMessage());
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage());
            response.withStatusCode(500);
            response.withBody(this.gson.newBuilder().serializeNulls().create()
                    .toJson(errorResponse, ErrorResponse.class));
        }
        return response;
    }
}
