package org.onion.agro.io

import okio.FileSystem
import okio.SYSTEM

internal actual val systemFileSystem: FileSystem
    get() = FileSystem.SYSTEM
