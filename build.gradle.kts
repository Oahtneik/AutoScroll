// Root build file. Plugin versions are declared here with `apply false`,
// then applied in the :app module. This is the recommended pattern for
// multi-module projects (and keeps us flexible if we add :core/:data later).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
