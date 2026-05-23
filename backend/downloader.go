package backend

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"sync"
	"time"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type Status string

const (
	StatusQueued      Status = "Queued"
	StatusDownloading Status = "Downloading"
	StatusPaused      Status = "Paused"
	StatusCompleted   Status = "Completed"
	StatusError       Status = "Error"
)

type Part struct {
	ID         int     `json:"id"`
	Start      int64   `json:"start"`
	End        int64   `json:"end"`
	Downloaded int64   `json:"downloaded"`
	Speed      float64 `json:"speed"` // MB/s
	Status     Status  `json:"status"`
}

type DownloadTask struct {
	ID           string    `json:"id"`
	URL          string    `json:"url"`
	FileName     string    `json:"file_name"`
	FilePath     string    `json:"file_path"`
	Size         int64     `json:"size"`
	Downloaded   int64     `json:"downloaded"`
	Progress     float64   `json:"progress"`
	Speed        float64   `json:"speed"` // MB/s
	ETA          string    `json:"eta"`
	Status       Status    `json:"status"`
	Threads      int       `json:"threads"`
	CreatedAt    time.Time `json:"created_at"`
	Parts        []Part    `json:"parts"`
	ErrorMessage string    `json:"error_message"`

	cancelFunc context.CancelFunc
}

type Config struct {
	DownloadDir            string  `json:"download_dir"`
	MetaDir                string  `json:"meta_dir"`
	MaxThreads             int     `json:"max_threads"`
	MaxConcurrentDownloads int     `json:"max_concurrent_downloads"`
	ProxyEnabled           bool    `json:"proxy_enabled"`
	ProxyURL               string  `json:"proxy_url"`
}

type DownloadManager struct {
	Config       Config
	Tasks        map[string]*DownloadTask
	runningTasks map[string]bool
	mutex        sync.RWMutex
	app          *application.App
}

func NewDownloadManager(app *application.App) *DownloadManager {
	var defaultDownloadDir string
	home, _ := os.UserHomeDir()

	switch runtime.GOOS {
	case "android":
		defaultDownloadDir = "/storage/emulated/0/Download/godownload"
	case "windows":
		defaultDownloadDir = filepath.Join(home, "Downloads", "godownload")
	default:
		defaultDownloadDir = filepath.Join(home, "Downloads", "Go Downloader")
	}

	defaultMetaDir := filepath.Join(home, ".godownloader", "meta")
	if runtime.GOOS == "android" {
		defaultMetaDir = "/storage/emulated/0/Download/godownload/.meta"
	}

	os.MkdirAll(defaultDownloadDir, 0755)
	os.MkdirAll(defaultMetaDir, 0755)

	dm := &DownloadManager{
		Config: Config{
			DownloadDir:            defaultDownloadDir,
			MetaDir:                defaultMetaDir,
			MaxThreads:             8,
			MaxConcurrentDownloads: 3,
			ProxyEnabled:           false,
		},
		Tasks:        make(map[string]*DownloadTask),
		runningTasks: make(map[string]bool),
		app:          app,
	}

	dm.loadState()
	go dm.queueWorker()
	return dm
}

func (dm *DownloadManager) SetApp(app *application.App) {
	dm.mutex.Lock()
	dm.app = app
	dm.mutex.Unlock()
}

// --- Wails Exposed Methods ---

func (dm *DownloadManager) GetTasks() []*DownloadTask {
	dm.mutex.RLock()
	defer dm.mutex.RUnlock()

	var tasks []*DownloadTask
	for _, t := range dm.Tasks {
		tasks = append(tasks, t)
	}
	return tasks
}

func (dm *DownloadManager) AddFile(targetURL string, startNow bool) string {
	fileName := extractFileName(targetURL)
	id := fmt.Sprintf("%x", time.Now().UnixNano())

	task := &DownloadTask{
		ID:        id,
		URL:       targetURL,
		FileName:  fileName,
		FilePath:  filepath.Join(dm.Config.DownloadDir, fileName),
		Status:    StatusQueued,
		CreatedAt: time.Now(),
		Threads:   dm.Config.MaxThreads,
	}

	dm.mutex.Lock()
	dm.Tasks[id] = task
	if startNow {
		task.Status = StatusQueued // Will be picked up by worker
	} else {
		task.Status = StatusPaused // Just add to queue
	}
	dm.mutex.Unlock()

	dm.saveState()
	return id
}

