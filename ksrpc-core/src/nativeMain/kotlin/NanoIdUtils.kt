/**
 * Copyright (C) 2022 Jason Monk <monkopedia@gmail.com>
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
package nanoid

import kotlin.math.E
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log
import kotlin.random.Random

/**
 * A class for generating unique String IDs.
 *
 * The implementations of the core logic in this class are based on NanoId, a JavaScript
 * library by Andrey Sitnik released under the MIT license. (https://github.com/ai/nanoid)
 *
 * @author David Klebanoff
 */
internal object NanoIdUtils {
    /**
     * The default random number generator used by this class.
     * Creates cryptographically strong NanoId Strings.
     */
    val DEFAULT_NUMBER_GENERATOR = Random.Default

    /**
     * The default alphabet used by this class.
     * Creates url-friendly NanoId Strings using 64 unique symbols.
     */
    val DEFAULT_ALPHABET =
        "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

    /**
     * The default size used by this class.
     * Creates NanoId Strings with slightly more unique values than UUID v4.
     */
    const val DEFAULT_SIZE = 21
    /**
     * Static factory to retrieve a NanoId String.
     *
     * The string is generated using the given random number generator.
     *
     * @param random   The random number generator.
     * @param alphabet The symbols used in the NanoId String.
     * @param size     The number of symbols in the NanoId String.
     * @return A randomly generated NanoId String.
     */
    /**
     * Static factory to retrieve a url-friendly, pseudo randomly generated, NanoId String.
     *
     * The generated NanoId String will have 21 symbols.
     *
     * The NanoId String is generated using a cryptographically strong pseudo random number
     * generator.
     *
     * @return A randomly generated NanoId String.
     */
    fun randomNanoId(
        random: Random? = DEFAULT_NUMBER_GENERATOR,
        alphabet: CharArray? = DEFAULT_ALPHABET,
        size: Int = DEFAULT_SIZE
    ): String {
        requireNotNull(random) { "random cannot be null." }
        requireNotNull(alphabet) { "alphabet cannot be null." }
        require(!(alphabet.size == 0 || alphabet.size >= 256)) {
            "alphabet must contain between 1 and 255 symbols."
        }
        require(size > 0) { "size must be greater than zero." }
        val mask = (2 shl floor(log((alphabet.size - 1).toDouble(), E) / log(2.0, E)).toInt()) - 1
        val step = ceil(1.6 * mask * size / alphabet.size).toInt()
        val idBuilder = StringBuilder()
        while (true) {
            val bytes = ByteArray(step)
            random.nextBytes(bytes)
            for (i in 0 until step) {
                val alphabetIndex: Int = bytes[i].toInt() and mask
                if (alphabetIndex < alphabet.size) {
                    idBuilder.append(alphabet[alphabetIndex])
                    if (idBuilder.length == size) {
                        return idBuilder.toString()
                    }
                }
            }
        }
    }
}
