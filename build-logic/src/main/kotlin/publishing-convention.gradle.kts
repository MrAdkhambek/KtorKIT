import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish")
}

// POM coordinates and metadata come from GROUP / VERSION_NAME / POM_* in the root
// gradle.properties, plus POM_ARTIFACT_ID / POM_NAME / POM_DESCRIPTION per module.
// For the multiplatform runtime this publishes the shared metadata artifact plus one
// artifact per target, so consumers get the right variant automatically.
extensions.configure<MavenPublishBaseExtension> {
    publishToMavenCentral()
    signAllPublications()
}
