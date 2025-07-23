#!/bin/bash

# Simple RAG Application Startup Script

echo "🚀 Starting Simple RAG Application..."
echo "=================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17+ and try again."
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven and try again."
    exit 1
fi

echo "✅ Prerequisites check passed!"

# Start PostgreSQL database
echo "🐘 Starting PostgreSQL database..."
docker-compose up -d

# Wait for database to be ready
echo "⏳ Waiting for database to be ready..."
sleep 10

# Check database connection
echo "🔍 Checking database connection..."
until docker-compose exec -T postgres pg_isready -U raguser -d simplerag > /dev/null 2>&1; do
    echo "   Database not ready yet, waiting..."
    sleep 5
done

echo "✅ Database is ready!"

# Build and run the application
echo "🏗️  Building application..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi

echo "✅ Build successful!"

# Create uploads directory
mkdir -p uploads

echo "🎯 Starting Spring Boot application..."
echo "📍 Application will be available at: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Run the application
mvn spring-boot:run
