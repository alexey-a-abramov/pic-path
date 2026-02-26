# Pic Selector - Android App

An Android image selector app built with Kotlin and Jetpack Compose that displays images from device storage with category tabs, fullscreen viewing, and easy path copying.

## Features

- **Category Tabs**: Filter images by All, Screenshots (default), Camera, Downloads, or Other
- **Fullscreen Viewer**: Single tap opens fullscreen view with swipe navigation
- **Easy Path Copying**: 70% transparent "Copy" button on every image (grid and fullscreen)
- **Navigation**: Left/right arrows and close button in fullscreen mode
- **Search**: Real-time search by filename with debouncing
- **Long Press**: Copy image path to clipboard in any mode
- **Refresh**: Manual refresh button to re-index images
- **Modern UI**: Material 3 with Jetpack Compose
- **Efficient Loading**: Coil for image loading and caching
- **Local Indexing**: Room database for fast category filtering and search

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Minimum SDK**: API 29 (Android 10+)
- **Target SDK**: API 34
- **Image Loading**: Coil
- **Database**: Room
- **Async**: Kotlin Coroutines + Flow

## Project Structure

```
app/
├── src/main/
│   ├── java/com/imageviewer/
│   │   ├── MainActivity.kt                 # Entry point with permission handling
│   │   ├── ui/
│   │   │   ├── ImageGridScreen.kt          # Main screen with grid
│   │   │   ├── ImageViewerApp.kt           # App root composable
│   │   │   └── components/
│   │   │       ├── ImageGridItem.kt        # Grid item with long press
│   │   │       └── SearchBar.kt            # Search input field
│   │   ├── viewmodel/
│   │   │   └── ImageViewModel.kt           # Business logic
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   └── ImageFile.kt            # Data model
│   │   │   ├── repository/
│   │   │   │   └── ImageRepository.kt      # Data source coordinator
│   │   │   └── database/
│   │   │       ├── ImageDatabase.kt        # Room database
│   │   │       └── ImageDao.kt             # Database queries
│   │   └── util/
│   │       ├── MediaStoreScanner.kt        # MediaStore integration
│   │       └── ClipboardHelper.kt          # Clipboard utility
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Building the Project

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34
- Gradle 8.2

### Build Instructions

1. Clone or download the project
2. Open in Android Studio
3. Sync Gradle files
4. Run on device or emulator (Android 10+)

Or use command line:

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build and install
./gradlew build installDebug
```

## Permissions

The app requires storage permissions to access images:
- `READ_MEDIA_IMAGES` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 10-12)

Permissions are requested at runtime on first launch.

## Usage

1. **Launch App**: Grant storage permission when prompted
2. **View Images**: Images load automatically in a 3-column grid
3. **Search**: Type in the search bar to filter by filename
4. **Copy Path**: Long press any image to copy its path to clipboard
5. **Refresh**: Pull down to re-index and refresh the image list

## Key Features Implementation

### Long Press to Copy Path
- Uses `Modifier.combinedClickable` for gesture handling
- Copies full file path using system clipboard
- Shows confirmation snackbar

### Search Functionality
- Real-time search with 300ms debounce
- Searches against indexed database
- Case-insensitive filename matching

### File Indexing
- Scans MediaStore on app launch
- Stores metadata in Room database
- Manual refresh via pull-to-refresh

### Image Display
- Lazy loading grid with 3 columns
- Coil for efficient image loading and caching
- Filename overlay on each thumbnail

## Libraries Used

- **Jetpack Compose**: Modern declarative UI
- **Material 3**: Material Design components
- **Room**: Local database for indexing
- **Coil**: Image loading and caching
- **Kotlin Coroutines**: Asynchronous programming
- **Lifecycle Components**: ViewModel and state management

## License

This project is provided as-is for educational and reference purposes.
