#!/bin/bash

echo "Building Event-Driven Architecture POC Services..."

# Build API Gateway
echo "Building API Gateway..."
cd api-gateway
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Failed to build API Gateway"
    exit 1
fi
cd ..

# Build Order Service
echo "Building Order Service..."
cd order-service
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Failed to build Order Service"
    exit 1
fi
cd ..

# Build Payment Service
echo "Building Payment Service..."
cd payment-service
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Failed to build Payment Service"
    exit 1
fi
cd ..

# Build Order Gateway
echo "Building Order Gateway..."
cd order-gateway
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Failed to build Order Gateway"
    exit 1
fi
cd ..

echo "All services built successfully!"
echo "You can now run: docker-compose up -d"
