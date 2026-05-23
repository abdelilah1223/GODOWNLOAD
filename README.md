# Go Downloader (GoDownload)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Go Version](https://img.shields.io/github/go-mod/go-version/abdelilah1223/GODOWNLOAD?color=00ADD8&logo=go)](https://go.dev/)
[![Wails Version](https://img.shields.io/badge/Wails-v3-blue?style=flat&logo=wails)](https://v3.wails.io/)
[![React](https://img.shields.io/badge/Frontend-React%20%2B%20Vite%20%2B%20Tailwind-blue?logo=react)](https://react.dev/)

Go Downloader is a high-performance, modern, and professional desktop download manager. Built with a robust backend in Go (Wails v3) and a responsive frontend in React, Vite, and Tailwind CSS, it is designed to maximize download speeds while offering a unified, clean user experience across Windows, macOS, and Linux.

---

## Downloads and Repository

*   **Pre-compiled Executable (EXE/App/Binary):** [Download Windows EXE & App Releases](https://abdelilah.kesug.com/archive/apps/)
*   **Official Git Repository:** [github.com/abdelilah1223/GODOWNLOAD](https://github.com/abdelilah1223/GODOWNLOAD.git)

---

## Features

-   **Multi-Threaded Segmented Downloading**: Speeds up downloads by splitting files into multiple segments (threads) and downloading them concurrently, maximizing bandwidth utilization.
-   **Intelligent Concurrent Queue**: Enforces a maximum limit on concurrent active downloads. Excess downloads are queued automatically and run sequentially as active ones finish.
-   **Pause & Resume Control**: Seamlessly pause active downloads and resume them later from where they left off. No progress is lost.
-   **Native Proxy Configuration**: Integrates HTTP proxy settings directly into the application for secure or restricted downloading.
-   **File Explorer Integration**: Open the download folder directly or highlight/select the downloaded file in Windows Explorer, macOS Finder, or Linux File Managers with a single click.
-   **Real-time Telemetry and Metrics**:
    -   Detailed per-part speed, status, and progress reporting.
    -   Global speed metrics (MB/s) and visual progress bars.
    -   Accurate ETA (Estimated Time of Arrival) calculation.
-   **Auto-Save & Persistent State**: Application configuration, download lists, and part progress are continuously saved to a local state JSON file, ensuring everything is restored exactly as it was upon restart.
-   **Localization Support**: Multilingual UI supporting both English (LTR) and Arabic (RTL) with appropriate alignment and formatting.

---

## Codebase Structure

The project follows a clean separation of concerns, decoupling Go system-level operations from the React user interface:

```text
GoDownloader/
├── main.go                # Application entrypoint. Configures Wails v3, setups window/styling, and boots services.
├── greetservice.go        # Standard template service binding.
├── backend/               # Core Go Backend
│   └── downloader.go      # Segmented download engine, queue worker, configuration managers, and OS-specific functions.
├── frontend/              # Modern React Frontend
│   ├── src/
│   │   ├── App.jsx        # Premium dashboard UI with status tabs, download forms, speedometers, and settings.
│   │   ├── main.jsx       # React application mounting point.
│   │   ├── index.css      # Core Tailwind CSS directives and custom scrollbar styles.
│   │   ├── i18n.js        # Localization system config (i18next).
│   │   └── locales/       # Language translation dictionaries
│   │       ├── en.json    # English UI translations.
│   │       └── ar.json    # Arabic UI translations (RTL aligned).
│   ├── package.json       # Node.js dependencies (Lucide React, Tailwind, Vite, Wails Runtime).
│   └── vite.config.js     # React/Vite development server configuration.
├── build/                 # Custom build assets, application icons, and packaging configs.
├── wails-patch/           # Custom local patches for Wails framework compilation.
└── Taskfile.yml           # Automated task runner for building, running dev instances, and packaging.
```

---

## Deep Dive into the Backend (backend/downloader.go)

-   **DownloadManager**: The orchestrator maintaining the configuration (Config), task list (Tasks), and executing active workers (runningTasks).
-   **queueWorker()**: A background ticker routine running every 2 seconds to automatically spin up queued downloads while respecting MaxConcurrentDownloads.
-   **startDownload()**: Manages the download lifecycle. It initiates Head requests to obtain content length, invokes createParts() to compute ranges, creates/truncates the local target file, starts concurrent part downloaders, and monitors real-time telemetry.
-   **downloadPart()**: The worker goroutine that makes segmented GET requests using the HTTP Range header (bytes=start-end). It reads stream chunks using optimized buffer sizes (512KB) and writes directly to specific file offsets using Seek.
-   **OpenFolder()**: Utilizes native OS commands (explorer /select,, open -R, or xdg-open) to navigate users straight to their files in the system's native file explorer.

---

## How to Clone, Run, and Build

### Prerequisites

To run or compile Go Downloader from source code, ensure you have the following installed:

1.  **Go** (version 1.21 or higher) -> [Install Go](https://go.dev/doc/install)
2.  **Node.js** (version 18 or higher) & **npm** -> [Install Node.js](https://nodejs.org/)
3.  **Wails v3 CLI** -> Follow the official [Wails v3 installation guide](https://v3.wails.io/go-getting-started/installation).

### 1. Clone the Repository
```bash
git clone https://github.com/abdelilah1223/GODOWNLOAD.git
cd GODOWNLOAD
```

### 2. Run in Development Mode
To start hot-reloading for both backend Go code and frontend React code, run:
```bash
wails3 dev
```
Wails will compile the Go backend, install frontend dependencies, host the Vite server, and launch a native OS window container.

### 3. Build Production Executable
To package a highly-optimized, compressed native executable for your current platform, run:
```bash
wails3 build
```
The compiled, production-ready desktop executable will be created in the build/ or bin/ directory.

---

## Future Roadmap (Recommended Features)

Planned features for future updates include:

*   **Browser Extensions**: Direct integration with Google Chrome, Mozilla Firefox, and Microsoft Edge to automatically intercept and redirect file downloads into Go Downloader.
*   **Speed Limiter**: Global and per-task bandwidth throttling controls to prevent Go Downloader from consuming the entire internet connection.
*   **Scheduled Downloads**: A scheduling calendar to start, pause, or throttle downloads during off-peak hours (e.g., overnight).
*   **Browser cookie / Credential capturing**: Automatic capturing of session cookies, user agents, and authorization headers to download files behind logins or paywalls.
*   **Batch URL Downloader**: Paste a list of URLs or scan a page to download dozens of files simultaneously.
*   **Smart File Categorization**: Automatic sorting of finished downloads into respective subdirectories (e.g., /Videos, /Music, /Documents, /Compressed) based on file extension.
*   **BitTorrent & Magnet Protocol**: Integrating a lightweight, native torrent client to download peer-to-peer torrent files directly within the same UI.

---

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---
Created using Wails v3 and React.
