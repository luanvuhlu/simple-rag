@echo off
setlocal

echo 🚀 Starting Simple RAG Application...
echo ==================================

:: Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not running. Please start Docker and try again.
    pause
    exit /b 1
)

:: Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java is not installed. Please install Java 17+ and try again.
    pause
    exit /b 1
)

:: Check if Maven is installed
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Maven is not installed. Please install Maven and try again.
    pause
    exit /b 1
)

echo ✅ Prerequisites check passed!

:: Start PostgreSQL database
echo 🐘 Starting PostgreSQL database...
docker-compose up -d

:: Wait for database to be ready
echo ⏳ Waiting for database to be ready...
timeout /t 10 /nobreak >nul

echo 🔍 Checking database connection...
:db_check
docker-compose exec -T postgres pg_isready -U raguser -d simplerag >nul 2>&1
if %errorlevel% neq 0 (
    echo    Database not ready yet, waiting...
    timeout /t 5 /nobreak >nul
    goto db_check
)

echo ✅ Database is ready!

:: Build and run the application
echo 🏗️  Building application...
mvn clean compile -q

if %errorlevel% neq 0 (
    echo ❌ Build failed. Please check the errors above.
    pause
    exit /b 1
)

echo ✅ Build successful!

:: Create uploads directory
if not exist "uploads" mkdir uploads

echo 🎯 Starting Spring Boot application...
echo 📍 Application will be available at: http://localhost:8080
echo.
echo Press Ctrl+C to stop the application
echo.

:: Run the application
mvn spring-boot:run

pause
