package com.flowboard;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * AWS Lambda Handler for FlowBoard Backend.
 * 
 * This handler initializes the Spring Boot application once and reuses it
 * across Lambda invocations for optimal cold start performance.
 * 
 * Usage:
 * - Handler: com.flowboard.LambdaHandler
 * - Runtime: java21
 * - Memory: 1024MB+ (higher memory = faster CPU = lower latency)
 * - Timeout: 29 seconds (API Gateway max)
 * 
 * Environment Variables Required:
 * - DB_URL: JDBC URL for PostgreSQL (e.g., jdbc:postgresql://host:5432/flowboard)
 * - DB_USERNAME: Database username
 * - DB_PASSWORD: Database password
 * - JWT_SECRET: Secret key for JWT signing (min 32 characters)
 * - CORS_ALLOWED_ORIGINS: Allowed CORS origins (e.g., https://yourdomain.com)
 */
public class LambdaHandler implements RequestStreamHandler {

    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        try {
            // Initialize Spring Boot application once
            // This is reused across all Lambda invocations
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(FlowBoardApplication.class);
            
        } catch (Exception e) {
            // Log to stderr since Lambda logging isn't initialized yet
            System.err.println("FATAL: Failed to initialize Spring Boot handler");
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot handler", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        try {
            handler.proxyStream(inputStream, outputStream, context);
        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            
            // Return a proper error response
            AwsProxyResponse errorResponse = new AwsProxyResponse();
            errorResponse.setStatusCode(500);
            errorResponse.setBody("{\"error\":\"Internal Server Error\",\"message\":\"" + e.getMessage() + "\"}");
            objectMapper.writeValue(outputStream, errorResponse);
        }
    }
}
