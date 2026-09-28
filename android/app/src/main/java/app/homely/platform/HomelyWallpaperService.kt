package app.homely.platform

import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

class HomelyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null
        private var ready = false
        private var showing = false

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayer(holder)
        }

        private fun startPlayer(holder: SurfaceHolder) {
            player?.release()
            ready = false
            val file = File(filesDir, "motion.mp4")
            if (!file.exists()) return
            player =
                MediaPlayer().apply {
                    setDataSource(file.path)
                    setSurface(holder.surface)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener {
                        ready = true
                        if (showing) it.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        ready = false
                        true
                    }
                    prepareAsync()
                }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            showing = visible
            if (ready) {
                if (visible) player?.start() else player?.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            player?.release()
            player = null
            ready = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            player?.release()
            player = null
            super.onDestroy()
        }
    }
}
