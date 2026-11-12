// Tidings: a private, secure feed reader.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    // The SVG helper: the app starts itself a second time to draw SVG images, so
    // that an SVG which crashes the drawing code ends that process and not the app
    // (see images.rs). It must do nothing else: no window, no database, no network.
    if std::env::args_os().nth(1).is_some_and(|arg| arg == tidings_lib::WORKER_ARG) {
        return tidings_lib::worker_main();
    }

    tidings_lib::run();
}
