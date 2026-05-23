import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import * as backend from "../bindings/godownloader/backend/downloadmanager";
import {
  LayoutDashboard,
  Download,
  Settings,
  Pause,
  Play,
  Trash2,
  FolderOpen,
  Plus,
  Clock,
  Zap,
  AlertCircle,
  HardDrive,
  Globe,
  Monitor,
  Menu,
  X,
  ChevronDown,
  Gauge,
  Network,
  Cpu,
  Activity,
  Sun,
  Moon,
  Languages,
} from "lucide-react";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";

function cn(...inputs) {
  return twMerge(clsx(inputs));
}

const App = () => {
  const { t, i18n } = useTranslation();
  const [activeTab, setActiveTab] = useState("dashboard");
  const [url, setUrl] = useState("");
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedTasks, setExpandedTasks] = useState({});
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem("theme") || "dark");
  const [config, setConfig] = useState({
    download_dir: "",
    max_threads: 8,
    max_concurrent_downloads: 3,
    proxy_enabled: false,
    proxy_url: "",
  });

  // Apply theme and language direction to document
  useEffect(() => {
    localStorage.setItem("theme", theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.dir = i18n.language === "ar" ? "rtl" : "ltr";
    document.documentElement.lang = i18n.language;
  }, [i18n.language]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [t, c] = await Promise.all([
          backend.GetTasks(),
          backend.GetConfig()
        ]);
        setTasks(t || []);
        setConfig(c);
        setLoading(false);
      } catch (e) {
        console.error("Fetch failed", e);
      }
    };

    fetchData();
    const interval = setInterval(() => {
      backend.GetTasks().then(setTasks).catch(() => { });
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const toggleExpand = (id) => {
    setExpandedTasks(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const handleAdd = async (startNow) => {
    if (!url) return;
    await backend.AddFile(url, startNow);
    setUrl("");
    setActiveTab("downloads");
    setSidebarOpen(false);
  };

  const updateConfig = async (key, value) => {
    const newConfig = { ...config, [key]: value };
    setConfig(newConfig);
    await backend.UpdateConfig(newConfig);
  };

  const changeLanguage = (lang) => {
    i18n.changeLanguage(lang);
  };

  const toggleTheme = () => {
    setTheme(prev => prev === "dark" ? "light" : "dark");
  };

  const isDark = theme === "dark";

  const activeTasksCount = tasks.filter(t => t.status === "Downloading").length;
  const completedTasksCount = tasks.filter(t => t.status === "Completed").length;
  const totalSize = tasks.reduce((acc, t) => acc + (t.size || 0), 0);
  const downloadedSize = tasks.reduce((acc, t) => acc + (t.downloaded || 0), 0);

  const formatSize = (bytes) => {
    if (bytes === 0 || !bytes) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
  };

  const formatSpeed = (speed) => {
    if (!speed) return "0 MB/s";
    return `${speed.toFixed(1)} MB/s`;
  };

  // Theme classes
  const bg = isDark
    ? "bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 text-slate-100"
    : "bg-gradient-to-br from-slate-100 via-white to-slate-100 text-slate-900";

  const sidebar = isDark
    ? "bg-slate-900/95 border-slate-800/50"
    : "bg-white/95 border-slate-200/80";

  const header = isDark
    ? "bg-slate-900/50 border-slate-800/50"
    : "bg-white/80 border-slate-200/80";

  const card = isDark
    ? "bg-slate-900/50 border-slate-800/50"
    : "bg-white border-slate-200";

  const inputCls = isDark
    ? "bg-slate-950/50 border-slate-800 text-slate-100 placeholder:text-slate-600"
    : "bg-slate-50 border-slate-300 text-slate-900 placeholder:text-slate-400";

  const mutedText = isDark ? "text-slate-500" : "text-slate-500";
  const subText = isDark ? "text-slate-400" : "text-slate-600";
  const hoverItem = isDark ? "hover:bg-slate-800/50" : "hover:bg-slate-100";

  return (
    <div className={cn("flex h-full font-sans relative", bg)}
      dir={i18n.language === "ar" ? "rtl" : "ltr"}
    >
      {/* Mobile Overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/50 backdrop-blur-sm z-20 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <div className={cn(
        "fixed lg:static inset-y-0 start-0 w-[280px] backdrop-blur-xl border-e flex flex-col py-6 transition-all duration-300 z-30",
        sidebar,
        sidebarOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"
      )}>
        {/* Sidebar Header */}
        <div className="px-6 mb-8 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="bg-gradient-to-br from-blue-500 to-blue-600 p-2.5 rounded-xl shadow-lg shadow-blue-500/20">
              <Zap className="w-5 h-5 text-white" />
            </div>
            <h1 className="text-xl font-bold bg-gradient-to-r from-blue-500 to-blue-400 bg-clip-text text-transparent">
              Go Downloader
            </h1>
          </div>
          <button
            onClick={() => setSidebarOpen(false)}
            className={cn("lg:hidden p-2 rounded-lg", hoverItem)}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 space-y-1.5 px-4">
          <NavItem
            isDark={isDark}
            active={activeTab === "dashboard"}
            onClick={() => { setActiveTab("dashboard"); setSidebarOpen(false); }}
            icon={<LayoutDashboard className="w-5 h-5" />}
            label={t("dashboard")}
          />
          <NavItem
            isDark={isDark}
            active={activeTab === "downloads"}
            onClick={() => { setActiveTab("downloads"); setSidebarOpen(false); }}
            icon={<Download className="w-5 h-5" />}
            label={t("downloads")}
            badge={activeTasksCount > 0 ? activeTasksCount : null}
          />
          <NavItem
            isDark={isDark}
            active={activeTab === "settings"}
            onClick={() => { setActiveTab("settings"); setSidebarOpen(false); }}
            icon={<Settings className="w-5 h-5" />}
            label={t("settings")}
          />
        </nav>

        {/* System Status */}
        <div className="mt-auto px-6 py-4">
          <div className={cn("rounded-xl p-4 border", isDark ? "bg-slate-800/50 border-slate-700/50" : "bg-slate-100 border-slate-200")}>
            <div className="flex items-center gap-3">
              <div className="relative">
                <div className="w-2 h-2 rounded-full bg-green-500" />
                <div className="absolute inset-0 w-2 h-2 rounded-full bg-green-500 animate-ping" />
              </div>
              <div className="flex-1">
                <p className="text-sm font-medium">{t("system_online")}</p>
                <p className={cn("text-xs", mutedText)}>{t("all_systems_operational")}</p>
              </div>
            </div>
            <div className={cn("mt-3 pt-3 border-t grid grid-cols-2 gap-2 text-xs", isDark ? "border-slate-700/50" : "border-slate-200")}>
              <div>
                <span className={mutedText}>{t("active")}</span>
                <p className="font-medium text-blue-400">{activeTasksCount}</p>
              </div>
              <div>
                <span className={mutedText}>{t("completed")}</span>
                <p className="font-medium text-green-400">{completedTasksCount}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header */}
        <header className={cn(
          "h-16 border-b flex items-center justify-between px-4 lg:px-8 backdrop-blur-xl sticky top-0 z-10",
          header
        )}>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setSidebarOpen(true)}
              className={cn("lg:hidden p-2 rounded-lg", hoverItem)}
            >
              <Menu className="w-5 h-5" />
            </button>
            <h2 className="text-lg lg:text-xl font-semibold capitalize bg-gradient-to-r from-blue-500 to-blue-400 bg-clip-text text-transparent">
              {t(activeTab)}
            </h2>
          </div>

          <div className="flex items-center gap-3 lg:gap-4 text-sm">
            <div className={cn("hidden sm:flex items-center gap-2", subText)}>
              <HardDrive className="w-4 h-4" />
              <span className="hidden lg:inline">{t("storage")}:</span>
              <span className="font-medium">{formatSize(downloadedSize)} / {formatSize(totalSize)}</span>
            </div>
            {/* Theme toggle */}
            <button
              onClick={toggleTheme}
              className={cn("p-2 rounded-xl border transition-all", isDark ? "bg-slate-800 border-slate-700 text-yellow-400 hover:bg-slate-700" : "bg-slate-100 border-slate-300 text-slate-700 hover:bg-slate-200")}
              title={isDark ? t("light") : t("dark")}
            >
              {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
            {/* Language toggle */}
            <button
              onClick={() => changeLanguage(i18n.language === "ar" ? "en" : "ar")}
              className={cn("p-2 rounded-xl border transition-all flex items-center gap-1.5 text-xs font-bold", isDark ? "bg-slate-800 border-slate-700 hover:bg-slate-700" : "bg-slate-100 border-slate-300 hover:bg-slate-200")}
              title={t("language")}
            >
              <Languages className="w-4 h-4" />
              <span className="hidden sm:inline uppercase">{i18n.language === "ar" ? "EN" : "AR"}</span>
            </button>
            <div className={cn("flex items-center gap-2 px-3 py-1.5 rounded-full border", isDark ? "bg-blue-500/10 border-blue-500/20" : "bg-blue-50 border-blue-200")}>
              <Zap className="w-4 h-4 text-blue-400" />
              <span className="text-blue-400 font-medium text-xs lg:text-sm">{config.max_concurrent_downloads} {t("concurrent")}</span>
            </div>
          </div>
        </header>

        {/* Main Content Area */}
        <main className="flex-1 overflow-y-auto overflow-x-hidden p-4 lg:p-8 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent">
          {activeTab === "dashboard" && (
            <div className="max-w-7xl mx-auto space-y-6 lg:space-y-8 animate-in fade-in duration-500">
              {/* Welcome Card */}
              <div className={cn("relative overflow-hidden rounded-2xl lg:rounded-3xl border p-6 lg:p-8 shadow-2xl", isDark ? "bg-gradient-to-br from-slate-900 to-slate-950 border-slate-800/50" : "bg-gradient-to-br from-blue-50 to-white border-blue-100")}>
                <div className="absolute inset-0 bg-grid-white/[0.02] bg-[size:50px_50px]" />
                <div className="relative">
                  <h3 className="text-xl lg:text-3xl font-bold mb-2 bg-gradient-to-r from-blue-500 to-blue-400 bg-clip-text text-transparent">
                    {t("welcome_back")}
                  </h3>
                  <p className={cn("text-sm lg:text-base mb-6 lg:mb-8 max-w-2xl", subText)}>
                    {t("welcome_desc")}
                  </p>

                  <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row gap-3">
                      <input
                        type="text"
                        className={cn("flex-1 border rounded-xl lg:rounded-2xl px-4 lg:px-6 py-3 lg:py-4 outline-none focus:ring-2 ring-blue-500/50 transition-all text-sm lg:text-base", inputCls)}
                        placeholder="https://example.com/file.pkg"
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && handleAdd(true)}
                      />
                    </div>

                    <div className="flex flex-col sm:flex-row gap-3">
                      <button
                        className="flex-1 bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white font-semibold py-3 lg:py-4 px-4 lg:px-6 rounded-xl lg:rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-[0.98] shadow-lg shadow-blue-500/25 text-sm lg:text-base"
                        onClick={() => handleAdd(true)}
                      >
                        <Zap className="w-4 h-4 lg:w-5 lg:h-5" /> {t("start_download")}
                      </button>
                      <button
                        className={cn("font-semibold py-3 lg:py-4 px-4 lg:px-6 rounded-xl lg:rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-[0.98] border text-sm lg:text-base", isDark ? "bg-slate-800/50 hover:bg-slate-800 text-slate-200 border-slate-700/50" : "bg-slate-100 hover:bg-slate-200 text-slate-700 border-slate-300")}
                        onClick={() => handleAdd(false)}
                      >
                        <Plus className="w-4 h-4 lg:w-5 lg:h-5" /> {t("add_to_queue")}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Stats Grid */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 lg:gap-6">
                <StatsCard isDark={isDark} title={t("total_files")} value={tasks.length} icon={<Monitor className="w-4 h-4 lg:w-5 lg:h-5 text-blue-400" />} trend={tasks.length > 0 ? "+12%" : "0%"} />
                <StatsCard isDark={isDark} title={t("downloading")} value={activeTasksCount} icon={<Download className="w-4 h-4 lg:w-5 lg:h-5 text-green-400" />} trend={activeTasksCount > 0 ? t("active") : "Idle"} />
                <StatsCard isDark={isDark} title={t("completed")} value={completedTasksCount} icon={<Zap className="w-4 h-4 lg:w-5 lg:h-5 text-yellow-400" />} trend={`${tasks.length ? Math.round((completedTasksCount / tasks.length) * 100) : 0}%`} />
                <StatsCard isDark={isDark} title={t("speed")} value={formatSpeed(Math.max(...tasks.map(t => t.speed || 0)))} icon={<Gauge className="w-4 h-4 lg:w-5 lg:h-5 text-purple-400" />} trend="Peak" />
              </div>

              {/* Quick Actions */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                <QuickAction
                  icon={<Play className="w-4 h-4" />}
                  label={t("resume_all")}
                  onClick={() => backend.StartAll()}
                  color="green"
                />
                <QuickAction
                  icon={<Pause className="w-4 h-4" />}
                  label={t("pause_all")}
                  onClick={() => backend.StopAll()}
                  color="yellow"
                />
                <QuickAction
                  icon={<FolderOpen className="w-4 h-4" />}
                  label={t("open_folder")}
                  onClick={() => backend.OpenDownloadsFolder()}
                  color="blue"
                />
                <QuickAction
                  icon={<Activity className="w-4 h-4" />}
                  label={t("system_info")}
                  onClick={() => setActiveTab("settings")}
                  color="purple"
                />
              </div>
            </div>
          )}

          {activeTab === "downloads" && (
            <div className="max-w-7xl mx-auto space-y-4 lg:space-y-6 animate-in fade-in duration-500">
              {/* Mobile Controls */}
              <div className="flex sm:hidden gap-2 overflow-x-auto pb-2 scrollbar-hide">
                <button onClick={() => backend.StartAll()} className={cn("px-4 py-2 rounded-xl text-sm font-medium flex items-center gap-2 whitespace-nowrap border", isDark ? "bg-slate-800/50 hover:bg-slate-800 border-slate-700/50" : "bg-white hover:bg-slate-50 border-slate-200")}>
                  <Play className="w-4 h-4" /> {t("resume_all")}
                </button>
                <button onClick={() => backend.StopAll()} className={cn("px-4 py-2 rounded-xl text-sm font-medium flex items-center gap-2 whitespace-nowrap border", isDark ? "bg-slate-800/50 hover:bg-slate-800 border-slate-700/50" : "bg-white hover:bg-slate-50 border-slate-200")}>
                  <Pause className="w-4 h-4" /> {t("pause_all")}
                </button>
              </div>

              {/* Desktop Controls */}
              <div className="hidden sm:flex justify-between items-center">
                <div className="flex gap-2">
                  <button onClick={() => backend.StartAll()} className={cn("px-4 py-2 rounded-xl text-sm font-medium flex items-center gap-2 transition-colors border", isDark ? "bg-slate-800/50 hover:bg-slate-800 border-slate-700/50" : "bg-white hover:bg-slate-50 border-slate-200")}>
                    <Play className="w-4 h-4" /> {t("resume_all")}
                  </button>
                  <button onClick={() => backend.StopAll()} className={cn("px-4 py-2 rounded-xl text-sm font-medium flex items-center gap-2 transition-colors border", isDark ? "bg-slate-800/50 hover:bg-slate-800 border-slate-700/50" : "bg-white hover:bg-slate-50 border-slate-200")}>
                    <Pause className="w-4 h-4" /> {t("pause_all")}
                  </button>
                </div>
              </div>

              {/* Downloads List */}
              <div className={cn("backdrop-blur-sm rounded-xl lg:rounded-2xl border overflow-hidden", card)}>
                {tasks.length === 0 ? (
                  <div className="p-12 lg:p-20 text-center space-y-4">
                    <div className={cn("w-16 h-16 lg:w-20 lg:h-20 rounded-2xl flex items-center justify-center mx-auto mb-4", isDark ? "bg-slate-800/50" : "bg-slate-100")}>
                      <Download className={cn("w-8 h-8 lg:w-10 lg:h-10", isDark ? "text-slate-600" : "text-slate-400")} />
                    </div>
                    <p className={cn("font-medium text-base lg:text-lg", subText)}>{t("queue_empty")}</p>
                    <button
                      onClick={() => setActiveTab("dashboard")}
                      className="text-blue-400 hover:text-blue-300 text-sm lg:text-base transition-colors"
                    >
                      {t("add_first_download")}
                    </button>
                  </div>
                ) : (
                  <div className={cn("divide-y", isDark ? "divide-slate-800/50" : "divide-slate-100")}>
                    {tasks.sort((a, b) => new Date(b.created_at) - new Date(a.created_at)).map((task) => (
                      <DownloadItem
                        key={task.id}
                        task={task}
                        expanded={expandedTasks[task.id]}
                        onToggleExpand={() => toggleExpand(task.id)}
                        onPause={() => backend.PauseTask(task.id)}
                        onResume={() => backend.ResumeTask(task.id)}
                        onOpenFolder={() => backend.OpenFolder(task.id)}
                        onDelete={() => backend.DeleteTask(task.id, false)}
                        formatSize={formatSize}
                        formatSpeed={formatSpeed}
                        isDark={isDark}
                        t={t}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === "settings" && (
            <div className="max-w-4xl mx-auto space-y-4 lg:space-y-6 animate-in fade-in duration-500">
              {/* Appearance Settings */}
              <SettingsCard isDark={isDark} title={t("theme") + " & " + t("language")} icon={<Monitor className="w-5 h-5 text-blue-400" />}>
                <div className="space-y-4 lg:space-y-6">
                  {/* Theme */}
                  <SettingItem isDark={isDark} label={t("theme")} description={isDark ? t("dark") : t("light")}>
                    <div className="flex gap-2">
                      <button
                        onClick={() => setTheme("light")}
                        className={cn("flex items-center gap-2 px-4 py-2 rounded-xl border text-sm font-medium transition-all", theme === "light" ? "bg-yellow-400 text-yellow-900 border-yellow-500" : isDark ? "bg-slate-800 border-slate-700 hover:bg-slate-700" : "bg-slate-100 border-slate-300 hover:bg-slate-200")}
                      >
                        <Sun className="w-4 h-4" /> {t("light")}
                      </button>
                      <button
                        onClick={() => setTheme("dark")}
                        className={cn("flex items-center gap-2 px-4 py-2 rounded-xl border text-sm font-medium transition-all", theme === "dark" ? "bg-slate-700 text-white border-slate-600" : isDark ? "bg-slate-800 border-slate-700 hover:bg-slate-700" : "bg-slate-100 border-slate-300 hover:bg-slate-200")}
                      >
                        <Moon className="w-4 h-4" /> {t("dark")}
                      </button>
                    </div>
                  </SettingItem>

                  {/* Language */}
                  <SettingItem isDark={isDark} label={t("language")} description={i18n.language === "ar" ? t("arabic") : t("english")}>
                    <div className="flex gap-2">
                      <button
                        onClick={() => changeLanguage("en")}
                        className={cn("flex items-center gap-2 px-4 py-2 rounded-xl border text-sm font-medium transition-all", i18n.language === "en" ? "bg-blue-600 text-white border-blue-700" : isDark ? "bg-slate-800 border-slate-700 hover:bg-slate-700" : "bg-slate-100 border-slate-300 hover:bg-slate-200")}
                      >
                        🇺🇸 {t("english")}
                      </button>
                      <button
                        onClick={() => changeLanguage("ar")}
                        className={cn("flex items-center gap-2 px-4 py-2 rounded-xl border text-sm font-medium transition-all", i18n.language === "ar" ? "bg-blue-600 text-white border-blue-700" : isDark ? "bg-slate-800 border-slate-700 hover:bg-slate-700" : "bg-slate-100 border-slate-300 hover:bg-slate-200")}
                      >
                        🇸🇦 {t("arabic")}
                      </button>
                    </div>
                  </SettingItem>
                </div>
              </SettingsCard>

              {/* General Settings */}
              <SettingsCard isDark={isDark} title={t("general_settings")} icon={<Monitor className="w-5 h-5 text-blue-400" />}>
                <div className="space-y-4 lg:space-y-6">
                  <SettingItem isDark={isDark} label={t("download_dir")} description="">
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        className={cn("flex-1 border rounded-lg px-3 py-2 text-sm focus:ring-1 ring-blue-500 outline-none min-w-[200px] lg:min-w-[300px]", inputCls)}
                        value={config.download_dir}
                        onChange={(e) => updateConfig("download_dir", e.target.value)}
                      />
                      <button className={cn("p-2 rounded-lg border", isDark ? "hover:bg-slate-800 border-slate-800" : "hover:bg-slate-100 border-slate-200")}>
                        <FolderOpen className="w-4 h-4" />
                      </button>
                    </div>
                  </SettingItem>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <SettingItem isDark={isDark} label={t("max_concurrent")} description="">
                      <input
                        type="number"
                        className={cn("border rounded-lg px-3 py-2 text-sm w-24 text-center outline-none focus:ring-1 ring-blue-500", inputCls)}
                        value={config.max_concurrent_downloads}
                        min="1" max="10"
                        onChange={(e) => updateConfig("max_concurrent_downloads", parseInt(e.target.value))}
                      />
                    </SettingItem>

                    <SettingItem isDark={isDark} label={t("threads_per_file")} description="">
                      <input
                        type="number"
                        className={cn("border rounded-lg px-3 py-2 text-sm w-24 text-center outline-none focus:ring-1 ring-blue-500", inputCls)}
                        value={config.max_threads}
                        min="1" max="32"
                        onChange={(e) => updateConfig("max_threads", parseInt(e.target.value))}
                      />
                    </SettingItem>
                  </div>
                </div>
              </SettingsCard>

              {/* Proxy Settings */}
              <SettingsCard isDark={isDark} title={t("proxy_config")} icon={<Globe className="w-5 h-5 text-purple-400" />}
                action={
                  <Toggle
                    enabled={config.proxy_enabled}
                    onChange={(val) => updateConfig("proxy_enabled", val)}
                  />
                }
              >
                {config.proxy_enabled && (
                  <div className="space-y-3 animate-in fade-in slide-in-from-top-2 duration-300">
                    <label className={cn("text-sm block font-medium", subText)}>Proxy Server Address</label>
                    <input
                      type="text"
                      placeholder="http://username:password@proxy.com:8080"
                      className={cn("w-full border rounded-lg px-4 py-2.5 text-sm focus:ring-1 ring-purple-500 outline-none", inputCls)}
                      value={config.proxy_url}
                      onChange={(e) => updateConfig("proxy_url", e.target.value)}
                    />
                    <p className={cn("text-xs flex items-center gap-1", mutedText)}>
                      <Network className="w-3 h-3" /> Supports HTTP, HTTPS, and SOCKS5 proxies
                    </p>
                  </div>
                )}
              </SettingsCard>

              {/* System Info */}
              <SettingsCard isDark={isDark} title={t("system_info")} icon={<Cpu className="w-5 h-5 text-green-400" />}>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  <SystemInfoItem isDark={isDark} label={t("version")} value="1.0.0" />
                  <SystemInfoItem isDark={isDark} label={t("go_version")} value="1.21" />
                  <SystemInfoItem isDark={isDark} label={t("threads")} value={config.max_threads} />
                  <SystemInfoItem isDark={isDark} label={t("downloads")} value={tasks.length} />
                </div>
              </SettingsCard>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

// Helper Components

const NavItem = ({ active, onClick, icon, label, badge, isDark }) => (
  <button
    onClick={onClick}
    className={cn(
      "w-full flex items-center justify-between py-2.5 px-3 rounded-xl transition-all duration-200 group",
      active
        ? "bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-600/20"
        : isDark ? "text-slate-400 hover:bg-slate-800/50 hover:text-slate-100" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
    )}
  >
    <div className="flex items-center gap-3 text-sm font-medium">
      {icon}
      <span>{label}</span>
    </div>
    {badge && (
      <span className={cn(
        "text-[10px] font-bold px-1.5 py-0.5 rounded-full",
        active ? "bg-white/20 text-white" : "bg-blue-500/20 text-blue-400"
      )}>
        {badge}
      </span>
    )}
  </button>
);

const StatsCard = ({ title, value, icon, trend, isDark }) => (
  <div className={cn("backdrop-blur-sm border p-4 lg:p-6 rounded-xl lg:rounded-2xl hover:border-slate-700/50 transition-colors", isDark ? "bg-slate-900/50 border-slate-800/50" : "bg-white border-slate-200")}>
    <div className="flex items-center justify-between mb-2">
      <span className={cn("text-xs lg:text-sm font-medium", isDark ? "text-slate-500" : "text-slate-500")}>{title}</span>
      <div className={cn("p-1.5 lg:p-2 rounded-lg", isDark ? "bg-slate-800/50" : "bg-slate-100")}>
        {icon}
      </div>
    </div>
    <div className="flex items-end justify-between">
      <div className="text-xl lg:text-2xl font-bold">{value}</div>
      <span className={cn("text-xs font-medium", trend?.includes('+') ? "text-green-400" : "text-slate-500")}>
        {trend}
      </span>
    </div>
  </div>
);

const QuickAction = ({ icon, label, onClick, color }) => {
  const colors = {
    green: "from-green-600 to-green-500 hover:from-green-700 hover:to-green-600 shadow-green-500/25",
    yellow: "from-yellow-600 to-yellow-500 hover:from-yellow-700 hover:to-yellow-600 shadow-yellow-500/25",
    blue: "from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 shadow-blue-500/25",
    purple: "from-purple-600 to-purple-500 hover:from-purple-700 hover:to-purple-600 shadow-purple-500/25"
  };

  return (
    <button
      onClick={onClick}
      className={cn(
        "bg-gradient-to-r p-3 lg:p-4 rounded-xl lg:rounded-2xl flex items-center justify-center gap-2 transition-all active:scale-[0.98] shadow-lg text-white text-sm lg:text-base",
        colors[color]
      )}
    >
      {icon}
      <span className="hidden sm:inline">{label}</span>
    </button>
  );
};

const DownloadItem = ({
  task,
  expanded,
  onToggleExpand,
  onPause,
  onResume,
  onOpenFolder,
  onDelete,
  formatSize,
  formatSpeed,
  isDark,
  t
}) => {
  const isDownloading = task.status === "Downloading";
  const isPaused = task.status === "Paused";
  const isCompleted = task.status === "Completed";
  const isError = task.status === "Error";
  const isQueued = task.status === "Queued";

  const statusColors = {
    Downloading: "bg-blue-500",
    Paused: "bg-yellow-500",
    Completed: "bg-green-500",
    Error: "bg-red-500",
    Queued: "bg-slate-500"
  };

  const statusTextColors = {
    Downloading: "text-blue-400",
    Paused: "text-yellow-400",
    Completed: "text-green-400",
    Error: "text-red-400",
    Queued: "text-slate-400"
  };

  return (
    <div className="group">
      <div className={cn("p-3 lg:p-5 transition-colors", isDark ? "hover:bg-slate-800/30" : "hover:bg-slate-50")}>
        <div className="flex items-start gap-3 lg:gap-4">
          <div className={cn(
            "w-8 h-8 lg:w-10 lg:h-10 rounded-xl flex items-center justify-center shrink-0",
            isCompleted ? "bg-green-500/10" : isDownloading ? "bg-blue-500/10" : isError ? "bg-red-500/10" : isDark ? "bg-slate-800/50" : "bg-slate-100"
          )}>
            {isCompleted ? <Zap className="w-4 h-4 lg:w-5 lg:h-5 text-green-500" /> :
              isError ? <AlertCircle className="w-4 h-4 lg:w-5 lg:h-5 text-red-500" /> :
                <Download className={cn("w-4 h-4 lg:w-5 lg:h-5", isDownloading ? "text-blue-500" : "text-slate-500")} />
            }
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-2">
              <div className="min-w-0">
                <h4 className="font-medium text-sm lg:text-base truncate pr-2">
                  {task.file_name}
                </h4>
                <div className="flex items-center gap-2 text-xs mt-1">
                  <span className={cn("font-medium", statusTextColors[task.status] || "text-slate-400")}>
                    {task.status}
                  </span>
                  <span className={isDark ? "text-slate-600" : "text-slate-300"}>•</span>
                  <span className={isDark ? "text-slate-400" : "text-slate-600"}>
                    {formatSize(task.downloaded)} / {formatSize(task.size)}
                  </span>
                  {isDownloading && (
                    <>
                      <span className={cn("hidden sm:inline", isDark ? "text-slate-600" : "text-slate-300")}>•</span>
                      <span className="text-blue-400 hidden sm:inline">{formatSpeed(task.speed)}</span>
                    </>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-1 lg:gap-2">
                <ActionButton
                  isDark={isDark}
                  onClick={onToggleExpand}
                  icon={<ChevronDown className={cn("w-4 h-4 transition-transform", expanded && "rotate-180")} />}
                  tooltip={t("details")}
                  active={expanded}
                />
                <ActionButton
                  isDark={isDark}
                  onClick={isDownloading || isQueued ? onPause : onResume}
                  icon={isDownloading || isQueued ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                  tooltip={isDownloading || isQueued ? t("pause") : t("resume")}
                  disabled={isCompleted}
                />
                <ActionButton
                  isDark={isDark}
                  onClick={onOpenFolder}
                  icon={<FolderOpen className="w-4 h-4" />}
                  tooltip={t("open_folder")}
                />
                <ActionButton
                  isDark={isDark}
                  onClick={onDelete}
                  icon={<Trash2 className="w-4 h-4" />}
                  tooltip={t("delete")}
                  danger
                />
              </div>
            </div>

            <div className="mt-3 space-y-1">
              <div className={cn("h-1.5 lg:h-2 w-full rounded-full overflow-hidden", isDark ? "bg-slate-800/50" : "bg-slate-200")}>
                <div
                  className={cn("h-full rounded-full transition-all duration-500", statusColors[task.status] || "bg-slate-600")}
                  style={{ width: `${task.progress || 0}%` }}
                />
              </div>
              {isDownloading && (
                <div className={cn("flex lg:hidden justify-between text-[10px]", isDark ? "text-slate-500" : "text-slate-400")}>
                  <span>{formatSpeed(task.speed)}</span>
                  <span className="flex items-center gap-1">
                    <Clock className="w-3 h-3" /> {task.eta || "∞"}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {expanded && (
        <div className={cn("px-3 lg:px-5 pb-4 lg:pb-6 border-t", isDark ? "border-slate-800/50 bg-slate-900/30" : "border-slate-100 bg-slate-50/50")}>
          <div className="pt-4 grid grid-cols-1 lg:grid-cols-2 gap-4 lg:gap-6">
            <div>
              <h5 className={cn("text-[10px] font-bold uppercase mb-3 tracking-wider flex items-center gap-2", isDark ? "text-slate-500" : "text-slate-400")}>
                <Zap className="w-3 h-3" /> {t("thread_segments")} ({task.parts?.length || 0})
              </h5>
              <div className="flex flex-wrap gap-1">
                {task.parts?.map((part, idx) => (
                  <div
                    key={idx}
                    title={`Thread ${part.id}: ${formatSize(part.downloaded)} (${part.status})`}
                    className={cn(
                      "w-4 h-4 lg:w-5 lg:h-5 rounded transition-all duration-300",
                      part.status === "Completed" ? "bg-green-500" :
                        part.status === "Downloading" ? "bg-blue-500 animate-pulse" :
                          part.status === "Error" ? "bg-red-500" : isDark ? "bg-slate-700" : "bg-slate-300"
                    )}
                  />
                ))}
              </div>
            </div>

            <div>
              <h5 className={cn("text-[10px] font-bold uppercase mb-3 tracking-wider flex items-center gap-2", isDark ? "text-slate-500" : "text-slate-400")}>
                <Activity className="w-3 h-3" /> {t("thread_speeds")}
              </h5>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {task.parts?.slice(0, 9).map((part, idx) => (
                  <div key={idx} className={cn("p-2 rounded-lg border", isDark ? "bg-slate-900/50 border-slate-800/50" : "bg-white border-slate-200")}>
                    <div className={cn("text-[8px]", isDark ? "text-slate-600" : "text-slate-400")}>Thread {part.id}</div>
                    <div className={cn("text-[10px] font-mono mt-0.5", part.status === "Downloading" ? "text-blue-400" : isDark ? "text-slate-600" : "text-slate-400")}>
                      {formatSpeed(part.speed)}
                    </div>
                  </div>
                ))}
                {task.parts?.length > 9 && (
                  <div className={cn("text-[10px] flex items-center justify-center italic rounded-lg border", isDark ? "text-slate-600 bg-slate-900/50 border-slate-800/50" : "text-slate-400 bg-white border-slate-200")}>
                    +{task.parts.length - 9} more
                  </div>
                )}
              </div>
            </div>
          </div>

          {isError && task.error_message && (
            <div className="mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
              <p className="text-xs text-red-400 flex items-center gap-2">
                <AlertCircle className="w-4 h-4" />
                {task.error_message}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const ActionButton = ({ onClick, icon, tooltip, danger, disabled, active, isDark }) => (
  <button
    onClick={onClick}
    disabled={disabled}
    className={cn(
      "p-1.5 lg:p-2 rounded-lg transition-all active:scale-90",
      danger ? "hover:bg-red-500/20 text-red-400 hover:text-red-300" :
        active ? "bg-blue-500/20 text-blue-400" : isDark ? "text-slate-500 hover:bg-slate-800 hover:text-slate-300" : "text-slate-400 hover:bg-slate-100 hover:text-slate-700",
      disabled && "opacity-50 cursor-not-allowed"
    )}
    title={tooltip}
  >
    {icon}
  </button>
);

const SettingsCard = ({ title, icon, children, action, isDark }) => (
  <div className={cn("backdrop-blur-sm rounded-xl lg:rounded-2xl border p-4 lg:p-6", isDark ? "bg-slate-900/50 border-slate-800/50" : "bg-white border-slate-200")}>
    <div className="flex items-center justify-between mb-4 lg:mb-6">
      <h3 className="text-base lg:text-lg font-semibold flex items-center gap-2">
        {icon}
        {title}
      </h3>
      {action}
    </div>
    {children}
  </div>
);

const SettingItem = ({ label, description, children, isDark }) => (
  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4">
    <div className="flex-1">
      <div className="font-medium text-sm">{label}</div>
      {description && <div className={cn("text-xs mt-0.5", isDark ? "text-slate-500" : "text-slate-400")}>{description}</div>}
    </div>
    {children}
  </div>
);

const SystemInfoItem = ({ label, value, isDark }) => (
  <div className={cn("rounded-lg p-3 text-center", isDark ? "bg-slate-800/30" : "bg-slate-100")}>
    <div className={cn("text-xs mb-1", isDark ? "text-slate-500" : "text-slate-500")}>{label}</div>
    <div className="font-mono text-sm lg:text-base font-medium">{value}</div>
  </div>
);

const Toggle = ({ enabled, onChange }) => (
  <button
    onClick={() => onChange(!enabled)}
    className={cn(
      "relative w-12 h-6 rounded-full transition-colors duration-200 focus:outline-none",
      enabled ? "bg-blue-600" : "bg-slate-700"
    )}
  >
    <span
      className={cn(
        "absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-transform duration-200",
        enabled && "transform translate-x-6"
      )}
    />
  </button>
);

export default App;