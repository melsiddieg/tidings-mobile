#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/// Installs iOS audio session configuration (background & lock-screen podcast
/// playback) and privacy shield observers (app switcher blur + screen capture
/// protection) before Tauri enters UIApplicationMain.
void tidings_ios_setup(void);

#ifdef __cplusplus
}
#endif
