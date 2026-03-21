# Pic Path - Android Image Path Copier

<div align="center">

**Fast, Simple Image Path Copying for Android**

Perfect for Claude on Termux - instantly get file paths for image analysis!

[Download APK](https://github.com/alexey-a-abramov/pic-path/releases) | [Report Bug](https://github.com/alexey-a-abramov/pic-path/issues) | [Request Feature](https://github.com/alexey-a-abramov/pic-path/issues)

</div>

---

## 📱 What is Pic Path?

Pic Path is a lightweight Android app designed to make copying image file paths effortless. Built specifically for developers and power users who frequently need to reference image paths - especially when working with AI assistants like Claude in Termux.

### Why Pic Path?

When using Claude (or other AI tools) on Android, you often need to provide image file paths for analysis. Traditional methods are tedious:
- ❌ Navigating through file managers
- ❌ Long-pressing to copy paths
- ❌ Switching between multiple apps

**With Pic Path, it's instant:**
- ✅ Single tap to copy any image path
- ✅ Share images directly from any app
- ✅ Automatic clipboard copy with notification
- ✅ Works perfectly with Claude on Termux

---

## ✨ Features

### 🎯 Core Features

**📂 Category Tabs**
- Browse images by category: All, Screenshots, Camera, Downloads, Other
- Screenshots tab set as default (most commonly shared)
- Fast category switching with Material 3 design

**🖼️ Dual View Modes**
- **Grid View**: 3-column thumbnail grid for browsing
- **Fullscreen View**: Single tap opens fullscreen with swipe navigation
- **Image Editor**: Integrated editor with crop and annotation tools (arrows and text)

**📋 Multiple Copy Methods**
- **Copy Button**: 70% transparent button on every image (bottom-right)
- **Edit Button**: Quick access to crop and annotate before copying path
- **Long Press**: Press and hold anywhere on image
- **Share Target**: Share images from any app to auto-copy path or edit before copying

**🔔 Smart Notifications**
- System notification appears when path is copied
- Shows filename and full path
- Persistent in notification shade for later reference
- Expandable to see full path

**🔄 Auto-Refresh**
- Pull down to refresh image list
- Auto-refreshes after each copy operation (300ms delay)
- Smooth updates without UI blinking

**🎨 Modern UI**
- Material 3 design language
- Custom app icon
- Smooth animations throughout
- Dark background for fullscreen viewing

**🔄 Auto-Update**
- Check for updates directly from GitHub
- One-tap update installation
- View release notes before updating
- MIT License - fully open source

---

## 🚀 Usage

### Method 1: Browse and Copy

1. Open Pic Path app
2. Choose category tab (Screenshots, Camera, etc.)
3. **Single tap** image to open fullscreen
4. **Tap copy button** or **long press** to copy path
5. See notification confirming copy
6. Paste path in Claude on Termux!

### Method 2: Edit, Annotate, and Copy (New! ⚡)

1. Open an image in fullscreen or share it to Pic Path
2. Tap the **Edit** button (pencil icon)
3. Use the tools:
   - **Crop**: Drag to select an area
   - **Arrow**: Drag to draw arrows pointing to specific details
   - **Text**: Tap and type to add labels or explanations
4. Tap **Done** (check icon)
5. The edited image is saved to `Pictures/PicPath/` and its path is **automatically copied**!
6. Paste the path directly to your AI model to give it perfect context.

### Method 3: Share from Any App (Recommended)

1. Open Gallery, Files, or any app with images
2. Select an image
3. Tap **Share** button
4. Choose **Pic Path** from share menu
5. Path is **automatically copied**!
6. Notification appears with the path
7. Switch to Termux and paste

### Method 4: Search and Copy

1. Open Pic Path
2. Use search bar at top
3. Type filename to filter
4. Tap copy button on result
5. Path is copied!

### Keeping Updated

1. Tap **Info** icon (ℹ️) in top-right corner
2. Tap **Check for Updates**
3. If update available, tap **Update Now**
4. APK downloads and installs automatically
5. View release notes to see what's new

---

## 💡 Perfect for Claude on Termux

### Why this works great with Claude:

When running Claude in Termux, you often need to analyze images. Instead of typing long paths:

```bash
# Old way (tedious):
# Navigate to file manager, find image, copy path manually...

# With Pic Path (instant):
# 1. Share image to Pic Path
# 2. Path auto-copied to clipboard
# 3. In Termux, type your command and paste:
claude analyze /sdcard/DCIM/Camera/IMG_20240226_123456.jpg
```

**Real workflow example:**
1. Take a screenshot of an error
2. Tap Share → Pic Path
3. Path copied automatically with notification
4. Open Termux
5. Type: `claude "explain this error" ` and paste path
6. Done! ⚡

---

## 📥 Installation

### Option 1: Download APK
1. Download the latest APK from [Releases](https://github.com/alexey-a-abramov/pic-path/releases)
2. Enable "Install from Unknown Sources" if prompted
3. Tap APK to install

### Option 2: Build from Source
```bash
git clone https://github.com/alexey-a-abramov/pic-path.git
cd pic-path
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🛠️ Technical Details

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room (for fast image indexing and search)
- **Image Loading**: Coil (efficient caching)
- **Networking**: OkHttp (for GitHub API update checks)
- **Serialization**: Kotlinx Serialization (JSON parsing)
- **Async**: Kotlin Coroutines + Flow
- **Min SDK**: Android 10 (API 29)
- **Target SDK**: Android 14 (API 34)

### Permissions
- `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 10-12)
- `INTERNET` (for checking updates from GitHub)
- `REQUEST_INSTALL_PACKAGES` (for installing APK updates)

### Project Structure
```
app/src/main/java/com/imageviewer/
├── MainActivity.kt                    # Entry point, permission handling
├── ui/
│   ├── ImageGridScreen.kt            # Main grid view with tabs
│   ├── FullscreenImageViewer.kt      # Fullscreen image viewer
│   ├── SharedImageViewer.kt          # Share target handler
│   └── components/
│       ├── ImageGridItem.kt          # Grid thumbnail component
│       └── SearchBar.kt              # Search functionality
├── viewmodel/
│   └── ImageViewModel.kt             # Business logic
├── data/
│   ├── model/ImageFile.kt            # Image data model
│   ├── repository/ImageRepository.kt # Data source coordination
│   └── database/
│       ├── ImageDatabase.kt          # Room database
│       └── ImageDao.kt               # Database queries
└── util/
    ├── MediaStoreScanner.kt          # MediaStore integration
    ├── ClipboardHelper.kt            # Clipboard operations
    └── UriHelper.kt                  # URI to path conversion
```

---

## 🚀 Why Pic Path?

Copying image paths on Android is notoriously difficult. Pic Path makes it a one-tap operation, especially useful for developers and AI power users who need to provide local file paths to tools like:
- **Gemini CLI**
- **Codex**
- **Claude on Termux**

Simply find your image, tap the copy button, and paste the absolute path directly into your terminal.

## 🎨 Features in Detail

### Category Detection
Images are automatically categorized based on their file path:
- **Screenshots**: `/Pictures/Screenshots/`, `/DCIM/Screenshots/`
- **Camera**: `/DCIM/Camera/`, `/DCIM/`
- **Downloads**: `/Download/`, `/Downloads/`
- **Other**: Everything else

### Copy Button Design
- Position: Bottom-right corner
- Transparency: 70% (alpha 0.3)
- Icon: Custom clipboard/copy icon
- Shape: Circular with white background
- Size: 36dp (grid), 48dp (fullscreen)

### Feedback
- When a path is copied, a "Path copied to clipboard" message is shown.

### Pull-to-Refresh Behavior
- Trigger: Pull down on image grid
- Action: Re-scans device storage for new images
- Updates: Database is updated with new/deleted images
- UI: Smooth refresh without clearing existing images

---

## 🔧 Configuration

### Changing Default Tab
Edit `ImageViewModel.kt`:
```kotlin
private val _selectedCategory = MutableStateFlow("Screenshots") // Change to "All", "Camera", etc.
```

### Adjusting Grid Columns
Edit `ImageGridScreen.kt`:
```kotlin
columns = GridCells.Fixed(3) // Change to 2, 4, or adaptive
```

### Customizing Copy Button Transparency
Edit `ImageGridItem.kt` and `FullscreenImageViewer.kt`:
```kotlin
.background(Color.White.copy(alpha = 0.3f)) // Change alpha value (0.0-1.0)
```

---

## 🐛 Troubleshooting

### Images Not Showing
- Grant storage permission when prompted
- Pull down to refresh the image list
- Check if images are in standard Android folders (DCIM, Pictures, Download)

### Share Target Not Appearing
- Reinstall the app
- Clear defaults for Gallery/Files app
- Check that you're sharing an image file (not video)

### Path Shows "null" or Content URI
- Some apps share images via content:// URIs
- App attempts to resolve to real path, but not always possible
- Works best with native Android Gallery and file managers

---

## 🤝 Contributing

Contributions are welcome! Here's how:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📝 Changelog

### v1.2.0 (2026-03-05)
- ✨ **New Image Editor**: Crop and annotate images before copying paths
- 🏹 **Arrow Tool**: Point AI models exactly where to look
- 📝 **Text Tool**: Add labels and context directly to your images
- ✂️ **Crop Tool**: Focus on the most important parts of a screenshot
- 💾 **Auto-Save & Copy**: Edited images are saved to `Pictures/PicPath` and their new paths are instantly copied

### v1.1.0 (2026-02-26)
- 🔄 Auto-update feature with GitHub integration
- ℹ️ About screen with app information
- 📋 View release notes before updating
- 🔒 MIT License confirmation in app
- 🎨 New top app bar with info button

### v1.0.0 (2024-02-26)
- ✨ Initial release
- 📂 Category tabs (All, Screenshots, Camera, Downloads, Other)
- 🖼️ Grid and fullscreen view modes
- 📋 Copy button on all images
- 🔔 System notifications
- 🔄 Pull-to-refresh and auto-refresh
- 📤 Share target from other apps
- 🎨 Custom Material 3 UI
- 🔍 Search functionality

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

---

## 🙏 Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Image loading by [Coil](https://coil-kt.github.io/coil/)
- Database by [Room](https://developer.android.com/training/data-storage/room)
- Icons from [Material Icons](https://fonts.google.com/icons)

---

## 📧 Contact

Questions or suggestions? Open an issue on GitHub!

---

<div align="center">

**Made with ❤️ for developers who need fast image paths**

*Especially those using Claude on Termux! 🚀*

</div>
