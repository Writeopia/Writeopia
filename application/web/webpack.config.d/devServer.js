// Configure webpack dev server to handle client-side routing
// This enables SPA routing for paths like /site/{documentId}
if (config.devServer) {
    config.devServer.historyApiFallback = true;

    // Proxy API requests to avoid CORS issues during development
    // Requests to /api/* will be forwarded to the backend server
    //
    // To use this proxy:
    // 1. In MainWeb.kt, set the base URL to empty string:
    //    WriteopiaConnectionInjector.setBaseUrl("")
    // 2. Run the webapp: ./gradlew :application:web:wasmJsBrowserDevelopmentRun
    // 3. API calls will be proxied: /api/* -> https://writeopia.io/api/*
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'https://writeopia.io',
            changeOrigin: true,
            secure: true
        }
    ];
}
