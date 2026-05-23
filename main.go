package main

import (
	"embed"
	_ "embed"
	"log"
	

	"godownloader/backend"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// Wails uses Go's `embed` package to embed the frontend files into the binary.
// Any files in the frontend/dist folder will be embedded into the binary and
// made available to the frontend.
// See https://pkg.go.dev/embed for more information.

//go:embed all:frontend/dist
var assets embed.FS

func init() {
	// Register a custom event whose associated data type is string.
	// This is not required, but the binding generator will pick up registered events
	// and provide a strongly typed JS/TS API for them.
	application.RegisterEvent[string]("time")
}

// main function serves as the application's entry point. It initializes the application, creates a window,
// and starts a goroutine that emits a time-based event every second. It subsequently runs the application and
// logs any error that might occur.
func main() {

	// Initialize DownloadManager with the app instance
	dm := backend.NewDownloadManager(nil) // Temporarily pass nil, we'll set it later if needed

	// Create a new Wails application by providing the necessary options.
	app := application.New(application.Options{
		Name:        "Go Downloader",
		Description: "A professional download manager built with Wails v3",
		Services: []application.Service{
			application.NewService(&GreetService{}),
			application.NewService(dm),
		},
		Assets: application.AssetOptions{
			Handler: application.AssetFileServerFS(assets),
		},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: true,
		},
	})
	
	// Set the app instance in dm
	dm.SetApp(app)

	// Create a new window
	app.Window.NewWithOptions(application.WebviewWindowOptions{
		Title: "Go Downloader",
		Mac: application.MacWindow{
			InvisibleTitleBarHeight: 50,
			Backdrop:                application.MacBackdropTranslucent,
			TitleBar:                application.MacTitleBarHiddenInset,
		},
		BackgroundColour: application.NewRGB(15, 23, 42), // Slate 900
		URL:              "/",
	})

	// Run the application
	err := app.Run()

	if err != nil {
		log.Fatal(err)
	}
}
