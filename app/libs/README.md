# Vendored binaries

## recyclerview-flexibledivider-1.2.9.jar

`com.yqritc:recyclerview-flexibledivider:1.2.9`, taken from the AAR of the same
version. The library was published to jcenter, which shut down in 2021, and is
not on Maven Central (404). It is vendored instead of pointing the build at a
third-party mirror.

It is needed because `lib-n-ide-release-10/lib-n-ide-release-10.aar` is a
prebuilt binary that hard-references it: `FileListPagerFragment` creates a
`HorizontalDividerItemDecoration.Builder` while setting up the file explorer,
so without the class that screen throws `NoClassDefFoundError`. The AAR itself
does not bundle the library.

Only `classes.jar` is kept from the AAR: its `res/` directory is empty and its
classes reference no generated `R` class, so the AAR wrapper adds nothing.
