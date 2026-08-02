package org.onion.agro.io

import okio.FileSystem

/**
 * Provides the host file system without leaking Okio's platform-refined
 * `FileSystem.SYSTEM` declaration into common metadata compilation.
 */
internal expect val systemFileSystem: FileSystem
