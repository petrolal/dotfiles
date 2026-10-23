addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.4")

// Provides JUnit 5 test support: must be an actual sbt plugin (not just a
// library dependency) because it overrides sbt's own test-detection task
// with one that uses the JUnit Platform's own discovery mechanism -- sbt's
// native AnnotatedFingerprint-based discovery does not find @Test-annotated
// classes on its own.
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.14.0")
