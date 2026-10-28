package com.opentdi.wear.sensing

import android.content.Context
import android.os.Handler
import com.opentdi.wear.BuildConfig
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Where the Samsung Health Sensor SDK comes from (owner, 2026-10-28: "bring your own Samsung kit"). Public builds carry
 * no Samsung code: the user downloads the SDK from Samsung, the phone app converts it and sends it here
 * ([USER_DEX]). A developer's local build may still bundle the AAR. [SamsungSensorSource] uses the SDK's classes, so
 * with the user's kit it is loaded a second time by a child-first loader over this APK plus that dex: its SDK classes
 * come from the kit, everything else from the app. The rest of the app only sees it as [SamsungPath].
 */
object SamsungKit {
    const val USER_DEX = "samsung-sdk.dex"
    private const val SOURCE = "com.opentdi.wear.sensing.SamsungSensorSource" // also its inner classes and lambdas
    private const val SDK_PREFIX = "com.samsung."
    private var loader: ClassLoader? = null
    private var loaderStamp = 0L

    fun userDex(context: Context) = File(context.filesDir, USER_DEX)

    /** "built in", "your kit" or null when this watch has no Samsung SDK (basic Android sensors then). */
    fun source(context: Context): String? = when {
        BuildConfig.SAMSUNG_SDK_BUNDLED -> "built in"
        userDex(context).exists() -> "your kit"
        else -> null
    }

    /** The Samsung path, or null without an SDK or when the kit does not load (wrong file, another SDK version). */
    fun create(context: Context, handler: Handler, session: LoggingSession): SamsungPath? = try {
        val classes = loader(context) ?: return null
        classes.loadClass(SOURCE)
            .getConstructor(Context::class.java, Handler::class.java, LoggingSession::class.java)
            .newInstance(context, handler, session) as SamsungPath
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: LinkageError) { // the kit lacks a class or method this code uses
        null
    }

    @Synchronized
    private fun loader(context: Context): ClassLoader? {
        if (BuildConfig.SAMSUNG_SDK_BUNDLED) return SamsungKit::class.java.classLoader
        val dex = userDex(context).takeIf { it.exists() } ?: return null
        if (loader != null && loaderStamp == dex.lastModified()) return loader
        dex.setReadOnly() // Android 14+ refuses to load a code file the app could still write
        val path = context.applicationInfo.sourceDir + File.pathSeparator + dex.path
        return ChildFirstLoader(path, context.codeCacheDir.path, SamsungKit::class.java.classLoader!!).also {
            loader = it
            loaderStamp = dex.lastModified()
        }
    }

    /** Finds the SDK and [SOURCE] in its own dex files first; everything else in the app, so the types match. */
    private class ChildFirstLoader(path: String, codeCache: String, parent: ClassLoader) :
        DexClassLoader(path, codeCache, null, parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith(SDK_PREFIX) && !name.startsWith(SOURCE)) return super.loadClass(name, resolve)
            synchronized(this) {
                return findLoadedClass(name) ?: findClass(name)
            }
        }
    }
}
