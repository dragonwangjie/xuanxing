plugins {
    alias(libs.plugins.android.library)
    id("xuanxing.android.library")
}

android {
    namespace = "me.rerere.rikkahub.material3"
    sourceSets {
        getByName("main").java.srcDir("../material-color-utilities/java")
    }
}
