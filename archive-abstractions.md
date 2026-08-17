# App-layer abstractions that JBinding7zArchiveExplorer depends on

`JBinding7zArchiveExplorer.kt` depends on the following **simple interfaces / data classes** (from the publishing
app, not the port core). Declare equivalent types in your own project, or replace the browse/extract logic with
commons-compress / the official `SevenZFile`.

## 1. `ArchiveExplorer` interface (excerpt)

```kotlin
interface ArchiveExplorer : Closeable {
    val archiveFile: File
    val archiveType: ArchiveType

    data class ExtractProgress(
        val currentBytes: Long = 0,
        val totalBytes: Long = 0,
        val currentEntry: String = "",
    )

    // Compression (the core of this port)
    suspend fun compress(
        sourceFiles: List<File>,
        destinationFile: File,
        password: String?,
        splitSize: Long?,
        onProgress: ((ExtractProgress) -> Unit)? = null,
    )

    // The following browse/extract methods are NOT implemented by this port
    // (delegated to a browse engine such as commons-compress's SevenZFile)
    suspend fun listRootEntries(password: String?): List<ArchiveEntryFileInfo>
    suspend fun listChildEntries(directoryEntry: ArchiveEntryFileInfo, password: String?): List<ArchiveEntryFileInfo>
    suspend fun extractAll(destinationDir: File, password: String?,
                           conflictStrategy: FileHelper.ConflictStrategy,
                           onProgress: ((ExtractProgress) -> Unit)? = null)
    // ...
}
```

## 2. `ArchiveException`

```kotlin
class ArchiveException(
    message: String,
    val reason: Reason = Reason.UNKNOWN,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason { WRONG_PASSWORD, UNKNOWN, IO_ERROR, NOT_SUPPORTED }
}
```

## 3. `ArchiveType`, `ArchiveEntryFileInfo`

```kotlin
enum class ArchiveType { SEVEN_ZIP, ZIP, RAR, TAR, TAR_XZ, /* ... */ }

data class ArchiveEntryFileInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long = 0,
    // ...
)
```

## 4. `FileHelper.ConflictStrategy`

```kotlin
object FileHelper {
    enum class ConflictStrategy { OVERWRITE, SKIP, RENAME }
    // Extract-conflict helper (unrelated to compression; only used in delegated method signatures)
}
```

## 5. `SevenZipArchiveExplorer` (delegated browse/extract impl)

`delegate: SevenZipArchiveExplorer` wraps commons-compress's `SevenZFile` and handles browse/extract.
**Compression logic is fully independent from browse/extract** — you can take only
`JBinding7zArchiveExplorer.compress` + `SplitSevenZOutStream` and wire browse/extract back to your own solution.

> If you only need "7z creation", you can skip the `delegate` entirely and just delete the delegated methods
> like `listRootEntries`.
