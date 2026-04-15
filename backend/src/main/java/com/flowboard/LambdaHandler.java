package com.flowboard;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaHandler implements RequestHandler<AwsProxyRequest, AwsProxyResponse> {

    static {
        try {
            // Explicitly register PostgreSQL driver before handler initialization
            Class.forName("org.postgresql.Driver");

            // Force the lambda profile via system property before Spring Boot starts
            System.setProperty("spring.profiles.active", "lambda");

            // Use custom handler that works around Spring Boot 3.2 context creation issue
            FixedSpringBootLambdaContainerHandler fixedHandler = new FixedSpringBootLambdaContainerHandler();
            fixedHandler.activateSpringProfiles("lambda");
            new com.amazonaws.serverless.proxy.InitializationWrapper().start(fixedHandler);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize Lambda handler: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AwsProxyResponse handleRequest(AwsProxyRequest input, Context context) {
        SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler =
                (SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse>) SpringBootLambdaContainerHandler.getInstance();
        if (handler == null) {
            throw new IllegalStateException("SpringBootLambdaContainerHandler has not been initialized");
        }
        return handler.proxy(input, context);
    }
}