func (dm *DownloadManager) PauseTask(id string) {
	dm.mutex.Lock()
	task, ok := dm.Tasks[id]
	if !ok {
		dm.mutex.Unlock()
		return
	}

	if task.Status == StatusDownloading || task.Status == StatusQueued {
		if task.cancelFunc != nil {
			task.cancelFunc()
		}
		task.Status = StatusPaused
		task.Speed = 0
		task.ETA = ""
		delete(dm.runningTasks, id)
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) ResumeTask(id string) {
	dm.mutex.Lock()
	task, ok := dm.Tasks[id]
	if ok && (task.Status == StatusPaused || task.Status == StatusError) {
		task.Status = StatusQueued
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) DeleteTask(id string, removeFile bool) {
	dm.mutex.Lock()
	task, ok := dm.Tasks[id]
	if ok {
		if task.cancelFunc != nil {
			task.cancelFunc()
		}
		if removeFile {
			os.Remove(task.FilePath)
		}
		delete(dm.Tasks, id)
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) StartAll() {
	dm.mutex.Lock()
	for _, t := range dm.Tasks {
		if t.Status == StatusPaused || t.Status == StatusError {
			t.Status = StatusQueued
		}
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) StopAll() {
	dm.mutex.Lock()
	for id, t := range dm.Tasks {
		if t.Status == StatusDownloading || t.Status == StatusQueued {
			if t.cancelFunc != nil {
				t.cancelFunc()
			}
			t.Status = StatusPaused
			t.Speed = 0
			t.ETA = ""
			delete(dm.runningTasks, id)
		}
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) UpdateConfig(config Config) {
	dm.mutex.Lock()
	dm.Config = config
	dm.mutex.Unlock()
	os.MkdirAll(config.DownloadDir, 0755)
	dm.saveState()
}

func (dm *DownloadManager) GetConfig() Config {
	dm.mutex.RLock()
	defer dm.mutex.RUnlock()
	return dm.Config
}

func (dm *DownloadManager) OpenFolder(id string) {
	dm.mutex.RLock()
	task, ok := dm.Tasks[id]
	dm.mutex.RUnlock()

	if !ok {
		return
	}

	dir := filepath.Dir(task.FilePath)
	var cmd *exec.Cmd

	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", "/select,", task.FilePath)
	case "darwin":
		cmd = exec.Command("open", "-R", task.FilePath)
	default: // linux
		cmd = exec.Command("xdg-open", dir)
	}
	cmd.Run()
}

func (dm *DownloadManager) OpenDownloadsFolder() {
	dm.mutex.RLock()
	dir := dm.Config.DownloadDir
	dm.mutex.RUnlock()

	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", dir)
	case "darwin":
		cmd = exec.Command("open", dir)
	default:
		cmd = exec.Command("xdg-open", dir)
	}
	cmd.Run()
}

// --- Internal Implementation ---

func (dm *DownloadManager) getClient() *http.Client {
	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		// Performance optimizations
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		DisableCompression:    true, // Often faster for pre-compressed downloads
	}

	if dm.Config.ProxyEnabled && dm.Config.ProxyURL != "" {
		proxyURL, err := url.Parse(dm.Config.ProxyURL)
		if err == nil {
			transport.Proxy = http.ProxyURL(proxyURL)
		}
	}

	return &http.Client{
		Transport: transport,
		Timeout:   0, // No timeout for downloads
	}
}

func (dm *DownloadManager) queueWorker() {
	ticker := time.NewTicker(2 * time.Second)
	for range ticker.C {
		dm.mutex.Lock()
		// Clean finished tasks from runningTasks
		for id := range dm.runningTasks {
			if task, ok := dm.Tasks[id]; !ok || task.Status != StatusDownloading {
				delete(dm.runningTasks, id)
			}
		}

		// Pick new tasks
		if len(dm.runningTasks) < dm.Config.MaxConcurrentDownloads {
			for id, task := range dm.Tasks {
				if task.Status == StatusQueued && !dm.runningTasks[id] {
					dm.runningTasks[id] = true
					go dm.startDownload(id)
					if len(dm.runningTasks) >= dm.Config.MaxConcurrentDownloads {
						break
					}
				}
			}
		}
		dm.mutex.Unlock()
	}
}

func (dm *DownloadManager) startDownload(id string) {
	dm.mutex.Lock()
	task, ok := dm.Tasks[id]
	if !ok {
		dm.mutex.Unlock()
		return
	}

	// Double check to avoid multiple goroutines
	if task.Status == StatusDownloading {
		dm.mutex.Unlock()
		return
	}

	if task.Status == StatusQueued {
		task.Status = StatusDownloading
	} else if task.Status != StatusDownloading {
		dm.mutex.Unlock()
		return
	}

	ctx, cancel := context.WithCancel(context.Background())
	task.cancelFunc = cancel
	task.ErrorMessage = ""
	dm.mutex.Unlock()

	client := dm.getClient()

	// 1. Get File Info if not present
	if task.Size == 0 {
		resp, err := client.Head(task.URL)
		if err != nil {
			dm.failTask(id, err.Error())
			return
		}
		resp.Body.Close()

		size, _ := strconv.ParseInt(resp.Header.Get("Content-Length"), 10, 64)
		task.Size = size

		dm.mutex.Lock()
		if len(task.Parts) == 0 {
			task.Parts = createParts(size, task.Threads)
		}
		dm.mutex.Unlock()
	}

	// 2. Prepare file
	os.MkdirAll(filepath.Dir(task.FilePath), 0755)
	file, err := os.OpenFile(task.FilePath, os.O_CREATE|os.O_RDWR, 0644)
	if err != nil {
		dm.failTask(id, err.Error())
		return
	}
	if task.Size > 0 {
		file.Truncate(task.Size)
	}
	file.Close()

	// 3. Start workers
	var wg sync.WaitGroup
	startTime := time.Now()
	
	dm.mutex.RLock()
	partsCount := len(task.Parts)
	lastDownloaded := task.Downloaded
	dm.mutex.RUnlock()

	for i := 0; i < partsCount; i++ {
		dm.mutex.RLock()
		p := task.Parts[i]
		dm.mutex.RUnlock()

		if p.Downloaded >= (p.End-p.Start+1) && p.End != 0 {
			continue
		}
		wg.Add(1)
		go dm.downloadPart(ctx, id, i, &wg, startTime)
	}

	// Progress Monitoring
	monitorCtx, monitorCancel := context.WithCancel(ctx)
	defer monitorCancel()
	
	go func() {
		ticker := time.NewTicker(1 * time.Second)
		defer ticker.Stop()
		
		// Track movement per part
		lastPartDownloaded := make([]int64, partsCount)
		dm.mutex.RLock()
		for i := 0; i < partsCount; i++ {
			lastPartDownloaded[i] = task.Parts[i].Downloaded
		}
		dm.mutex.RUnlock()

		for {
			select {
			case <-monitorCtx.Done():
				return
			case <-ticker.C:
				dm.mutex.Lock()
				t, ok := dm.Tasks[id]
				if !ok || t.Status != StatusDownloading {
					dm.mutex.Unlock()
					return
				}

				totalNewDownloaded := int64(0)
				for i := range t.Parts {
					p := &t.Parts[i]
					partNew := p.Downloaded
					diff := partNew - lastPartDownloaded[i]
					p.Speed = float64(diff) / (1024 * 1024)
					lastPartDownloaded[i] = partNew
					totalNewDownloaded += partNew
				}

				speed := float64(totalNewDownloaded-lastDownloaded) / (1024 * 1024)
				t.Downloaded = totalNewDownloaded
				t.Speed = speed
				if t.Size > 0 {
					t.Progress = (float64(totalNewDownloaded) / float64(t.Size)) * 100
					if speed > 0 {
						remSecs := float64(t.Size-totalNewDownloaded) / (speed * 1024 * 1024)
						t.ETA = time.Duration(remSecs * float64(time.Second)).Round(time.Second).String()
					}
				}
				lastDownloaded = totalNewDownloaded
				dm.mutex.Unlock()
				dm.saveState()
			}
		}
	}()

	wg.Wait()

	dm.mutex.Lock()
	if task, ok = dm.Tasks[id]; ok && task.Status == StatusDownloading {
		task.Status = StatusCompleted
		task.Progress = 100
		task.ETA = "Finished"
		task.Speed = 0
		delete(dm.runningTasks, id)
	}
	dm.mutex.Unlock()
	dm.saveState()
}

func (dm *DownloadManager) downloadPart(ctx context.Context, taskID string, partIdx int, wg *sync.WaitGroup, startTime time.Time) {
	defer wg.Done()

	for retry := 0; retry < 5; retry++ {
		select {
		case <-ctx.Done():
			return
		default:
		}

		dm.mutex.RLock()
		task, ok := dm.Tasks[taskID]
		if !ok {
			dm.mutex.RUnlock()
			return
		}
		part := &task.Parts[partIdx]
		targetURL := task.URL
		filePath := task.FilePath
		currentStart := part.Start + part.Downloaded
		dm.mutex.RUnlock()

		if currentStart > part.End && part.End != 0 {
			return
		}

		dm.mutex.Lock()
		// Re-check existence and state while locking
		if t, ok := dm.Tasks[taskID]; ok && t.Status == StatusDownloading {
			t.Parts[partIdx].Status = StatusDownloading
		}
		dm.mutex.Unlock()

		req, _ := http.NewRequestWithContext(ctx, "GET", targetURL, nil)
		if part.End > 0 {
			req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", currentStart, part.End))
		}

		client := dm.getClient()
		resp, err := client.Do(req)
		if err != nil {
			dm.mutex.Lock()
			if t, ok := dm.Tasks[taskID]; ok {
				t.Parts[partIdx].Status = StatusError
			}
			dm.mutex.Unlock()
			time.Sleep(2 * time.Second)
			continue
		}

		file, err := os.OpenFile(filePath, os.O_WRONLY, 0644)
		if err != nil {
			resp.Body.Close()
			return
		}
		file.Seek(currentStart, 0)

		// Optimization: Use a larger buffer for faster downloads (512KB)
		buffer := make([]byte, 512*1024) 
		for {
			n, err := resp.Body.Read(buffer)
			if n > 0 {
				file.Write(buffer[:n])
				dm.mutex.Lock()
				if t, ok := dm.Tasks[taskID]; ok && t.Status == StatusDownloading {
					t.Parts[partIdx].Downloaded += int64(n)
				}
				dm.mutex.Unlock()
			}

			// Check cancellation after each read
			select {
			case <-ctx.Done():
				dm.mutex.Lock()
				if t, ok := dm.Tasks[taskID]; ok {
					t.Parts[partIdx].Status = StatusPaused
					t.Parts[partIdx].Speed = 0
				}
				dm.mutex.Unlock()
				file.Close()
				resp.Body.Close()
				return
			default:
			}

			if err != nil {
				file.Close()
				resp.Body.Close()
				if err == io.EOF {
					dm.mutex.Lock()
					if t, ok := dm.Tasks[taskID]; ok {
						t.Parts[partIdx].Status = StatusCompleted
						t.Parts[partIdx].Speed = 0
					}
					dm.mutex.Unlock()
					return
				}
				goto retry_label
			}
		}
	retry_label:
		time.Sleep(2 * time.Second)
	}
}

func (dm *DownloadManager) failTask(id string, msg string) {
	dm.mutex.Lock()
	if task, ok := dm.Tasks[id]; ok {
		task.Status = StatusError
		task.ErrorMessage = msg
		task.Speed = 0
	}
	dm.mutex.Unlock()
	dm.saveState()
}

// --- State Helpers ---

func (dm *DownloadManager) loadState() {
	path := filepath.Join(dm.Config.MetaDir, "state.json")
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	
	var state struct {
		Config Config                   `json:"config"`
		Tasks  map[string]*DownloadTask `json:"tasks"`
	}
	if err := json.Unmarshal(data, &state); err == nil {
		dm.Config = state.Config
		dm.Tasks = state.Tasks

		// Ensure defaults if missing from old config
		if dm.Config.MaxThreads <= 0 {
			dm.Config.MaxThreads = 8
		}
		if dm.Config.MaxConcurrentDownloads <= 0 {
			dm.Config.MaxConcurrentDownloads = 3
		}

		// Reset transient states
		for _, t := range dm.Tasks {
			if t.Status == StatusDownloading {
				t.Status = StatusPaused
			}
			t.Speed = 0
			t.ETA = ""
		}
	}
}

func (dm *DownloadManager) saveState() {
	dm.mutex.RLock()
	defer dm.mutex.RUnlock()

	path := filepath.Join(dm.Config.MetaDir, "state.json")
	state := struct {
		Config Config                   `json:"config"`
		Tasks  map[string]*DownloadTask `json:"tasks"`
	}{
		Config: dm.Config,
		Tasks:  dm.Tasks,
	}

	data, _ := json.MarshalIndent(state, "", "  ")
	os.WriteFile(path, data, 0644)
}

func createParts(size int64, threads int) []Part {
	if size <= 0 {
		return []Part{{ID: 0, Start: 0, End: 0, Downloaded: 0, Status: StatusQueued}}
	}
	partSize := size / int64(threads)
	var parts []Part
	for i := 0; i < threads; i++ {
		start := int64(i) * partSize
		end := start + partSize - 1
		if i == threads-1 {
			end = size - 1
		}
		parts = append(parts, Part{
			ID:         i,
			Start:      start,
			End:        end,
			Downloaded: 0,
			Status:     StatusQueued,
		})
	}
	return parts
}

func extractFileName(urlStr string) string {
	u, err := url.Parse(urlStr)
	if err != nil {
		return "unknown_file"
	}
	name := filepath.Base(u.Path)
	if name == "." || name == "/" {
		return "download_" + strconv.FormatInt(time.Now().Unix(), 10)
	}
	return name
}