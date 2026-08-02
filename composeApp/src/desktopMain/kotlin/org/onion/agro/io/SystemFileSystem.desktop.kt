package org.onion.agro.io

import okio.FileSystem

internal actual val systemFileSystem: FileSystem
    get() = FileSystem.SYSTEM
