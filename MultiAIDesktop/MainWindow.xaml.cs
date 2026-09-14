using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;
using MultiAIDesktop.Models;

namespace MultiAIDesktop
{
    public partial class MainWindow : Window
    {
        private readonly List<WebView2> _webViews = new();

        public MainWindow()
        {
            InitializeComponent();
            Loaded += MainWindow_Loaded;
            PreviewKeyDown += MainWindow_PreviewKeyDown;
        }

        private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            var userDataFolder = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "MultiAIDesktop", "WebView2");
            Directory.CreateDirectory(userDataFolder);

            var environment = await CoreWebView2Environment.CreateAsync(userDataFolder: userDataFolder);

            foreach (var service in LoadServices())
            {
                var webView = new WebView2();
                var tab = new TabItem { Header = service.Name, Content = webView };
                ServiceTabs.Items.Add(tab);
                _webViews.Add(webView);

                await webView.EnsureCoreWebView2Async(environment);
                webView.Source = new Uri(service.Url);
            }

            if (ServiceTabs.Items.Count > 0)
                ServiceTabs.SelectedIndex = 0;
        }

        private static List<AiService> LoadServices()
        {
            var path = Path.Combine(AppContext.BaseDirectory, "services.json");
            if (File.Exists(path))
            {
                var json = File.ReadAllText(path);
                var services = JsonSerializer.Deserialize<List<AiService>>(json);
                if (services is { Count: > 0 })
                    return services;
            }

            return new List<AiService>
            {
                new() { Name = "Claude", Url = "https://claude.ai" },
                new() { Name = "ChatGPT", Url = "https://chatgpt.com" },
                new() { Name = "Gemini", Url = "https://gemini.google.com" },
            };
        }

        private WebView2? CurrentWebView =>
            ServiceTabs.SelectedIndex >= 0 && ServiceTabs.SelectedIndex < _webViews.Count
                ? _webViews[ServiceTabs.SelectedIndex]
                : null;

        private void BackButton_Click(object sender, RoutedEventArgs e)
        {
            var core = CurrentWebView?.CoreWebView2;
            if (core != null && core.CanGoBack)
                core.GoBack();
        }

        private void ForwardButton_Click(object sender, RoutedEventArgs e)
        {
            var core = CurrentWebView?.CoreWebView2;
            if (core != null && core.CanGoForward)
                core.GoForward();
        }

        private void ReloadButton_Click(object sender, RoutedEventArgs e)
        {
            CurrentWebView?.CoreWebView2?.Reload();
        }

        private void ZoomInButton_Click(object sender, RoutedEventArgs e)
        {
            var wv = CurrentWebView;
            if (wv != null)
                wv.ZoomFactor = Math.Min(wv.ZoomFactor + 0.1, 3.0);
        }

        private void ZoomOutButton_Click(object sender, RoutedEventArgs e)
        {
            var wv = CurrentWebView;
            if (wv != null)
                wv.ZoomFactor = Math.Max(wv.ZoomFactor - 0.1, 0.3);
        }

        private void MainWindow_PreviewKeyDown(object sender, KeyEventArgs e)
        {
            if (Keyboard.Modifiers == ModifierKeys.Control && e.Key >= Key.D1 && e.Key <= Key.D9)
            {
                int index = e.Key - Key.D1;
                if (index < ServiceTabs.Items.Count)
                {
                    ServiceTabs.SelectedIndex = index;
                    e.Handled = true;
                }
            }
        }
    }
}
