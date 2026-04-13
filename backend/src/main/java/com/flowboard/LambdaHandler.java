package com.flowboard;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            // Explicitly register PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            
            // Create the handler using the standard factory method
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(FlowBoardApplication.class);
            
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize Lambda handler: " + e.getMessage(), e);
        }
    }

    @Override
    public AwsProxyResponse handleRequest(AwsProxyRequest input, Context context) {
        return handler.proxy(input, context);
    }
}
