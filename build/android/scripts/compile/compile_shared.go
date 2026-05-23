//go:build ignore

package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

func main() {
	arch := flag.String("arch", "arm64", "Target architecture: arm64 or amd64")
	ndkVersion := flag.String("ndk", "27.0.12077973", "NDK version to use")
	minSDK := flag.String("minsdk", "21", "Minimum Android SDK version")
	production := flag.Bool("production", false, "Production build (stripped, optimized)")
	flag.Parse()

	// Find Android NDK
	ndkRoot := os.Getenv("ANDROID_NDK_HOME")
	if ndkRoot == "" {
		androidHome := os.Getenv("ANDROID_HOME")
		if androidHome == "" {
			androidHome = os.Getenv("ANDROID_SDK_ROOT")
		}
		if androidHome == "" {
			// Default path for Windows
			home, _ := os.UserHomeDir()
			androidHome = filepath.Join(home, "AppData", "Local", "Android", "Sdk")
		}
		ndkRoot = filepath.Join(androidHome, "ndk", *ndkVersion)
	}

	if _, err := os.Stat(ndkRoot); os.IsNotExist(err) {
		fmt.Fprintf(os.Stderr, "Error: Android NDK not found at %s\n", ndkRoot)
		fmt.Fprintf(os.Stderr, "Please set ANDROID_NDK_HOME or install NDK %s via Android Studio\n", *ndkVersion)
		os.Exit(1)
	}
	fmt.Printf("Using NDK: %s\n", ndkRoot)

	// Determine host OS tag
	var hostTag string
	switch runtime.GOOS {
	case "darwin":
		hostTag = "darwin-x86_64"
	case "linux":
		hostTag = "linux-x86_64"
	case "windows":
		hostTag = "windows-x86_64"
	default:
		fmt.Fprintf(os.Stderr, "Unsupported host OS: %s\n", runtime.GOOS)
		os.Exit(1)
	}

	toolchain := filepath.Join(ndkRoot, "toolchains", "llvm", "prebuilt", hostTag)

	// Determine target architecture settings
	var cc, cxx, goArch, jniDir string
	// Extension for Windows clang wrapper scripts
	ext := ""
	if runtime.GOOS == "windows" {
		ext = ".cmd"
	}

	switch *arch {
	case "arm64":
		cc = filepath.Join(toolchain, "bin", fmt.Sprintf("aarch64-linux-android%s-clang%s", *minSDK, ext))
		cxx = filepath.Join(toolchain, "bin", fmt.Sprintf("aarch64-linux-android%s-clang++%s", *minSDK, ext))
		goArch = "arm64"
		jniDir = "arm64-v8a"
	case "amd64", "x86_64":
		cc = filepath.Join(toolchain, "bin", fmt.Sprintf("x86_64-linux-android%s-clang%s", *minSDK, ext))
		cxx = filepath.Join(toolchain, "bin", fmt.Sprintf("x86_64-linux-android%s-clang++%s", *minSDK, ext))
		goArch = "amd64"
		jniDir = "x86_64"
	default:
		fmt.Fprintf(os.Stderr, "Unsupported architecture: %s\n", *arch)
		os.Exit(1)
	}

	// Verify clang exists; try without extension if .cmd not found
	if _, err := os.Stat(cc); os.IsNotExist(err) && ext != "" {
		cc = cc[:len(cc)-len(ext)]
		cxx = cxx[:len(cxx)-len(ext)]
	}
	fmt.Printf("CC: %s\n", cc)

	// Create output directories
	jniLibsDir := filepath.Join("build", "android", "app", "src", "main", "jniLibs", jniDir)
	if err := os.MkdirAll(jniLibsDir, 0755); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to create jniLibs dir: %v\n", err)
		os.Exit(1)
	}
	if err := os.MkdirAll("bin", 0755); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to create bin dir: %v\n", err)
		os.Exit(1)
	}

	outputPath := filepath.Join(jniLibsDir, "libwails.so")

	// Build flags
	var buildFlags []string
	if *production {
		buildFlags = []string{
			"-buildmode=c-shared",
			"-tags", "production,android",
			"-trimpath",
			"-buildvcs=false",
			"-ldflags=-w -s",
			"-o", outputPath,
		}
	} else {
		buildFlags = []string{
			"-buildmode=c-shared",
			"-tags", "android,debug",
			"-buildvcs=false",
			"-gcflags=all=-l",
			"-o", outputPath,
		}
	}

	// Run go build
	cmd := exec.Command("go", append([]string{"build"}, buildFlags...)...)
	cmd.Env = append(os.Environ(),
		"CGO_ENABLED=1",
		"GOOS=android",
		"GOARCH="+goArch,
		"CC="+cc,
		"CXX="+cxx,
	)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	fmt.Printf("Building Go shared library for Android (%s)...\n", *arch)
	fmt.Printf("Output: %s\n", outputPath)

	if err := cmd.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Build failed: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("✓ Successfully built %s\n", outputPath)
}
