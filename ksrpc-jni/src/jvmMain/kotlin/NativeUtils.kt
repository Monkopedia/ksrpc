/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc.jni

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.ProviderNotFoundException
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/*
 * Class NativeUtils is published under the The MIT License:
 *
 * Copyright (c) 2012 Adam Heinrich <adam@adamh.cz>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * A simple library class which helps with loading dynamic libraries stored in the
 * JAR archive. These libraries usually contain implementation of some methods in
 * native code (using JNI - Java Native Interface).
 *
 * @see [http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar](http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar)
 *
 * @see [https://github.com/adamheinrich/native-utils](https://github.com/adamheinrich/native-utils)
 */
object NativeUtils {
    /**
     * The minimum length a prefix for a file has to have according to [File.createTempFile]}.
     */
    private const val MIN_PREFIX_LENGTH = 3
    private const val NATIVE_FOLDER_PATH_PREFIX = "nativeutils"

    /**
     * Temporary directory which will contain the DLLs.
     */
    private var temporaryDir: File? = null

    /**
     * Loads library from current JAR archive
     *
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after
     * exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     *
     * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters
     * (restriction of [File.createTempFile]).
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    @Suppress("UnsafeDynamicallyLoadedCode")
    @Throws(IOException::class)
    fun loadLibraryFromJar(path: String?) {
        require(path != null && path[0] == '/') { "The path has to be absolute (start with '/')." }

        // Obtain filename from path
        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val filename = if (parts.size > 1) parts[parts.size - 1] else null

        // Check if the filename is okay
        require(!(filename == null || filename.length < MIN_PREFIX_LENGTH)) {
            "The filename has to be at least 3 characters long."
        }

        // Prepare temporary file
        if (temporaryDir == null) {
            temporaryDir = createTempDirectory()
            temporaryDir!!.deleteOnExit()
        }
        val temp = File(temporaryDir, filename)
        try {
            NativeUtils::class.java.getResourceAsStream(path).use { inputStream ->
                Files.copy(inputStream, temp.toPath(), REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            temp.delete()
            throw e
        } catch (e: NullPointerException) {
            temp.delete()
            throw FileNotFoundException("File $path was not found inside JAR.")
        }
        try {
            System.load(temp.absolutePath)
        } finally {
            if (isPosixCompliant) {
                // Assume POSIX compliant file system, can be deleted after loading
                temp.delete()
            } else {
                // Assume non-POSIX, and don't delete until last file descriptor closed
                temp.deleteOnExit()
            }
        }
    }

    private val isPosixCompliant: Boolean
        get() = try {
            FileSystems.getDefault()
                .supportedFileAttributeViews()
                .contains("posix")
        } catch (e: FileSystemNotFoundException) {
            false
        } catch (e: ProviderNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private fun createTempDirectory(): File {
        val tempDir = System.getProperty("java.io.tmpdir")
        val generatedDir = File(tempDir, NATIVE_FOLDER_PATH_PREFIX + System.nanoTime())
        if (!generatedDir.mkdir()) {
            throw IOException(
                "Failed to create temp directory " + generatedDir.name
            )
        }
        return generatedDir
    }
}
