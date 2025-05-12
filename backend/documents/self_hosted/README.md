# Writeopia Self-Hosted Backend

This module provides a self-hosted backend for the Writeopia app. It allows you to run your own server to store and sync your notes, providing full control over your data.

## Features

- REST API for syncing notes
- Local storage of documents
- Simple configuration
- Docker support for easy deployment

## Getting Started

### Using Docker (Recommended)

The easiest way to run the Writeopia self-hosted backend is using Docker and docker-compose:

1. Clone the repository
2. Navigate to the `backend/documents/self_hosted` directory
3. Run `docker-compose up -d`

The server will be available at `http://localhost:8080`

### Manual Setup

If you prefer to run the server directly:

1. Build the JAR file:
   ```
   ./gradlew :backend:documents:self_hosted:shadowJar
   ```

2. Run the JAR file:
   ```
   java -jar backend/documents/self_hosted/build/libs/writeopia-selfhosted.jar
   ```

## Configuration

The self-hosted backend will automatically create a configuration file at `~/.writeopia/writeopia-config.json`. You can modify this file to change the port, database location, and other settings:

```json
{
  "port": 8080,
  "databasePath": "/path/to/database",
  "debug": false
}
```

## Connecting the App

To connect your Writeopia app to your self-hosted backend:

1. In the Writeopia app, go to the Notes menu
2. Click on the menu icon and select "Configure Directory"
3. Enter the URL of your self-hosted backend (e.g., `http://localhost:8080`)
4. Click "Sync" to sync your notes with the backend

## API Endpoints

The self-hosted backend provides the following API endpoints:

- `GET /api/self-hosted/status` - Check the server status
- `GET /api/self-hosted/info` - Get server information
- `POST /api/document` - Upload documents
- `GET /api/document/{id}` - Get a document by ID
- `POST /api/document/folder/diff` - Get document differences for a folder

## Development

To work on the self-hosted backend, import the project into your IDE and run the `Application.kt` file directly. This will start the server using the default configuration.
