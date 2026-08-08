// Configure webpack dev server to handle client-side routing
// This enables SPA routing for paths like /site/{documentId}
if (config.devServer) {
    config.devServer.historyApiFallback = true;
}
